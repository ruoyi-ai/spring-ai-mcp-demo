package cn.sam.demo.mcpclient.controller;

import cn.sam.demo.mcpclient.service.UniversalMcpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 连接测试控制器
 * 提供直接测试 MCP 服务器连接的功能（类似单测）
 *
 * @author Administrator
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp/test")
public class McpConnectionTestController {

    @Resource
    private UniversalMcpClientService universalMcpClientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试 MCP 服务器连接
     *
     * @param request 测试请求
     * @return 测试结果
     */
    @PostMapping("/connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = (String) request.get("url");
            String transportType = (String) request.getOrDefault("transportType", "streamable-http");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) request.get("headers");

            if (url == null || url.isEmpty()) {
                result.put("success", false);
                result.put("error", "服务器地址不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            // 执行 Ping 测试
            boolean pingResult = universalMcpClientService.ping(url, transportType, headers);
            
            result.put("success", pingResult);
            result.put("url", url);
            result.put("transportType", transportType);
            result.put("message", pingResult ? "连接成功" : "连接失败");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("测试连接失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 获取 MCP 服务器工具列表
     *
     * @param request 请求参数
     * @return 工具列表
     */
    @PostMapping("/tools/list")
    public ResponseEntity<Map<String, Object>> listTools(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = (String) request.get("url");
            String transportType = (String) request.getOrDefault("transportType", "streamable-http");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) request.get("headers");

            if (url == null || url.isEmpty()) {
                result.put("success", false);
                result.put("error", "服务器地址不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            List<McpSchema.Tool> tools = universalMcpClientService.listTools(url, transportType, headers);
            
            result.put("success", true);
            result.put("url", url);
            result.put("transportType", transportType);
            result.put("tools", tools.stream().map(tool -> {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.name());
                toolMap.put("description", tool.description());
                toolMap.put("inputSchema", tool.inputSchema());
                return toolMap;
            }).toList());
            result.put("count", tools.size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("获取工具列表失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * 测试调用 MCP 工具
     *
     * @param request 测试请求
     * @return 调用结果
     */
    @PostMapping("/tools/invoke")
    public ResponseEntity<Map<String, Object>> invokeTool(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String url = (String) request.get("url");
            String transportType = (String) request.getOrDefault("transportType", "streamable-http");
            String toolName = (String) request.get("toolName");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) request.get("headers");

            if (url == null || url.isEmpty()) {
                result.put("success", false);
                result.put("error", "服务器地址不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (toolName == null || toolName.isEmpty()) {
                result.put("success", false);
                result.put("error", "工具名称不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            long startTime = System.currentTimeMillis();
            Object toolResult = universalMcpClientService.invokeToolSync(
                    url, 
                    transportType, 
                    toolName, 
                    arguments != null ? arguments : Map.of(), 
                    headers
            );
            long duration = System.currentTimeMillis() - startTime;

            // 格式化返回结果
            String resultJson;
            if (toolResult instanceof String) {
                resultJson = (String) toolResult;
            } else {
                resultJson = objectMapper.writeValueAsString(toolResult);
            }

            result.put("success", true);
            result.put("url", url);
            result.put("transportType", transportType);
            result.put("toolName", toolName);
            result.put("request", arguments != null ? arguments : Map.of());
            result.put("response", toolResult);
            result.put("responseJson", resultJson);
            result.put("duration", duration + "ms");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("调用工具失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            result.put("url", request.get("url"));
            result.put("transportType", request.getOrDefault("transportType", "streamable-http"));
            result.put("toolName", request.get("toolName"));
            return ResponseEntity.ok(result);
        }
    }
}

