package cn.sam.demo.mcpclient.listener;

import cn.sam.demo.mcpclient.entity.McpToolData;
import cn.sam.demo.mcpclient.service.McpToolRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpPromptListChanged;
import org.springaicommunity.mcp.annotation.McpResourceListChanged;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 服务端变更通知处理器
 * 
 * <p>功能：监听 MCP 服务端发送的变更通知，实时更新数据库中的工具列表</p>
 * 
 * <h3>支持的通知类型：</h3>
 * <ul>
 *   <li>工具列表变更（Tool List Changed）- 自动同步到数据库</li>
 *   <li>资源列表变更（Resource List Changed）</li>
 *   <li>提示词列表变更（Prompt List Changed）</li>
 * </ul>
 * 
 * <h3>工作原理：</h3>
 * <ol>
 *   <li>客户端通过 SSE 与服务端保持长连接</li>
 *   <li>服务端工具列表变更时主动推送通知</li>
 *   <li>客户端接收通知并触发对应的处理方法</li>
 *   <li>自动同步更新到数据库（增量更新）</li>
 *   <li>无需重启客户端，实时获取最新配置</li>
 * </ol>
 * 
 * <h3>优势：</h3>
 * <ul>
 *   <li>✅ 实时性：变更通知 < 1 秒到达</li>
 *   <li>✅ 低延迟：基于 SSE 长连接</li>
 *   <li>✅ 无需重启：动态更新本地配置</li>
 *   <li>✅ 低负载：仅在变更时推送，不轮询</li>
 *   <li>✅ 自动同步：增量更新数据库</li>
 * </ul>
 * 
 * @author Administrator
 * @since 1.0.0
 */
@Slf4j
@Component
public class McpChangeNotificationHandler {

    @Resource
    private McpToolRegistryService mcpToolRegistryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理工具列表变更通知
     * 
     * <p>当 MCP 服务端的工具列表发生变化时（新增、删除、修改），
     * 服务端会主动推送通知到客户端，此方法会被自动调用。</p>
     * 
     * <h4>触发场景：</h4>
     * <ul>
     *   <li>服务端新增工具（@McpTool 类被加载）</li>
     *   <li>服务端删除工具（@McpTool 类被卸载）</li>
     *   <li>服务端修改工具定义（参数、描述等）</li>
     *   <li>服务端重启后工具列表变化</li>
     * </ul>
     * 
     * <h4>自动同步：</h4>
     * <ul>
     *   <li>增量更新数据库中的远程工具列表</li>
     *   <li>新增：自动添加新工具到数据库</li>
     *   <li>删除：自动标记已删除的工具为 DISABLED</li>
     *   <li>修改：自动更新工具的描述和参数</li>
     * </ul>
     * 
     * @param updatedTools 更新后的完整工具列表
     */
    @McpToolListChanged(clients = "*")  // clients = "*" 表示监听所有 MCP 服务器
    public void handleToolListChanged(List<McpSchema.Tool> updatedTools) {
        log.info("========================================");
        log.info("收到工具列表变更通知！");
        log.info("========================================");
        log.info("更新后的工具数量: {}", updatedTools.size());
        
        try {
            // 打印工具详情
            if (log.isInfoEnabled()) {
                for (McpSchema.Tool tool : updatedTools) {
                    log.info("  工具: {} - {}", tool.name(), 
                            tool.description() != null ? tool.description() : "无描述");
                }
            }
            
            // 增量更新数据库中的工具列表
            log.info("开始同步工具列表到数据库...");
            syncToolsToDatabase(updatedTools);
            log.info("工具列表同步完成！");
            
            // TODO: 可选的后续操作
            // 1. 清除工具调用缓存
            // toolInvocationCache.clear();
            
            // 2. 通知前端刷新工具列表（通过 WebSocket）
            // webSocketService.notifyToolListChanged(updatedTools);
            
            log.info("工具列表已更新！无需重启客户端。");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("处理工具列表变更通知失败", e);
            log.error("========================================");
        }
    }

    /**
     * 处理资源列表变更通知
     * 
     * <p>当 MCP 服务端的资源列表发生变化时，服务端会主动推送通知。</p>
     * 
     * <h4>资源示例：</h4>
     * <ul>
     *   <li>文件资源：file:///path/to/document.txt</li>
     *   <li>数据库资源：db://users/table</li>
     *   <li>API 资源：api://weather/current</li>
     * </ul>
     * 
     * @param updatedResources 更新后的完整资源列表
     */
    @McpResourceListChanged(clients = "*")
    public void handleResourceListChanged(List<McpSchema.Resource> updatedResources) {
        log.info("========================================");
        log.info("收到资源列表变更通知！");
        log.info("========================================");
        log.info("更新后的资源数量: {}", updatedResources.size());
        
        // 打印资源详情
        if (log.isInfoEnabled()) {
            for (McpSchema.Resource resource : updatedResources) {
                log.info("  资源: {} - {} ({})", 
                        resource.name(), 
                        resource.description() != null ? resource.description() : "无描述",
                        resource.uri());
            }
        }
        
        // TODO: 实现资源缓存更新逻辑
        // resourceCache.update(updatedResources);
        
        log.info("资源列表已更新！");
        log.info("========================================");
    }

