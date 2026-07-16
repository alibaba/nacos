/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;

import java.util.Properties;

/**
 * Builder for {@link SkillScannerPipelineService}. Checks if skill-scanner is installed
 * during initialization and logs installation instructions if not found.
 *
 * <p>Optional node properties (via {@code nacos.plugin.ai-pipeline.skill-scanner.*}):</p>
 * <ul>
 *   <li>{@code command} - CLI command or executable path</li>
 *   <li>{@code use-llm} - {@code true} to pass {@code --use-llm}</li>
 *   <li>{@code llm-api-key} - sets subprocess {@code SKILL_SCANNER_LLM_API_KEY}</li>
 *   <li>{@code llm-model} - sets subprocess {@code SKILL_SCANNER_LLM_MODEL}</li>
 *   <li>{@code llm-provider} - value for {@code --llm-provider}</li>
 *   <li>{@code enable-meta} - {@code true} to pass {@code --enable-meta}</li>
 * </ul>
 * Historical camel-case keys and the {@code executable}/{@code path} command aliases remain
 * supported for compatibility.
 *
 * @author qiacheng.cxy
 */
public class SkillScannerPipelineServiceBuilder implements PublishPipelineServiceBuilder {
    
    @Override
    public String pipelineId() {
        return "skill-scanner";
    }
    
    @Override
    public PublishPipelineService build(Properties properties) {
        SkillScannerPluginConfig config = SkillScannerPluginConfig.fromProperties(properties);
        return new SkillScannerPipelineService(config);
    }
}
