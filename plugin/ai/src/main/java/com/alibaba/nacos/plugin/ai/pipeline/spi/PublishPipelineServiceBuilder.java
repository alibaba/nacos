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

package com.alibaba.nacos.plugin.ai.pipeline.spi;

import java.util.Properties;

/**
 * Builder SPI for creating {@link PublishPipelineService} instances.
 *
 * <p>Since SPI-loaded classes are instantiated via no-arg constructors, this builder pattern allows
 * creating pipeline service implementations. Each pipeline plugin should implement this builder and
 * register it via SPI (META-INF/services).</p>
 *
 * <p>The {@link #build(Properties)} method receives per-plugin configuration properties from the
 * pipeline config (for example {@code nacos.plugin.{pluginName}.{type}.*}), allowing each plugin
 * to be initialized with custom parameters such as API endpoints, timeouts, etc.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public interface PublishPipelineServiceBuilder {
    
    /**
     * Pipeline plugin identifier, corresponding to {@link PublishPipelineService#pipelineId()}.
     *
     * @return pipeline plugin id, e.g. "ai-review", "manual-confirm"
     */
    String pipelineId();
    
    /**
     * Build a {@link PublishPipelineService} instance with the given configuration properties.
     *
     * <p>The properties are sourced from pipeline configuration, keyed by this builder's
     * {@link #pipelineId()}. For example, if pipelineId is "ai-review", properties may contain
     * entries like "endpoint", "timeout", etc.</p>
     *
     * @param properties per-node configuration properties, never null (may be empty)
     * @return a fully initialized {@link PublishPipelineService} instance
     */
    PublishPipelineService build(Properties properties);
}