    /**
     * 处理提示词列表变更通知
     * 
     * <p>当 MCP 服务端的提示词模板发生变化时，服务端会主动推送通知。</p>
     * 
     * <h4>提示词用途：</h4>
     * <ul>
     *   <li>AI 对话的预设提示词</li>
     *   <li>任务执行的指令模板</li>
     *   <li>多轮对话的上下文</li>
     * </ul>
     * 
     * @param updatedPrompts 更新后的完整提示词列表
     */
    @McpPromptListChanged(clients = "*")
    public void handlePromptListChanged(List<McpSchema.Prompt> updatedPrompts) {
        log.info("========================================");
        log.info("收到提示词列表变更通知！");
        log.info("========================================");
        log.info("更新后的提示词数量: {}", updatedPrompts.size());
        
        // 打印提示词详情
        if (log.isInfoEnabled()) {
            for (McpSchema.Prompt prompt : updatedPrompts) {
                log.info("  提示词: {} - {}", 
                        prompt.name(), 
                        prompt.description() != null ? prompt.description() : "无描述");
            }
        }
        
        // TODO: 实现提示词缓存更新逻辑
        // promptCache.update(updatedPrompts);
        
        log.info("提示词列表已更新！");
        log.info("========================================");
    }
    
    // ========== 核心同步方法 ==========
    
