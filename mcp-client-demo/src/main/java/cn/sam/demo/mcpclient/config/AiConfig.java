package cn.sam.demo.mcpclient.config;


import cn.sam.demo.mcpclient.service.McpToolCallbackService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * AI配置类 - 使用 Spring AI 1.1.0 新特性
 *
 * @author Administrator
 */
@Configuration
public class AiConfig {

    /**
     * 配置基础 ChatClient（不带工具）
     * 用于普通对话
     */
    @Bean
    public ChatClient chatClient(DeepSeekChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个有用的AI助手，能够理解上下文并提供准确的回答。")
                .build();
    }

    /**
     * 配置带工具的 ChatClient
     * 支持调用 MCP 工具
     * 使用 @Lazy 延迟加载，避免启动时工具未就绪
     */
    @Bean
    @Lazy
    public ChatClient toolChatClient(DeepSeekChatModel chatModel, McpToolCallbackService mcpToolCallbackService) {
        // 获取所有启用的 MCP 工具
        List<FunctionToolCallback> tools = mcpToolCallbackService.getEnabledToolCallbacks();

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem("你是一个有用的AI助手，能够理解上下文并提供准确的回答。" +
                        "当用户的请求需要使用工具时，请调用相应的工具来完成任务。");

        // 如果有工具，则添加（Spring AI 1.1.0 使用 toolCallbacks 而非 tools）
        if (!tools.isEmpty()) {
            builder.defaultToolCallbacks(tools.toArray(new FunctionToolCallback[0]));
        }

        return builder.build();
    }
}

