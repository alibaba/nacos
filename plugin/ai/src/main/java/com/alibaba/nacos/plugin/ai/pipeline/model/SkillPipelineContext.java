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

package com.alibaba.nacos.plugin.ai.pipeline.model;

import java.util.List;

/**
 * Skill-specific publish pipeline context, extending common fields with Skill's multi-file structure.
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public class SkillPipelineContext extends PublishPipelineContext {

    /**
     * Skill resource file content list (loaded from Storage, e.g. SKILL.md, template files, etc.).
     */
    private List<ResourceFileContent> files;

    public SkillPipelineContext() {
        setResourceType(PublishPipelineResourceType.SKILL);
    }

    public List<ResourceFileContent> getFiles() {
        return files;
    }

    public void setFiles(List<ResourceFileContent> files) {
        this.files = files;
    }
}