    /**
     * 同步工具列表到数据库（增量更新）
     * 
     * <p>策略：</p>
     * <ol>
     *   <li>获取数据库中所有远程工具</li>
     *   <li>对比服务端推送的工具列表</li>
     *   <li>新增：添加新工具到数据库</li>
     *   <li>删除：标记已删除的工具为 DISABLED</li>
     *   <li>修改：更新工具的描述和参数</li>
     * </ol>
     * 
     * @param serverTools 服务端推送的工具列表
     */
    private void syncToolsToDatabase(List<McpSchema.Tool> serverTools) {
        try {
            // 1. 获取数据库中所有远程工具
            List<McpToolData> dbTools = mcpToolRegistryService.getAllRemoteTools();
            
            // 构建服务端工具的名称集合
            Set<String> serverToolNames = serverTools.stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toSet());
            
            // 构建数据库工具的名称映射
            Map<String, McpToolData> dbToolMap = dbTools.stream()
                    .collect(Collectors.toMap(McpToolData::getName, tool -> tool));
            
            int addedCount = 0;
            int updatedCount = 0;
            int disabledCount = 0;
            
            // 2. 处理服务端的工具（新增或更新）
            for (McpSchema.Tool serverTool : serverTools) {
                String toolName = serverTool.name();
                McpToolData dbTool = dbToolMap.get(toolName);
                
                if (dbTool == null) {
                    // 新增工具
                    log.info("  [新增] 工具: {}", toolName);
                    addToolToDatabase(serverTool);
                    addedCount++;
                } else {
                    // 更新工具（如果描述或参数有变化）
                    if (shouldUpdateTool(dbTool, serverTool)) {
                        log.info("  [更新] 工具: {}", toolName);
                        updateToolInDatabase(dbTool, serverTool);
                        updatedCount++;
                    } else {
                        log.debug("  [跳过] 工具无变化: {}", toolName);
                    }
                }
            }
            
            // 3. 处理已删除的工具（在数据库中存在但服务端没有）
            for (McpToolData dbTool : dbTools) {
                if (!serverToolNames.contains(dbTool.getName())) {
                    log.info("  [禁用] 工具已从服务端移除: {}", dbTool.getName());
                    disableToolInDatabase(dbTool);
                    disabledCount++;
                }
            }
            
            // 4. 输出同步结果
            log.info("同步统计: 新增 {} 个, 更新 {} 个, 禁用 {} 个, 总计 {} 个工具",
                    addedCount, updatedCount, disabledCount, serverTools.size());
            
        } catch (Exception e) {
            log.error("同步工具列表到数据库失败", e);
            throw new RuntimeException("工具列表同步失败", e);
        }
    }
    
    /**
     * 添加新工具到数据库
     * 
     * @param serverTool 服务端工具
     */
    private void addToolToDatabase(McpSchema.Tool serverTool) {
        try {
            McpToolData newTool = new McpToolData();
            newTool.setName(serverTool.name());
            newTool.setDisplayName(serverTool.name());
            newTool.setDescription(serverTool.description());
            newTool.setType(McpToolData.Type.REMOTE);
            newTool.setStatus(McpToolData.Status.ENABLED);
            
            // 将工具的 inputSchema 保存为 paramSchema
            if (serverTool.inputSchema() != null) {
                String paramSchemaJson = objectMapper.writeValueAsString(serverTool.inputSchema());
                newTool.setParamSchema(paramSchemaJson);
            }
            
            // 设置配置信息（暂时留空，后续可以从上下文获取）
            newTool.setConfigJson("{}");
            
            newTool.setCreateTime(LocalDateTime.now());
            newTool.setUpdateTime(LocalDateTime.now());
            
            // 保存到数据库
            mcpToolRegistryService.saveTool(newTool);
            
        } catch (Exception e) {
            log.error("添加工具到数据库失败: {}", serverTool.name(), e);
        }
    }
    
    /**
     * 更新数据库中的工具
     * 
     * @param dbTool 数据库中的工具
     * @param serverTool 服务端工具
     */
    private void updateToolInDatabase(McpToolData dbTool, McpSchema.Tool serverTool) {
        try {
            boolean updated = false;
            
            // 更新描述
            if (!Objects.equals(dbTool.getDescription(), serverTool.description())) {
                dbTool.setDescription(serverTool.description());
                updated = true;
            }
            
            // 更新参数 schema
            if (serverTool.inputSchema() != null) {
                String newParamSchema = objectMapper.writeValueAsString(serverTool.inputSchema());
                if (!Objects.equals(dbTool.getParamSchema(), newParamSchema)) {
                    dbTool.setParamSchema(newParamSchema);
                    updated = true;
                }
            }
            
            // 如果工具之前被禁用，重新启用
            if (dbTool.getStatus() == McpToolData.Status.DISABLED) {
                dbTool.setStatus(McpToolData.Status.ENABLED);
                updated = true;
                log.info("  工具 {} 已重新启用", dbTool.getName());
            }
            
            if (updated) {
                dbTool.setUpdateTime(LocalDateTime.now());
                mcpToolRegistryService.updateTool(dbTool);
            }
            
        } catch (Exception e) {
            log.error("更新工具失败: {}", dbTool.getName(), e);
        }
    }
    
    /**
     * 禁用数据库中的工具（不删除，只标记为 DISABLED）
     * 
     * @param dbTool 数据库中的工具
     */
    private void disableToolInDatabase(McpToolData dbTool) {
        try {
            if (dbTool.getStatus() != McpToolData.Status.DISABLED) {
                dbTool.setStatus(McpToolData.Status.DISABLED);
                dbTool.setUpdateTime(LocalDateTime.now());
                mcpToolRegistryService.updateTool(dbTool);
            }
        } catch (Exception e) {
            log.error("禁用工具失败: {}", dbTool.getName(), e);
        }
    }
    
    /**
     * 判断工具是否需要更新
     * 
     * @param dbTool 数据库中的工具
     * @param serverTool 服务端工具
     * @return 是否需要更新
     */
    private boolean shouldUpdateTool(McpToolData dbTool, McpSchema.Tool serverTool) {
        try {
            // 检查描述是否变化
            if (!Objects.equals(dbTool.getDescription(), serverTool.description())) {
                return true;
            }
            
            // 检查参数 schema 是否变化
            if (serverTool.inputSchema() != null) {
                String newParamSchema = objectMapper.writeValueAsString(serverTool.inputSchema());
                if (!Objects.equals(dbTool.getParamSchema(), newParamSchema)) {
                    return true;
                }
            }
            
            // 检查状态（如果被禁用，需要重新启用）
            if (dbTool.getStatus() == McpToolData.Status.DISABLED) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("判断工具是否需要更新失败: {}", dbTool.getName(), e);
            return false;
        }
    }
    
    // ========== 可选的辅助方法 ==========
    
    /**
     * 通知前端刷新（通过 WebSocket）
     * 
     * @param updatedTools 更新后的工具列表
     */
    private void notifyFrontendToRefresh(List<McpSchema.Tool> updatedTools) {
        // TODO: 实现 WebSocket 推送
        // 1. 构建 WebSocket 消息
        // 2. 推送到所有在线客户端
        // 3. 前端收到消息后自动刷新工具列表 UI
        
        log.debug("通知前端刷新工具列表...");
    }
}

