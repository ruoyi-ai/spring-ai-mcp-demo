package cn.sam.demo.mcpclient.service;

import cn.sam.demo.mcpclient.entity.McpToolData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP 工具回调服务
 * 将数据库中的 MCP 工具转换为 Spring AI 的 FunctionCallback
 * 供 ChatClient 使用，实现 AI 对话中的工具调用
 *
 * @author Administrator
 */
@Slf4j
@Service
public class McpToolCallbackService {

    @Resource
    private McpToolService mcpToolService;

    @Resource
    private UniversalMcpClientService universalMcpClientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取所有启用的 MCP 工具作为 FunctionCallback
     * 用于在 ChatClient 中注册工具
     *
     * @return FunctionCallback 列表
     */
    public List<FunctionToolCallback> getEnabledToolCallbacks() {
        List<FunctionToolCallback> callbacks = new ArrayList<>();

        // 获取所有启用的工具
        List<McpToolData> tools = mcpToolService.listByStatus(McpToolData.Status.ENABLED);

        for (McpToolData tool : tools) {
            try {
                FunctionToolCallback callback = createToolCallback(tool);
                if (callback != null) {
                    callbacks.add(callback);
                    log.debug("已创建工具回调: {}", tool.getName());
                }
            } catch (Exception e) {
                log.error("创建工具回调失败: {}", tool.getName(), e);
            }
        }

        log.info("共创建 {} 个工具回调", callbacks.size());
        return callbacks;
    }

    /**
     * 根据工具名称获取单个 FunctionCallback
     *
     * @param toolName 工具名称
     * @return FunctionCallback
     */
    public FunctionToolCallback getToolCallback(String toolName) {
        List<McpToolData> tools = mcpToolService.searchByName(toolName);
        for (McpToolData tool : tools) {
            if (toolName.equals(tool.getName()) && McpToolData.Status.ENABLED.equals(tool.getStatus())) {
                return createToolCallback(tool);
            }
        }
        return null;
    }

    /**
     * 创建单个工具的 FunctionCallback
     *
     * @param tool 工具数据
     * @return FunctionCallback
     */
    private FunctionToolCallback createToolCallback(McpToolData tool) {
        try {
            // 解析配置获取传输信息
            Map<String, Object> config = parseConfig(tool.getConfigJson());
            @SuppressWarnings("unchecked")
            Map<String, Object> transport = (Map<String, Object>) config.get("transport");

            if (transport == null) {
                log.warn("工具配置缺少 transport 信息: {}", tool.getName());
                return null;
            }

            String url = (String) transport.get("url");
            String transportType = (String) transport.getOrDefault("type", "streamable-http");

            if (url == null || url.isEmpty()) {
                log.warn("工具配置缺少 URL: {}", tool.getName());
                return null;
            }

            // 创建工具调用函数（输入类型为 Map，Spring AI 会自动将 JSON 反序列化为 Map）
            @SuppressWarnings("unchecked")
            BiFunction<Map<String, Object>, ToolContext, String> toolFunction = (arguments, context) -> {
                try {
                    log.info("调用 MCP 工具: {} 参数: {}", tool.getName(), arguments);

                    // 调用 MCP 工具
                    Object result = universalMcpClientService.invokeToolSync(
                            url, transportType, tool.getName(), arguments, null);

                    // 将结果转换为字符串
                    String resultStr;
                    if (result instanceof String) {
                        resultStr = (String) result;
                    } else {
                        resultStr = objectMapper.writeValueAsString(result);
                    }

                    log.info("MCP 工具 {} 调用结果: {}", tool.getName(), resultStr);
                    return resultStr;

                } catch (Exception e) {
                    log.error("调用 MCP 工具失败: {}", tool.getName(), e);
                    return "工具调用失败: " + e.getMessage();
                }
            };

            // 构建 FunctionToolCallback（使用 Map 作为输入类型）
            return FunctionToolCallback.builder(tool.getName(), toolFunction)
                    .description(tool.getDescription() != null ? tool.getDescription() : tool.getName())
                    .inputType(Map.class)
                    .inputSchema(tool.getParamSchema() != null ? tool.getParamSchema() : "{}")
                    .build();

        } catch (Exception e) {
            log.error("创建工具回调失败: {}", tool.getName(), e);
            return null;
        }
    }

    /**
     * 解析配置 JSON
     */
    private Map<String, Object> parseConfig(String configJson) {
        try {
            if (configJson == null || configJson.isEmpty()) {
                return Map.of();
            }
            return objectMapper.readValue(configJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析配置失败: {}", e.getMessage());
            return Map.of();
        }
    }
}

