package cn.sam.demo.mcpclient.service;

import cn.sam.demo.mcpclient.entity.McpToolData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 MCP 工具调用服务
 * 负责调用已注册的远程 MCP 工具
 * 现在支持 SSE 和 Streamable HTTP 两种传输方式
 *
 * @author Administrator
 */
@Slf4j
@Service
public class RemoteMcpToolInvokeService {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private McpToolRegistryService mcpToolRegistryService;

    @Resource
    private UniversalMcpClientService universalMcpClientService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用远程 MCP 工具（使用通用 MCP 客户端服务）
     *
     * @param tool     工具实体
     * @param toolName 工具名称（MCP 协议中的工具名称）
     * @param params   工具参数
     * @return 调用结果
     */
    public Object invokeRemoteTool(McpToolData tool, String toolName, Map<String, Object> params) {
        try {
            // 检查工具是否已注册
            if (!mcpToolRegistryService.isRegistered(tool.getId())) {
                throw new IllegalStateException("工具未注册: " + tool.getName());
            }

            // 获取远程客户端包装器
            String beanName = "mcpClient_" + tool.getId();
            McpToolRegistryService.RemoteMcpClientWrapper clientWrapper = applicationContext.getBean(beanName, McpToolRegistryService.RemoteMcpClientWrapper.class);

            // 获取传输配置
            String transportType = clientWrapper.transportType();
            String url = clientWrapper.url();
            Map<String, String> headers = clientWrapper.headers();

            // 标准化传输类型
            String normalizedTransportType = normalizeTransportType(transportType);

            // 使用通用 MCP 客户端服务调用
            log.debug("使用 {} 传输方式调用工具: {} -> {}", normalizedTransportType, url, toolName);
            return universalMcpClientService.invokeToolSync(url, normalizedTransportType, toolName, params, headers);
            
        } catch (Exception e) {
            log.error("调用远程工具失败: {} -> {}", tool.getName(), toolName, e);
            throw new RuntimeException("调用远程工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用远程 MCP 工具（使用通用 MCP 客户端服务）
     *
     * @param tool     工具实体
     * @param toolName 工具名称（MCP 协议中的工具名称）
     * @param params   工具参数
     * @return 流式响应
     */
    public Flux<Object> invokeRemoteToolStream(McpToolData tool, String toolName, Map<String, Object> params) {
        try {
            // 检查工具是否已注册
            if (!mcpToolRegistryService.isRegistered(tool.getId())) {
                return Flux.error(new IllegalStateException("工具未注册: " + tool.getName()));
            }

            // 获取远程客户端包装器
            String beanName = "mcpClient_" + tool.getId();
            McpToolRegistryService.RemoteMcpClientWrapper clientWrapper = applicationContext.getBean(beanName, McpToolRegistryService.RemoteMcpClientWrapper.class);

            // 获取传输配置
            String transportType = clientWrapper.transportType();
            String url = clientWrapper.url();
            Map<String, String> headers = clientWrapper.headers();

            // 标准化传输类型
            String normalizedTransportType = normalizeTransportType(transportType);

            // 使用通用 MCP 客户端服务流式调用
            log.debug("使用 {} 传输方式流式调用工具: {} -> {}", normalizedTransportType, url, toolName);
            return universalMcpClientService.invokeToolStream(url, normalizedTransportType, toolName, params, headers);
            
        } catch (Exception e) {
            log.error("流式调用远程工具失败: {} -> {}", tool.getName(), toolName, e);
            return Flux.error(new RuntimeException("流式调用远程工具失败: " + e.getMessage(), e));
        }
    }

    /**
     * 标准化传输类型
     */
    private String normalizeTransportType(String transportType) {
        if (transportType == null || transportType.isEmpty()) {
            return "streamable-http"; // 默认使用 streamable-http
        }
        
        String lower = transportType.toLowerCase();
        if ("sse".equals(lower) || "server-sent-events".equals(lower)) {
            return "sse";
        } else if ("http".equals(lower) || "streamable-http".equals(lower) || "streamable".equals(lower)) {
            return "streamable-http";
        } else {
            // 默认使用 streamable-http
            log.warn("未知的传输类型: {}，使用默认值 streamable-http", transportType);
            return "streamable-http";
        }
    }

    /**
     * 通过 HTTP/SSE 调用工具（使用 MCP 协议）
     * 保留此方法作为备用实现（向后兼容）
     * 推荐使用 UniversalMcpClientService
     */
    @Deprecated
    private Object invokeHttpTool(String baseUrl, String toolName, Map<String, Object> params, Map<String, String> headers) {
        try {
            // 构建请求 URL（MCP 协议使用 /mcp 端点）
            String requestUrl = baseUrl;
            if (!requestUrl.endsWith("/")) {
                requestUrl += "/";
            }
            // Spring AI MCP Server 的端点路径是 /mcp
            requestUrl += "mcp";

            // 构建请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            if (headers != null) {
                headers.forEach(httpHeaders::set);
            }

            // 构建请求体（MCP JSON-RPC 2.0 协议格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", System.currentTimeMillis()); // 使用时间戳作为 ID
            requestBody.put("method", "tools/call");
            
            Map<String, Object> methodParams = new HashMap<>();
            methodParams.put("name", toolName);
            methodParams.put("arguments", params != null ? params : Map.of());
            requestBody.put("params", methodParams);

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestBodyJson, httpHeaders);

            log.debug("调用 MCP 工具: {} -> {}, 请求: {}", requestUrl, toolName, requestBodyJson);

            // 发送 POST 请求
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("远程工具调用失败，状态码: " + response.getStatusCode());
            }

            // 解析响应（MCP JSON-RPC 2.0 格式）
            String responseBody = response.getBody();
            if (responseBody.isEmpty()) {
                return Map.of("success", true, "result", "");
            }

            // 解析 JSON-RPC 响应
            try {
                Map jsonRpcResponse = objectMapper.readValue(responseBody, Map.class);
                
                // 检查是否有错误
                if (jsonRpcResponse.containsKey("error")) {
                    Map<String, Object> error = (Map<String, Object>) jsonRpcResponse.get("error");
                    throw new RuntimeException("MCP 工具调用错误: " + error.get("message"));
                }
                
                // 提取结果
                Map<String, Object> result = (Map<String, Object>) jsonRpcResponse.get("result");
                if (result != null && result.containsKey("content")) {
                    // MCP 协议返回的内容在 content 字段中
                    Object content = result.get("content");
                    if (content instanceof List<?> contentList) {
                        if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                            Map<String, Object> firstContent = (Map<String, Object>) contentList.get(0);
                            return firstContent.get("text"); // 返回文本内容
                        }
                    }
                    return content;
                }
                
                return result;
            } catch (Exception e) {
                // 如果解析失败，尝试直接返回字符串
                log.warn("解析 MCP 响应失败，返回原始字符串: {}", e.getMessage());
                return responseBody;
            }
        } catch (Exception e) {
            log.error("HTTP 工具调用失败: {} -> {}", baseUrl, toolName, e);
            throw new RuntimeException("HTTP 工具调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查远程工具是否可用
     * 使用 UniversalMcpClientService 进行健康检查，支持 SSE 和 Streamable HTTP
     *
     * @param tool 工具实体
     * @return 是否可用
     */
    public boolean isToolAvailable(McpToolData tool) {
        try {
            if (!mcpToolRegistryService.isRegistered(tool.getId())) {
                log.debug("工具未注册: {}", tool.getName());
                return false;
            }

            String beanName = "mcpClient_" + tool.getId();
            McpToolRegistryService.RemoteMcpClientWrapper clientWrapper = applicationContext.getBean(beanName, McpToolRegistryService.RemoteMcpClientWrapper.class);
            
            String url = clientWrapper.url();
            String transportType = clientWrapper.transportType();
            Map<String, String> headers = clientWrapper.headers();

            // 标准化传输类型
            String normalizedTransportType = normalizeTransportType(transportType);

            // 使用 UniversalMcpClientService 进行 ping 测试
            // 它会根据传输类型自动选择正确的端点（SSE: /sse, Streamable HTTP: /mcp）
            log.debug("检查工具可用性: {} (传输类型: {}, URL: {})", tool.getName(), normalizedTransportType, url);
            boolean available = universalMcpClientService.ping(url, normalizedTransportType, headers);
            
            if (!available) {
                log.debug("工具健康检查失败: {} - Ping 返回 false", tool.getName());
            }
            
            return available;
            
        } catch (Exception e) {
            log.debug("工具健康检查失败: {} - 异常: {}", tool.getName(), e.getMessage());
            return false;
        }
    }
}
