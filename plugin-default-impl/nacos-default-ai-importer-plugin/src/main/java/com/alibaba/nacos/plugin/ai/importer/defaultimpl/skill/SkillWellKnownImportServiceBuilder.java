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

import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;

import java.util.Properties;

/**
 * Builder for the built-in well-known Skill import service.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class SkillWellKnownImportServiceBuilder implements AiResourceImportServiceBuilder {
    
    public static final String IMPORTER_TYPE = "skills-well-known";
    
    @Override
    public String importerType() {
        return IMPORTER_TYPE;
    }
    
    @Override
    public AiResourceImportService build(Properties properties) {
        return new SkillWellKnownImportService();
    }
}
