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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;

import java.util.List;

/**
 * SPI interface for reading legacy prompt data during migration.
 *
 * <p>Different environments (open-source Nacos vs commercial) may store prompt data
 * in different legacy formats. Implementations provide the ability to scan and read
 * legacy prompt data for migration to the new DB + typed storage architecture.</p>
 *
 * <p>The default implementation ({@code nacos}) reads from Nacos Config
 * ({@code nacos-ai-prompt} group). Commercial implementations can provide their own
 * {@code @Component} bean to override.</p>
 *
 * @author nacos
 * @since 3.2.0
 */
public interface PromptLegacyDataReader {
    
    /**
     * Provider type identifier. Used to select the active reader via configuration
     * property {@code nacos.ai.prompt.migration.provider}.
     *
     * @return type string, e.g. "nacos"
     */
    String type();
    
    /**
     * Scan legacy storage and return all prompts with their metadata and version lists.
     * Version content is NOT included; use {@link #readVersionContent} to load on demand.
     *
     * @return list of legacy prompt data
     */
    List<LegacyPromptData> scanLegacyPrompts();
    
    /**
     * Read the content of a specific prompt version from legacy storage.
     *
     * @param promptKey prompt key
     * @param version   version string
     * @return version info with template/variables/srcUser/commitMsg, or null if not found
     */
    PromptVersionInfo readVersionContent(String promptKey, String version);
}
