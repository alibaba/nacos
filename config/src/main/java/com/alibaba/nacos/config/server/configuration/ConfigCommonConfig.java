/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Nacos config common configs.
 *
 * @author blake.qiu
 */

public class ConfigCommonConfig extends AbstractDynamicConfig {
    
    private static final String CONFIG_COMMON = "ConfigCommon";
    
    private static final ConfigCommonConfig INSTANCE = new ConfigCommonConfig();
    
    private int maxPushRetryTimes = 50;
    
    private boolean derbyOpsEnabled = false;
    
    private ConfigCommonConfig() {
        super(CONFIG_COMMON);
        resetConfig();
    }
    
    public static ConfigCommonConfig getInstance() {
        return INSTANCE;
    }
    
    public int getMaxPushRetryTimes() {
        return maxPushRetryTimes;
    }
    
    public void setMaxPushRetryTimes(int maxPushRetryTimes) {
        this.maxPushRetryTimes = maxPushRetryTimes;
    }
    
    public boolean isDerbyOpsEnabled() {
        return derbyOpsEnabled;
    }
    
    public void setDerbyOpsEnabled(boolean derbyOpsEnabled) {
        this.derbyOpsEnabled = derbyOpsEnabled;
    }
    
    @Override
    protected void getConfigFromEnv() {
        maxPushRetryTimes = EnvUtil.getProperty("nacos.config.push.maxRetryTime", Integer.class, 50);
        derbyOpsEnabled = EnvUtil.getProperty("nacos.config.derby.ops.enabled", Boolean.class, false);
    }
    
    @Override
    protected String printConfig() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "ConfigCommonConfig{" + "maxPushRetryTimes=" + maxPushRetryTimes + ", derbyOpsEnabled=" + derbyOpsEnabled
                + '}';
    }
}
