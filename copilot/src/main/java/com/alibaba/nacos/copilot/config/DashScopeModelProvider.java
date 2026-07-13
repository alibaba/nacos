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
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DashScope Copilot model provider.
 *
 * @author nacos
 */
@Order(0)
@Component
public class DashScopeModelProvider implements CopilotModelProvider {
    
    private static final String NAME = "DashScope";
    
    private static final String DEFAULT_MODEL = "qwen-turbo";
    
    private static final CopilotProviderMetadata METADATA = new CopilotProviderMetadata(NAME,
        DEFAULT_MODEL, null, null, true, List.of(model("qwen-turbo"), model("qwen-plus"),
            model("qwen-max"), model("qwen-7b-chat"), model("qwen-14b-chat"),
            model("qwen-72b-chat"), model("qwen3-turbo"), model("qwen3-plus"),
            model("qwen3-max"), model("qwen3-7b-instruct"), model("qwen3-14b-instruct"),
            model("qwen3-32b-instruct"), model("qwen3-72b-instruct")),
        List.of());
    
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
        if (StringUtils.isBlank(config.getModel())) {
            throw new IllegalArgumentException("Copilot model cannot be blank");
        }
    }
    
    @Override
    public Model createModel(CopilotProperties config, String apiKey) {
        String modelName =
            StringUtils.isBlank(config.getModel()) ? DEFAULT_MODEL : config.getModel();
        return DashScopeChatModel.builder().apiKey(apiKey).modelName(modelName).stream(true)
            .enableThinking(true).build();
    }
    
    private static CopilotProviderMetadata.ModelMetadata model(String modelId) {
        return new CopilotProviderMetadata.ModelMetadata(modelId, null, List.of("text"), List.of());
    }
}
