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

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Dynamic configuration for the 'reviewed' status feature.
 *
 * <p>When enabled, pipeline completion (both approved and rejected) transitions version status
 * to {@code reviewed} instead of the legacy behavior (rejected → draft, approved → reviewed).
 * Users must explicitly call redraft to return to draft.</p>
 *
 * <p>Configuration key: {@code nacos.plugin.ai-pipeline.reviewed-status.enabled} (default: false).</p>
 *
 * @author nacos
 * @since 3.2.1
 */
public class ReviewedStatusConfig extends AbstractDynamicConfig {
    
    private static final String CONFIG_NAME = "ReviewedStatus";
    
    private static final String KEY_ENABLED = "nacos.plugin.ai-pipeline.reviewed-status.enabled";
    
    private static final boolean DEFAULT_ENABLED = false;
    
    private static final ReviewedStatusConfig INSTANCE = new ReviewedStatusConfig();
    
    private volatile boolean enabled = DEFAULT_ENABLED;
    
    private ReviewedStatusConfig() {
        super(CONFIG_NAME);
        resetConfig();
    }
    
    public static ReviewedStatusConfig getInstance() {
        return INSTANCE;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    protected void getConfigFromEnv() {
        this.enabled = EnvUtil.getProperty(KEY_ENABLED, Boolean.class, DEFAULT_ENABLED);
    }
    
    @Override
    protected String printConfig() {
        return "ReviewedStatusConfig{enabled=" + enabled + "}";
    }
}
