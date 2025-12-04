package cn.sam.demo.mcpclient;

import cn.sam.demo.mcpclient.service.UniversalMcpClientService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.annotation.Resource;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 客户端测试
 * 测试连接到 mcp-service-demo
 * 
 * 问题分析：
 * - HttpClientSseClientTransport 使用 SSE 传输，需要访问 /sse 端点
 * - 但服务端配置的是 streamable-http 协议，端点路径是 /mcp
 * - 应该使用 HttpClientStreamableHttpTransport 而不是 HttpClientSseClientTransport
 */
@SpringBootTest
class McpClientTest {

    @Resource
    private UniversalMcpClientService universalMcpClientService;

    @Test
    void testConnectToMcpServerStreamable() {
        // 使用 HttpClientStreamableHttpTransport（Streamable HTTP 传输）
        // 而不是 HttpClientSseClientTransport（SSE 传输）
        // 因为服务端配置的是 streamable-http 协议，端点路径是 /mcp
        String mcpServerUrl = "http://localhost:9899";

        System.out.println("============================================");
        System.out.println("MCP 客户端连接测试");
        System.out.println("============================================");
        System.out.println("服务器地址: " + mcpServerUrl);
        System.out.println("实际访问路径: " + mcpServerUrl + "/mcp");
        System.out.println();

        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL);

            // 使用 HttpClientStreamableHttpTransport，它会自动追加 /mcp 到 base URL
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                    .builder(mcpServerUrl)
                    .clientBuilder(clientBuilder)
                    .build();

            McpSyncClient client = McpClient.sync(transport).build();

            System.out.println("正在初始化 MCP 客户端...");
            client.initialize();
            System.out.println("初始化成功！");
            System.out.println("是否已经初始化: " + client.isInitialized());
            System.out.println();

            System.out.println("正在获取工具列表...");
            McpSchema.ListToolsResult listToolsResult = client.listTools();
            List<McpSchema.Tool> tools = listToolsResult.tools();

            if (tools.isEmpty()) {
                System.out.println("警告：未找到任何工具");
                return;
            }

            System.out.println("获取到的工具数量: " + tools.size());
            System.out.println("工具列表: " + tools.stream().map(McpSchema.Tool::name)
                    .collect(Collectors.joining(", ")));
            System.out.println();

            for (McpSchema.Tool tool : tools) {
                System.out.println("工具名称: " + tool.name());
                System.out.println("工具描述: " + tool.description());
                System.out.println("工具参数 Schema: " + tool.inputSchema());
                System.out.println();
            }

