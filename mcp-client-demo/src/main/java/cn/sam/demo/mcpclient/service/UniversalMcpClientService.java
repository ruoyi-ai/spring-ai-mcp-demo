package cn.sam.demo.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 MCP 客户端服务
 * 支持 SSE 和 Streamable HTTP 两种传输方式
 * 提供同步和异步（流式）调用接口
 *
 * @author Administrator
 */
@Slf4j
@Service
public class UniversalMcpClientService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储已创建的客户端实例
    private final Map<String, ClientWrapper> clientCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("UniversalMcpClientService 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        // 清理所有客户端连接
        clientCache.values().forEach(wrapper -> {
            try {
                if (wrapper.client != null && wrapper.client.isInitialized()) {
                    // MCP 客户端没有显式的关闭方法，这里只是清理缓存
                    log.debug("清理 MCP 客户端: {}", wrapper.url);
                }
            } catch (Exception e) {
                log.warn("清理客户端时出错: {}", wrapper.url, e);
            }
        });
        clientCache.clear();
        log.info("UniversalMcpClientService 已清理");
    }

    /**
     * 创建或获取 MCP 客户端
     *
     * @param url           服务器地址
     * @param transportType 传输类型：sse 或 streamable-http
     * @param headers       请求头
     * @return 客户端包装器
     */
    public ClientWrapper getOrCreateClient(String url, String transportType, Map<String, String> headers) {
        String cacheKey = buildCacheKey(url, transportType);

        return clientCache.computeIfAbsent(cacheKey, key -> {
            try {
                log.info("创建新的 MCP 客户端: {} (传输类型: {})", url, transportType);

                McpClientTransport transport = createTransport(url, transportType, headers);
                McpSyncClient client = McpClient.sync(transport)
                        .loggingConsumer(message -> log.debug("MCP Client Log: {}", message))
                        .build();

                // 初始化客户端
                client.initialize();

                log.info("MCP 客户端初始化成功: {}", url);

                return new ClientWrapper(url, transportType, client, transport);
            } catch (Exception e) {
                log.error("创建 MCP 客户端失败: {} ({})", url, transportType, e);
                throw new RuntimeException("创建 MCP 客户端失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 创建传输层
     */
    private McpClientTransport createTransport(String url, String transportType, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10));

        // 创建请求头自定义器（如果需要）
        McpSyncHttpClientRequestCustomizer headerCustomizer = null;
        if (headers != null && !headers.isEmpty()) {
            final Map<String, String> finalHeaders = headers;
            headerCustomizer = (builder, method, endpoint, body, context) -> finalHeaders.forEach(builder::header);
        }

        // 根据传输类型创建不同的传输层
        if ("sse".equalsIgnoreCase(transportType) || "server-sent-events".equalsIgnoreCase(transportType)) {
            // SSE 传输：明确指定 /sse 端点
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(url)
                    .sseEndpoint("/sse")  // 明确指定 SSE 端点路径
                    .clientBuilder(clientBuilder);

            // 添加自定义请求头
            if (headerCustomizer != null) {
                builder.httpRequestCustomizer(headerCustomizer);
            }

            return builder.build();
        } else {
            // Streamable HTTP 传输：默认使用 /mcp 端点
            HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport
                    .builder(url)
                    .clientBuilder(clientBuilder);

            // 添加自定义请求头
            if (headerCustomizer != null) {
                builder.httpRequestCustomizer(headerCustomizer);
            }

            return builder.build();
        }
    }

    /**
     * 同步调用工具
     *
     * @param url           服务器地址
     * @param transportType 传输类型
     * @param toolName      工具名称
     * @param arguments     工具参数
     * @param headers       请求头
     * @return 调用结果
     */
    public Object invokeToolSync(String url, String transportType, String toolName,
                                 Map<String, Object> arguments, Map<String, String> headers) {
        try {
            ClientWrapper wrapper = getOrCreateClient(url, transportType, headers);
            McpSyncClient client = wrapper.client;

            if (!client.isInitialized()) {
                client.initialize();
            }

            log.debug("同步调用工具: {} -> {}", url, toolName);

            // 构建 CallToolRequest
            McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                    .name(toolName)
                    .arguments(arguments != null ? arguments : Map.of())
                    .build();

            // 调用工具
            McpSchema.CallToolResult result = client.callTool(request);

            // 提取结果内容
            return extractToolResult(result);

        } catch (Exception e) {
            log.error("同步调用工具失败: {} -> {}", url, toolName, e);
            throw new RuntimeException("调用工具失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步调用工具（流式）
     *
     * @param url           服务器地址
     * @param transportType 传输类型
     * @param toolName      工具名称
     * @param arguments     工具参数
     * @param headers       请求头
     * @return 流式响应
     */
    public Flux<Object> invokeToolStream(String url, String transportType, String toolName,
                                         Map<String, Object> arguments, Map<String, String> headers) {
        return Flux.create(sink -> {
            try {
                ClientWrapper wrapper = getOrCreateClient(url, transportType, headers);
                McpSyncClient client = wrapper.client;

                if (!client.isInitialized()) {
                    client.initialize();
                }

                log.debug("流式调用工具: {} -> {}", url, toolName);

                // 注意：McpSyncClient 是同步客户端，不支持真正的流式调用
                // 如果需要真正的流式调用，需要使用异步客户端
                // 这里先使用同步调用，然后包装成 Flux
                CompletableFuture.supplyAsync(() -> {
                    try {
                        // 构建 CallToolRequest
                        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                                .name(toolName)
                                .arguments(arguments != null ? arguments : Map.of())
                                .build();

                        McpSchema.CallToolResult result = client.callTool(request);
                        return extractToolResult(result);
                    } catch (Exception e) {
                        sink.error(e);
                        return null;
                    }
                }).thenAccept(result -> {
                    if (result != null) {
                        sink.next(result);
                        sink.complete();
                    }
                });

            } catch (Exception e) {
                log.error("流式调用工具失败: {} -> {}", url, toolName, e);
                sink.error(e);
            }
        });
    }

    /**
     * 获取工具列表
     *
     * @param url           服务器地址
     * @param transportType 传输类型
     * @param headers       请求头
     * @return 工具列表
     */
    public List<McpSchema.Tool> listTools(String url, String transportType, Map<String, String> headers) {
        try {
            ClientWrapper wrapper = getOrCreateClient(url, transportType, headers);
            McpSyncClient client = wrapper.client;

            if (!client.isInitialized()) {
                client.initialize();
            }

            McpSchema.ListToolsResult result = client.listTools();
            return result.tools();

        } catch (Exception e) {
            log.error("获取工具列表失败: {}", url, e);
            throw new RuntimeException("获取工具列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查连接是否健康
     *
     * @param url           服务器地址
     * @param transportType 传输类型
     * @param headers       请求头
     * @return 是否健康
     */
    public boolean ping(String url, String transportType, Map<String, String> headers) {
        try {
            ClientWrapper wrapper = getOrCreateClient(url, transportType, headers);
            McpSyncClient client = wrapper.client;

            if (!client.isInitialized()) {
                client.initialize();
            }

            client.ping();
            return true;

        } catch (Exception e) {
            log.debug("Ping 失败: {} ({})", url, transportType, e);
            return false;
        }
    }

    /**
     * 移除客户端缓存
     *
     * @param url           服务器地址
     * @param transportType 传输类型
     */
    public void removeClient(String url, String transportType) {
        String cacheKey = buildCacheKey(url, transportType);
        clientCache.remove(cacheKey);
        log.info("已移除客户端缓存: {}", cacheKey);
    }

    /**
     * 提取工具调用结果
     */
    @SuppressWarnings("unchecked")
    private Object extractToolResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return null;
        }

        List<McpSchema.Content> content = result.content();
        if (content == null || content.isEmpty()) {
            return Map.of("success", true, "result", "");
        }
        List<McpSchema.TextContent> contents = content.stream()
                .filter(c -> "text".equals(c.type()))
                .map(c -> (McpSchema.TextContent) c)
                .toList();

        // 如果只有一个结果，直接返回文本
        if (contents.size() == 1) {
            return contents.get(0).text();
        }
        // 多个结果，返回列表
        return contents.stream()
                .map(McpSchema.TextContent::text)
                .toList();
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String url, String transportType) {
        return url + "|" + (transportType != null ? transportType.toLowerCase() : "streamable-http");
    }

    /**
     * 客户端包装器
     */
    public static class ClientWrapper {
        public final String url;
        public final String transportType;
        public final McpSyncClient client;
        public final McpClientTransport transport;
        public final long createdAt;

        public ClientWrapper(String url, String transportType, McpSyncClient client, McpClientTransport transport) {
            this.url = url;
            this.transportType = transportType;
            this.client = client;
            this.transport = transport;
            this.createdAt = System.currentTimeMillis();
        }
    }
}

