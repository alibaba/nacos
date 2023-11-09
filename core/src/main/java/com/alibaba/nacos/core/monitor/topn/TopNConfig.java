/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.monitor.topn;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.concurrent.TimeUnit;

/**
 * TopN configurations.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class TopNConfig extends AbstractDynamicConfig {
    
    private static final String TOP_N = "topN";
    
    private static final TopNConfig INSTANCE = new TopNConfig();
    
    private static final String TOP_N_PREFIX = "nacos.core.monitor.topn.";
    
    private static final String ENABLED_KEY = TOP_N_PREFIX + "enabled";
    
    private static final String COUNT_KEY = TOP_N_PREFIX + "count";
    
    private static final String INTERNAL_MS_KEY = TOP_N_PREFIX + "internalMs";
    
    private static final boolean DEFAULT_ENABLED = true;
    
    private static final int DEFAULT_COUNT = 10;
    
    private static final long DEFAULT_INTERNAL_MS = TimeUnit.SECONDS.toMillis(30);
    
    private boolean enabled;
    
    private int topNCount;
    
    private long internalMs;
    
    private TopNConfig() {
        super(TOP_N);
    }
    
    @Override
    protected void getConfigFromEnv() {
        enabled = EnvUtil.getProperty(ENABLED_KEY, Boolean.class, DEFAULT_ENABLED);
        topNCount = EnvUtil.getProperty(COUNT_KEY, Integer.class, DEFAULT_COUNT);
        internalMs = EnvUtil.getProperty(INTERNAL_MS_KEY, Long.class, DEFAULT_INTERNAL_MS);
    }
    
    @Override
    protected String printConfig() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "TopNConfig{" + "enabled=" + enabled + ", topNCount=" + topNCount + ", internalMs=" + internalMs + '}';
    }
    
    public static TopNConfig getInstance() {
        return INSTANCE;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getTopNCount() {
        return topNCount;
    }
    
    public long getInternalMs() {
        return internalMs;
    }
}
