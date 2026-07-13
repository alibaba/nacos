/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotModelProviderTest {
    
    @Test
    void testRegistryCreatesModelsForEachProviderProtocol() {
        CopilotModelProviderRegistry registry = new CopilotModelProviderRegistry(
            List.of(new DashScopeModelProvider(), new MiniMaxModelProvider()));
        CopilotProperties config = new CopilotProperties();
        assertInstanceOf(DashScopeChatModel.class, registry.createModel(config, "test-key"));
        
        config.setProvider(MiniMaxModelProvider.NAME);
        config.setModel(MiniMaxModelProvider.MODEL_M3);
        config.setProtocol(MiniMaxModelProvider.PROTOCOL_OPENAI);
        config.setRegion(MiniMaxModelProvider.REGION_GLOBAL);
        assertInstanceOf(OpenAIChatModel.class, registry.createModel(config, "test-key"));
        
        config.setProtocol(MiniMaxModelProvider.PROTOCOL_ANTHROPIC);
        assertInstanceOf(AnthropicChatModel.class, registry.createModel(config, "test-key"));
    }
    
    @Test
    void testMiniMaxMetadataCoversModelsProtocolsAndRegions() {
        CopilotProviderMetadata metadata = new MiniMaxModelProvider().getMetadata();
        assertEquals(List.of(MiniMaxModelProvider.MODEL_M3, MiniMaxModelProvider.MODEL_M27),
            metadata.getModels().stream().map(
                CopilotProviderMetadata.ModelMetadata::getModelId).toList());
        assertEquals(List.of(MiniMaxModelProvider.PROTOCOL_OPENAI,
            MiniMaxModelProvider.PROTOCOL_ANTHROPIC),
            metadata.getProtocols().stream().map(
                CopilotProviderMetadata.ProtocolMetadata::getName).toList());
        for (CopilotProviderMetadata.ProtocolMetadata protocol : metadata.getProtocols()) {
            assertEquals(List.of(MiniMaxModelProvider.REGION_GLOBAL,
                MiniMaxModelProvider.REGION_CHINA),
                protocol.getEndpoints().stream().map(
                    CopilotProviderMetadata.EndpointMetadata::getRegion).toList());
        }
        assertEquals(1_000_000, metadata.getModels().get(0).getContextWindow());
        assertEquals(List.of("text", "image", "video"),
            metadata.getModels().get(0).getInputModalities());
        assertEquals(List.of("adaptive", "disabled"),
            metadata.getModels().get(0).getThinking());
        assertEquals(204_800, metadata.getModels().get(1).getContextWindow());
        assertEquals(List.of("always_on"), metadata.getModels().get(1).getThinking());
    }
    
    @Test
    void testMiniMaxProviderRejectsProtocolPathMismatch() {
        CopilotProperties config = miniMaxConfig(MiniMaxModelProvider.PROTOCOL_ANTHROPIC);
        config.setBaseUrl("https://api.minimax.io/v1");
        assertThrows(IllegalArgumentException.class,
            () -> new MiniMaxModelProvider().validate(config));
    }
    
    @Test
    void testOpenAiAdapterAppendsChatCompletionsPath() throws Exception {
        assertEquals("/v1/chat/completions", capturePath(
            MiniMaxModelProvider.PROTOCOL_OPENAI, "/v1"));
    }
    
    @Test
    void testAnthropicAdapterAppendsMessagesPath() throws Exception {
        assertEquals("/anthropic/v1/messages", capturePath(
            MiniMaxModelProvider.PROTOCOL_ANTHROPIC, "/anthropic"));
    }
    
    private String capturePath(String protocol, String basePath) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            writeResponse(exchange, protocol);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + basePath;
            Model model = MiniMaxModelProvider.PROTOCOL_ANTHROPIC.equals(protocol)
                ? AnthropicChatModel.builder().apiKey("test-key").baseUrl(baseUrl)
                    .modelName(MiniMaxModelProvider.MODEL_M3).stream(false).build()
                : OpenAIChatModel.builder().apiKey("test-key").baseUrl(baseUrl)
                    .modelName(MiniMaxModelProvider.MODEL_M3).stream(false).build();
            model.stream(List.of(Msg.builder().textContent("Hello").build()), List.of(), null)
                .blockLast();
            return requestPath.get();
        } finally {
            server.stop(0);
        }
    }
    
    private void writeResponse(HttpExchange exchange, String protocol) throws IOException {
        String response = MiniMaxModelProvider.PROTOCOL_ANTHROPIC.equals(protocol)
            ? "{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"MiniMax-M3\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"ok\"}],\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,"
                + "\"output_tokens\":1}}"
            : "{\"id\":\"chatcmpl_test\",\"object\":\"chat.completion\","
                + "\"created\":1,\"model\":\"MiniMax-M3\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    private CopilotProperties miniMaxConfig(String protocol) {
        CopilotProperties config = new CopilotProperties();
        config.setProvider(MiniMaxModelProvider.NAME);
        config.setModel(MiniMaxModelProvider.MODEL_M3);
        config.setProtocol(protocol);
        config.setRegion(MiniMaxModelProvider.REGION_GLOBAL);
        return config;
    }
}
