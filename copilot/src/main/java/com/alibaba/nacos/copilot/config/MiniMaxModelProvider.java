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

import com.alibaba.nacos.common.utils.StringUtils;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * MiniMax Copilot model provider.
 *
 * @author nacos
 */
@Order(1)
@Component
public class MiniMaxModelProvider implements CopilotModelProvider {
    
    static final String NAME = "MiniMax";
    
    static final String MODEL_M3 = "MiniMax-M3";
    
    static final String MODEL_M27 = "MiniMax-M2.7";
    
    static final String PROTOCOL_OPENAI = "openai";
    
    static final String PROTOCOL_ANTHROPIC = "anthropic";
    
    static final String REGION_GLOBAL = "global_en";
    
    static final String REGION_CHINA = "cn_zh";
    
    private static final CopilotProviderMetadata METADATA = new CopilotProviderMetadata(NAME,
        MODEL_M3, PROTOCOL_OPENAI, REGION_GLOBAL, true,
        List.of(new CopilotProviderMetadata.ModelMetadata(MODEL_M3, 1_000_000,
            List.of("text", "image", "video"), List.of("adaptive", "disabled")),
            new CopilotProviderMetadata.ModelMetadata(MODEL_M27, 204_800, List.of("text"),
                List.of("always_on"))),
        List.of(protocol(PROTOCOL_OPENAI, "https://api.minimax.io/v1",
            "https://api.minimaxi.com/v1"),
            protocol(PROTOCOL_ANTHROPIC, "https://api.minimax.io/anthropic",
                "https://api.minimaxi.com/anthropic")));
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public CopilotProviderMetadata getMetadata() {
        return METADATA;
    }
    
    @Override
    public void validate(CopilotProperties config) {
        String model = effectiveModel(config);
        if (!MODEL_M3.equals(model) && !MODEL_M27.equals(model)) {
            throw new IllegalArgumentException("Unsupported MiniMax model: " + model);
        }
        String protocol = effectiveProtocol(config);
        if (!PROTOCOL_OPENAI.equals(protocol) && !PROTOCOL_ANTHROPIC.equals(protocol)) {
            throw new IllegalArgumentException("Unsupported MiniMax protocol: " + protocol);
        }
        String region = effectiveRegion(config);
        if (!REGION_GLOBAL.equals(region) && !REGION_CHINA.equals(region)) {
            throw new IllegalArgumentException("Unsupported MiniMax region: " + region);
        }
        validateBaseUrl(effectiveBaseUrl(config, protocol, region), protocol);
    }
    
    @Override
    public Model createModel(CopilotProperties config, String apiKey) {
        String protocol = effectiveProtocol(config);
        String baseUrl = effectiveBaseUrl(config, protocol, effectiveRegion(config));
        if (PROTOCOL_ANTHROPIC.equals(protocol)) {
            return AnthropicChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                .modelName(effectiveModel(config)).stream(true).build();
        }
        return OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl)
            .modelName(effectiveModel(config)).stream(true).build();
    }
    
    private static String effectiveModel(CopilotProperties config) {
        return StringUtils.isBlank(config.getModel()) ? MODEL_M3 : config.getModel();
    }
    
    private static String effectiveProtocol(CopilotProperties config) {
        return StringUtils.isBlank(config.getProtocol()) ? PROTOCOL_OPENAI
            : config.getProtocol().toLowerCase(Locale.ROOT);
    }
    
    private static String effectiveRegion(CopilotProperties config) {
        return StringUtils.isBlank(config.getRegion()) ? REGION_GLOBAL
            : config.getRegion().toLowerCase(Locale.ROOT);
    }
    
    private static String effectiveBaseUrl(CopilotProperties config, String protocol,
        String region) {
        if (StringUtils.isNotBlank(config.getBaseUrl())) {
            return stripTrailingSlash(config.getBaseUrl());
        }
        return METADATA.getProtocols().stream().filter(item -> item.getName().equals(protocol))
            .flatMap(item -> item.getEndpoints().stream())
            .filter(item -> item.getRegion().equals(region)).map(
                CopilotProviderMetadata.EndpointMetadata::getBaseUrl)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No MiniMax endpoint for protocol " + protocol + " and region " + region));
    }
    
    private static void validateBaseUrl(String baseUrl, String protocol) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid MiniMax base URL", e);
        }
        if (uri.getHost() == null || (!"https".equalsIgnoreCase(uri.getScheme())
            && !"http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("MiniMax base URL must be an absolute HTTP URL");
        }
        String expectedSuffix = PROTOCOL_ANTHROPIC.equals(protocol) ? "/anthropic" : "/v1";
        if (!stripTrailingSlash(uri.getPath()).endsWith(expectedSuffix)) {
            throw new IllegalArgumentException(
                "MiniMax " + protocol + " base URL must end with " + expectedSuffix);
        }
    }
    
    private static CopilotProviderMetadata.ProtocolMetadata protocol(String name,
        String globalBaseUrl, String chinaBaseUrl) {
        return new CopilotProviderMetadata.ProtocolMetadata(name,
            List.of(new CopilotProviderMetadata.EndpointMetadata(REGION_GLOBAL, globalBaseUrl),
                new CopilotProviderMetadata.EndpointMetadata(REGION_CHINA, chinaBaseUrl)));
    }
    
    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
