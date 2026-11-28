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

package com.alibaba.nacos.api.ai.model.skills;

import java.util.Map;

/**
 * Runtime Skill metadata returned by the client Skill query API.
 *
 * <p>This model describes one resolved online Skill version without carrying the Skill package
 * bytes. It is intended for consumers that need to detect the current version before deciding
 * whether to download the ZIP content. The inherited {@code description} and {@code updateTime}
 * are resource-level values; {@link #version} is the resolved online version token, and
 * {@link #frontMatter} is the display-version projection and is null when the resolved version is
 * not the display version.</p>
 *
 * @author nacos
 * @since 3.3.1
 */
public class SkillInfo extends SkillBasicInfo {
    
    /**
     * Resolved online version, after explicit version, label, or latest routing.
     */
    private String version;
    
    /**
     * Whether the Skill is globally enabled. true=enable, false=disable.
     */
    private boolean enable;
    
    /**
     * Label to version mapping, e.g. {"latest":"v3","stable":"v2"}.
     */
    private Map<String, String> labels;
    
    /**
     * Display version frontmatter projection. Null when the resolved version is not the display
     * version or when the metadata is unavailable.
     */
    private Map<String, String> frontMatter;
    
    /**
     * Whether custom fields were omitted or shortened in the frontmatter projection. Null when
     * frontmatter is unavailable.
     */
    private Boolean frontMatterTruncated;
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public boolean isEnable() {
        return enable;
    }
    
    public void setEnable(boolean enable) {
        this.enable = enable;
    }
    
    public Map<String, String> getLabels() {
        return labels;
    }
    
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    
    public Map<String, String> getFrontMatter() {
        return frontMatter;
    }
    
    public void setFrontMatter(Map<String, String> frontMatter) {
        this.frontMatter = frontMatter;
    }
    
    public Boolean getFrontMatterTruncated() {
        return frontMatterTruncated;
    }
    
    public void setFrontMatterTruncated(Boolean frontMatterTruncated) {
        this.frontMatterTruncated = frontMatterTruncated;
    }
}
