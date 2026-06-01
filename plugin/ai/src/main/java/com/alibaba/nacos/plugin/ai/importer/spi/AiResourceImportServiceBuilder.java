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

package com.alibaba.nacos.plugin.ai.importer.spi;

import java.util.Properties;

/**
 * Builder SPI for creating {@link AiResourceImportService} instances.
 *
 * <p>Since SPI-loaded classes are instantiated via no-arg constructors, this builder pattern allows
 * importers to be initialized with plugin-owned configuration.</p>
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public interface AiResourceImportServiceBuilder {
    
    /**
     * Importer implementation name.
     *
     * @return importer type, e.g. "mcp-registry"; corresponds to
     *         {@link AiResourceImportService#importerType()}
     */
    String importerType();
    
    /**
     * Build an {@link AiResourceImportService} instance with the given properties.
     *
     * @param properties importer configuration properties, never null
     * @return initialized import service
     */
    AiResourceImportService build(Properties properties);
}