            System.out.println("============================================");
            System.out.println("测试完成！");
            System.out.println("============================================");

        } catch (io.modelcontextprotocol.spec.McpTransportException e) {
            System.err.println("============================================");
            System.err.println("MCP 传输错误！");
            System.err.println("============================================");
            System.err.println("错误信息: " + e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("404")) {
                System.err.println();
                System.err.println("这是 404 错误，说明 MCP 服务器端点不存在！");
                System.err.println();
                System.err.println("请确保：");
                System.err.println("1. mcp-service-demo 应用已启动（运行在 9899 端口）");
                System.err.println("2. MCP 服务器端点路径正确（/mcp）");
                System.err.println("3. 服务端配置了 streamable-http 协议");
            }
            System.err.println("============================================");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("============================================");
            System.err.println("发生错误！");
            System.err.println("============================================");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("============================================");
            e.printStackTrace();
        }
    }


    @Test
    void testConnectToMcpServerSse() {
        // SSE 传输：明确指定 /sse 端点
        McpClientTransport transport = HttpClientSseClientTransport.builder("http://localhost:9899")
                .sseEndpoint("/sse")  // 明确指定 SSE 端点路径
                .build();
        var client = McpClient.sync(transport)
                .loggingConsumer(message -> System.out.println(">> Client Logging: " + message))
                .build();

        System.out.println("============================================");
        System.out.println("MCP 客户端 SSE 连接测试");
        System.out.println("============================================");
        System.out.println("服务器地址: http://localhost:9899");
        System.out.println("SSE 端点: /sse");
        System.out.println();

        try {
            System.out.println("正在初始化 MCP 客户端...");
            client.initialize();
            System.out.println("初始化成功！");
            System.out.println();

            System.out.println("正在执行 Ping...");
            client.ping();
            System.out.println("Ping 成功！");
            System.out.println();

            // List and demonstrate tools
            System.out.println("正在获取工具列表...");
            McpSchema.ListToolsResult toolsList = client.listTools();

            System.out.println("可用工具数量: " + toolsList.tools().size());
            toolsList.tools().forEach(tool -> System.out.printf("Tool: %s, description: %s, schema: %s%n",
                    tool.name(), tool.description(), tool.inputSchema()));

            System.out.println("============================================");
            System.out.println("测试完成！");
            System.out.println("============================================");

        } catch (Exception e) {
            System.err.println("============================================");
            System.err.println("测试失败！");
            System.err.println("============================================");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("============================================");
            e.printStackTrace();
        }
    }

    /**
     * 测试使用通用 MCP 客户端服务（Streamable HTTP）
     */
    @Test
    void testUniversalMcpClientServiceStreamable() {
        String mcpServerUrl = "http://localhost:9899";
        String transportType = "streamable-http";

        System.out.println("============================================");
        System.out.println("通用 MCP 客户端服务测试 - Streamable HTTP");
        System.out.println("============================================");
        System.out.println("服务器地址: " + mcpServerUrl);
        System.out.println("传输类型: " + transportType);
        System.out.println();

        try {
            // 1. 获取工具列表
            System.out.println("正在获取工具列表...");
            List<McpSchema.Tool> tools = universalMcpClientService.listTools(mcpServerUrl, transportType, null);
            
            if (tools.isEmpty()) {
                System.out.println("警告：未找到任何工具");
                return;
            }

            System.out.println("获取到的工具数量: " + tools.size());
            System.out.println("工具列表: " + tools.stream().map(McpSchema.Tool::name)
                    .collect(Collectors.joining(", ")));
            System.out.println();

            // 2. 调用工具
            if (tools.size() > 0) {
                McpSchema.Tool firstTool = tools.get(0);
                String toolName = firstTool.name();
                
                System.out.println("正在调用工具: " + toolName);
                Object result = universalMcpClientService.invokeToolSync(
                        mcpServerUrl,
                        transportType,
                        toolName,
                        Map.of(), // 空参数
                        null
                );
                
                System.out.println("工具调用结果: " + result);
                System.out.println();
            }

            // 3. 健康检查
            System.out.println("正在执行健康检查...");
            boolean isHealthy = universalMcpClientService.ping(mcpServerUrl, transportType, null);
            System.out.println("服务健康状态: " + (isHealthy ? "健康" : "不健康"));

            System.out.println("============================================");
            System.out.println("测试完成！");
            System.out.println("============================================");

        } catch (Exception e) {
            System.err.println("============================================");
            System.err.println("测试失败！");
            System.err.println("============================================");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("============================================");
            e.printStackTrace();
        }
    }

    /**
     * 测试使用通用 MCP 客户端服务（SSE）
     * 注意：此测试需要服务端启用 SSE 端点
     */
    @Test
    void testUniversalMcpClientServiceSse() {
        String mcpServerUrl = "http://localhost:9899";
        String transportType = "sse";

        System.out.println("============================================");
        System.out.println("通用 MCP 客户端服务测试 - SSE");
        System.out.println("============================================");
        System.out.println("服务器地址: " + mcpServerUrl);
        System.out.println("传输类型: " + transportType);
        System.out.println("注意：此测试需要服务端启用 SSE 端点");
        System.out.println();

        try {
            // 1. 获取工具列表
            System.out.println("正在获取工具列表...");
            List<McpSchema.Tool> tools = universalMcpClientService.listTools(mcpServerUrl, transportType, null);
            
            if (tools.isEmpty()) {
                System.out.println("警告：未找到任何工具");
                return;
            }

            System.out.println("获取到的工具数量: " + tools.size());
            System.out.println("工具列表: " + tools.stream().map(McpSchema.Tool::name)
                    .collect(Collectors.joining(", ")));
            System.out.println();

            // 2. 调用工具
            if (tools.size() > 0) {
                McpSchema.Tool firstTool = tools.get(0);
                String toolName = firstTool.name();
                
                System.out.println("正在调用工具: " + toolName);
                Object result = universalMcpClientService.invokeToolSync(
                        mcpServerUrl,
                        transportType,
                        toolName,
                        Map.of("a",1,"b",2), // 空参数
                        null
                );
                
                System.out.println("工具调用结果: " + result);
                System.out.println();
            }

            System.out.println("============================================");
            System.out.println("测试完成！");
            System.out.println("============================================");

        } catch (Exception e) {
            System.err.println("============================================");
            System.err.println("测试失败！");
            System.err.println("============================================");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println();
            System.err.println("提示：如果出现 404 错误，说明服务端未启用 SSE 端点");
            System.err.println("请在服务端配置中启用：spring.ai.mcp.server.sse-endpoint: /sse");
            System.err.println("============================================");
            e.printStackTrace();
        }
    }
}

