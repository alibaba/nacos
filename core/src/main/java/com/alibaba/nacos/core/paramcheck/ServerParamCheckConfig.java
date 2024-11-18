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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * The type Server param check config.
 *
 * @author zhuoguang
 */
public class ServerParamCheckConfig extends AbstractDynamicConfig {
    
    private static final String PARAM_CHECK = "ParamCheck";
    
    private static final ServerParamCheckConfig INSTANCE = new ServerParamCheckConfig();
    
    private boolean paramCheckEnabled = true;
    
    private String activeParamChecker = "default";
    
    protected ServerParamCheckConfig() {
        super(PARAM_CHECK);
        resetConfig();
    }
    
    public static ServerParamCheckConfig getInstance() {
        return INSTANCE;
    }
    
    @Override
    protected void getConfigFromEnv() {
        paramCheckEnabled = EnvUtil.getProperty("nacos.core.param.check.enabled", Boolean.class, true);
        activeParamChecker = EnvUtil.getProperty("nacos.core.param.check.checker", String.class, "default");
    }
    
    public boolean isParamCheckEnabled() {
        return paramCheckEnabled;
    }
    
    public void setParamCheckEnabled(boolean paramCheckEnabled) {
        this.paramCheckEnabled = paramCheckEnabled;
    }
    
    public String getActiveParamChecker() {
        return activeParamChecker;
    }
    
    public void setActiveParamChecker(String activeParamChecker) {
        this.activeParamChecker = activeParamChecker;
    }
    
    @Override
    protected String printConfig() {
        return "ParamCheckConfig{" + "paramCheckEnabled=" + paramCheckEnabled
                + "activeParamChecker=" + activeParamChecker + "}";
    }
    
}
