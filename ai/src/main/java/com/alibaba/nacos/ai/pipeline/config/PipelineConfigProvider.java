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

package com.alibaba.nacos.ai.pipeline.config;

import com.alibaba.nacos.ai.pipeline.model.PipelineConfig;

/**
 * Abstract provider for pipeline configuration, decoupling the config source (file, database, etc.).
 *
 * @author kiro
 * @since 3.2.0
 */
public interface PipelineConfigProvider {
    
    /**
     * Get the current pipeline configuration.
     *
     * @return pipeline configuration, never null
     */
    PipelineConfig getConfig();
    
    /**
     * Configuration source type identifier, e.g. "file", "database".
     *
     * @return type string
     */
    String type();
}
