package cn.sam.demo.mcpservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态工具管理控制器
 * 
 * <p>用于演示 MCP 变更通知功能</p>
 * 
 * <h3>功能：</h3>
 * <ul>
 *   <li>动态注册新工具（触发 tool-change-notification）</li>
 *   <li>动态注销工具（触发 tool-change-notification）</li>
 *   <li>查看当前工具列表</li>
 * </ul>
 * 
 * <h3>使用场景：</h3>
 * <ol>
 *   <li>POST /admin/tools/register - 注册新工具</li>
 *   <li>服务端自动发送 tools/list_changed 通知</li>
 *   <li>客户端接收通知并更新本地工具列表</li>
 *   <li>无需重启客户端 ✅</li>
 * </ol>
 * 
 * @author Administrator
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/tools")
public class DynamicToolController {

    /**
     * 动态工具存储（用于演示，生产环境应使用数据库）
     */
    private static final Map<String, DynamicToolInfo> DYNAMIC_TOOLS = new ConcurrentHashMap<>();

    /**
     * 模拟注册动态工具
     * 
     * <p>注意：实际的工具注册需要通过 MCP Server 的 API，这里仅做演示。
     * 真实场景下，需要使用 Spring AI MCP 的工具注册机制。</p>
     * 
     * @param toolName 工具名称
     * @param description 工具描述
     * @return 注册结果
     */
    @PostMapping("/register")
    public Map<String, Object> registerTool(
            @RequestParam String toolName,
            @RequestParam String description) {
        
        log.info("========================================");
        log.info("注册动态工具: {}", toolName);
        log.info("描述: {}", description);
        log.info("========================================");

        DynamicToolInfo toolInfo = new DynamicToolInfo();
        toolInfo.setName(toolName);
        toolInfo.setDescription(description);
        toolInfo.setRegisteredAt(LocalDateTime.now());

        DYNAMIC_TOOLS.put(toolName, toolInfo);

        // 注意：这里只是模拟，真实的工具注册应该：
        // 1. 使用 @McpTool 注解的类
        // 2. 或者通过 MCP Server 的 API 动态注册
        // 3. 注册成功后，服务端会自动发送 tools/list_changed 通知

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "工具注册成功（演示）");
        result.put("toolName", toolName);
        result.put("totalTools", DYNAMIC_TOOLS.size());
        result.put("note", "实际场景中，服务端会自动向所有连接的客户端发送 tools/list_changed 通知");

        return result;
    }

    /**
     * 模拟注销动态工具
     * 
     * @param toolName 工具名称
     * @return 注销结果
     */
    @DeleteMapping("/unregister")
    public Map<String, Object> unregisterTool(@RequestParam String toolName) {
        
        log.info("========================================");
        log.info("注销动态工具: {}", toolName);
        log.info("========================================");

        DynamicToolInfo removed = DYNAMIC_TOOLS.remove(toolName);

        Map<String, Object> result = new HashMap<>();
        if (removed != null) {
            result.put("success", true);
            result.put("message", "工具注销成功（演示）");
            result.put("toolName", toolName);
            result.put("totalTools", DYNAMIC_TOOLS.size());
            result.put("note", "实际场景中，服务端会自动向所有连接的客户端发送 tools/list_changed 通知");
        } else {
            result.put("success", false);
            result.put("message", "工具不存在");
            result.put("toolName", toolName);
        }

        return result;
    }

    /**
     * 查看当前动态工具列表
     * 
     * @return 工具列表
     */
    @GetMapping("/list")
    public Map<String, Object> listDynamicTools() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalTools", DYNAMIC_TOOLS.size());
        result.put("tools", new ArrayList<>(DYNAMIC_TOOLS.values()));
        return result;
    }

    /**
     * 触发通知测试
     * 
     * <p>模拟工具列表变更，测试客户端是否能收到通知</p>
     * 
     * @return 测试结果
     */
    @PostMapping("/test-notification")
    public Map<String, Object> testNotification() {
        log.info("========================================");
        log.info("触发工具列表变更通知测试");
        log.info("========================================");

        // 注意：实际的通知发送由 MCP Server 自动完成
        // 这里只是演示API

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "通知测试完成");
        result.put("info", Map.of(
                "notificationType", "tools/list_changed",
                "protocol", "SSE",
                "endpoint", "/sse",
                "description", "客户端应该收到工具列表变更通知"
        ));

        return result;
    }

    /**
     * 动态工具信息
     */
    public static class DynamicToolInfo {
        private String name;
        private String description;
        private LocalDateTime registeredAt;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDateTime getRegisteredAt() {
            return registeredAt;
        }

        public void setRegisteredAt(LocalDateTime registeredAt) {
            this.registeredAt = registeredAt;
        }

        public String getRegisteredAtFormatted() {
            return registeredAt != null 
                    ? registeredAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : null;
        }
    }
}

