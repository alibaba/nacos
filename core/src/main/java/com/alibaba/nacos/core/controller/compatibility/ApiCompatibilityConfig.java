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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Compatible for old version API configuration.
 *
 * @author xiweng.yy
 */
public class ApiCompatibilityConfig extends AbstractDynamicConfig {
    
    private static final String API_COMPATIBILITY = "ApiCompatibility";
    
    private static final ApiCompatibilityConfig INSTANCE = new ApiCompatibilityConfig();
    
    private static final String PREFIX = "nacos.core.api.compatibility";
    
    public static final String CLIENT_API_COMPATIBILITY_KEY = PREFIX + ".client.enabled";
    
    public static final String CONSOLE_API_COMPATIBILITY_KEY = PREFIX + ".console.enabled";
    
    public static final String ADMIN_API_COMPATIBILITY_KEY = PREFIX + ".admin.enabled";
    
    private boolean clientApiCompatibility;
    
    private boolean consoleApiCompatibility;
    
    private boolean adminApiCompatibility;
    
    protected ApiCompatibilityConfig() {
        super(API_COMPATIBILITY);
        resetConfig();
    }
    
    public static ApiCompatibilityConfig getInstance() {
        return INSTANCE;
    }
    
    @Override
    protected void getConfigFromEnv() {
        clientApiCompatibility = EnvUtil.getProperty(CLIENT_API_COMPATIBILITY_KEY, Boolean.class, true);
        consoleApiCompatibility = EnvUtil.getProperty(CONSOLE_API_COMPATIBILITY_KEY, Boolean.class, false);
        adminApiCompatibility = EnvUtil.getProperty(ADMIN_API_COMPATIBILITY_KEY, Boolean.class, false);
    }
    
    @Override
    protected String printConfig() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "ApiCompatibilityConfig{" + "clientApiCompatibility=" + clientApiCompatibility
                + ", consoleApiCompatibility=" + consoleApiCompatibility + ", adminApiCompatibility="
                + adminApiCompatibility + '}';
    }
    
    public boolean isClientApiCompatibility() {
        return clientApiCompatibility;
    }
    
    public void setClientApiCompatibility(boolean clientApiCompatibility) {
        this.clientApiCompatibility = clientApiCompatibility;
    }
    
    public boolean isConsoleApiCompatibility() {
        return consoleApiCompatibility;
    }
    
    public void setConsoleApiCompatibility(boolean consoleApiCompatibility) {
        this.consoleApiCompatibility = consoleApiCompatibility;
    }
    
    public boolean isAdminApiCompatibility() {
        return adminApiCompatibility;
    }
    
    public void setAdminApiCompatibility(boolean adminApiCompatibility) {
        this.adminApiCompatibility = adminApiCompatibility;
    }
}
