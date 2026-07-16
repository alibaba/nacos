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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;

import java.util.Properties;

/**
 * Builder for {@link SkillSpectorPipelineService}.
 *
 * @author nacos
 */
public class SkillSpectorPipelineServiceBuilder implements PublishPipelineServiceBuilder {
    
    @Override
    public String pipelineId() {
        return SkillSpectorPipelineService.PIPELINE_ID;
    }
    
    @Override
    public PublishPipelineService build(Properties properties) {
        SkillSpectorPluginConfig config = SkillSpectorPluginConfig.fromProperties(properties);
        return new SkillSpectorPipelineService(config);
    }
}
