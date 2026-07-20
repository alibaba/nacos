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

package com.alibaba.nacos.ai.pipeline;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal configurable pipeline service for unit tests.
 *
 * @author Nacos
 */
abstract class TestPublishPipelineService implements PublishPipelineService {
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return Collections.emptyList();
    }
    
    @Override
    public void applyConfig(Map<String, String> config) {
    }
    
    @Override
    public Map<String, String> getCurrentConfig() {
        return Collections.emptyMap();
    }
}
