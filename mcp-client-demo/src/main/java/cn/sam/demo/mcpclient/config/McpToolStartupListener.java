package cn.sam.demo.mcpclient.config;

import cn.sam.demo.mcpclient.entity.McpToolData;
import cn.sam.demo.mcpclient.service.McpToolService;
import cn.sam.demo.mcpclient.service.UniversalMcpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具启动监听器
 * 在应用启动时自动从远程 MCP 服务发现并注册工具到数据库
 * 
 * 核心改进：工具列表从远端 MCP 服务自动获取，而非手动硬编码
 *
 * @author Administrator
 */
@Slf4j
@Component
@Order(1) // 确保在 McpConfigMigration 之前执行
public class McpToolStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private McpToolService mcpToolService;

    @Resource
    private UniversalMcpClientService universalMcpClientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 是否启用自动发现
     */
    @Value("${mcp.client.auto-discovery.enabled:true}")
    private boolean autoDiscoveryEnabled;

    /**
     * 远程 MCP 服务地址
     */
    @Value("${mcp.client.remote.url:http://127.0.0.1:9899}")
    private String remoteUrl;

    /**
     * 传输类型：sse 或 streamable-http
     */
    @Value("${mcp.client.remote.transport-type:sse}")
    private String transportType;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!autoDiscoveryEnabled) {
            log.info("MCP 工具自动发现已禁用，跳过");
            return;
        }

        log.info("========================================");
        log.info("开始从远程 MCP 服务发现工具...");
        log.info("远程服务地址: {}", remoteUrl);
        log.info("传输类型: {}", transportType);
        log.info("========================================");

        try {
            // 从远端获取工具列表
            List<McpSchema.Tool> remoteTools = discoverRemoteTools();

            if (remoteTools == null || remoteTools.isEmpty()) {
                log.warn("未从远程服务发现任何工具，请检查远程服务是否启动");
                return;
            }

            log.info("从远程服务发现 {} 个工具", remoteTools.size());

            int successCount = 0;
            int updateCount = 0;
            int failCount = 0;

            // 注册每个工具
            for (McpSchema.Tool tool : remoteTools) {
                try {
                    boolean isNew = registerOrUpdateTool(tool);
                    if (isNew) {
                        successCount++;
                    } else {
                        updateCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("注册工具失败: {}", tool.name(), e);
                }
            }

            log.info("========================================");
            log.info("远程 MCP 工具同步完成！");
            log.info("新增: {}, 更新: {}, 失败: {}", successCount, updateCount, failCount);
            log.info("========================================");

        } catch (Exception e) {
            log.error("从远程 MCP 服务发现工具失败", e);
            log.warn("提示: 请确保远程 MCP 服务 ({}) 已启动，传输类型 ({}) 配置正确", remoteUrl, transportType);
        }
    }

    /**
     * 从远端发现工具列表
     */
    private List<McpSchema.Tool> discoverRemoteTools() {
        try {
            log.info("正在连接远程 MCP 服务: {} ({})", remoteUrl, transportType);
            
            // 先检查连接
            boolean connected = universalMcpClientService.ping(remoteUrl, transportType, null);
            if (!connected) {
                log.warn("无法连接到远程 MCP 服务: {}", remoteUrl);
                return null;
            }

            // 获取工具列表
            List<McpSchema.Tool> tools = universalMcpClientService.listTools(remoteUrl, transportType, null);
            
            if (tools != null) {
                log.info("成功获取远程工具列表，共 {} 个工具", tools.size());
                tools.forEach(tool -> log.debug("  - {} ({})", tool.name(), tool.description()));
            }
            
            return tools;

        } catch (Exception e) {
            log.error("获取远程工具列表失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 注册或更新工具
     * 
     * @param remoteTool 远程工具信息
     * @return true 表示新增，false 表示更新
     */
    private boolean registerOrUpdateTool(McpSchema.Tool remoteTool) {
        String toolName = remoteTool.name();
        String description = remoteTool.description();

        // 根据名称查找是否已存在
        List<McpToolData> existingTools = mcpToolService.searchByName(toolName);
        McpToolData tool = null;

        // 查找完全匹配的工具
        for (McpToolData t : existingTools) {
            if (toolName.equals(t.getName())) {
                tool = t;
                break;
            }
        }

        boolean isNew = (tool == null);
        
        // 构建配置 JSON
        String configJson = buildToolConfig(remoteTool);

        if (isNew) {
            // 创建新工具
            tool = McpToolData.builder()
                    .name(toolName)
                    .displayName(toolName) // 可以后续通过配置自定义显示名称
                    .description(description)
                    .type(McpToolData.Type.REMOTE)
                    .status(McpToolData.Status.ENABLED)
                    .configJson(configJson)
                    .paramSchema(extractParamSchema(remoteTool))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            log.info("发现新工具: {} - {}", toolName, description);
        } else {
            // 更新现有工具
            tool.setDescription(description);
            tool.setConfigJson(configJson);
            tool.setParamSchema(extractParamSchema(remoteTool));
            tool.setUpdateTime(LocalDateTime.now());
            // 确保类型为 REMOTE
            if (!McpToolData.Type.REMOTE.equals(tool.getType())) {
                tool.setType(McpToolData.Type.REMOTE);
            }
            log.debug("更新工具: {} - {}", toolName, description);
        }

        mcpToolService.saveOrUpdateInfo(tool);
        return isNew;
    }

    /**
     * 构建工具配置 JSON
     * 包含 transport 和 function 定义
     */
    private String buildToolConfig(McpSchema.Tool tool) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("tool_name", tool.name());
            config.put("tool_type", "REMOTE");
            
            // 传输配置 - 使用配置的传输类型和地址
            config.put("transport", Map.of(
                    "type", transportType,
                    "url", remoteUrl
            ));

            // 函数定义 - 从远端工具信息获取
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            
            // 参数 schema - 从远端获取
            if (tool.inputSchema() != null) {
                function.put("parameters", tool.inputSchema());
            }
            
            config.put("function", function);

            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("构建工具配置失败: {}", tool.name(), e);
            return "{}";
        }
    }

    /**
     * 提取参数 Schema
     */
    private String extractParamSchema(McpSchema.Tool tool) {
        try {
            if (tool.inputSchema() != null) {
                return objectMapper.writeValueAsString(tool.inputSchema());
            }
            return null;
        } catch (Exception e) {
            log.warn("提取参数 Schema 失败: {}", tool.name());
            return null;
        }
    }
}
