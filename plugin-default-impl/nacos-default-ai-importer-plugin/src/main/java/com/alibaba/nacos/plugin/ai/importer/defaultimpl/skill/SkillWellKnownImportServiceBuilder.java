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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.skill;

import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.defaultimpl.AbstractAiResourceImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;

import java.util.Collections;

/**
 * Configurable Skill well-known discovery import plugin.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class SkillWellKnownImportServiceBuilder
    extends AbstractAiResourceImportServiceBuilder {
    
    public static final String PLUGIN_NAME = "skills-well-known";
    
    public static final String IMPORTER_TYPE = "skills-well-known";
    
    /**
     * Legacy Skill well-known importer configuration prefix.
     *
     * @deprecated use {@code nacos.plugin.ai-resource-import.skills-well-known.} instead. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Deprecated
    private static final String LEGACY_PREFIX =
        "nacos.plugin.ai.importer.skills.well-known.";
    
    public SkillWellKnownImportServiceBuilder() {
        super(PLUGIN_NAME, IMPORTER_TYPE, "Skill Well-known Registry",
            "Import Skills from a well-known Skill discovery endpoint.",
            Collections.singleton(AiResourceImportConstants.RESOURCE_TYPE_SKILL), null,
            LEGACY_PREFIX, LEGACY_PREFIX + "url", LEGACY_PREFIX + "endpoint");
    }
    
    @Override
    protected AiResourceImportService createService(ConfigSnapshot config) {
        return new SkillWellKnownImportService(config.getEndpoint(), config.isAllowHttp(),
            config.isAllowPrivateNetwork(), config.getMaxItemCount(),
            config.getMaxArtifactSize());
    }
}
