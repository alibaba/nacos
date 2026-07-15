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

package com.alibaba.nacos.plugin.ai.storage.spi;

/**
 * Builder SPI for creating {@link AiResourceStorage} instances.
 *
 * <p>Since SPI-loaded classes are instantiated via no-arg constructors, this builder pattern allows
 * creating storage implementations. Each storage provider should implement this builder and register
 * it via SPI (META-INF/services).</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public interface AiResourceStorageBuilder {
    
    /**
     * Type identifier, corresponding to {@link AiResourceStorage#type()}.
     *
     * @return storage provider type, e.g. "nacos_config", "oss"
     */
    String type();
    
    /**
     * Build an {@link AiResourceStorage} instance.
     *
     * @return a fully initialized {@link AiResourceStorage} instance
     */
    AiResourceStorage build();
}
