/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.control;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.configs.ControlConfigsInitializer;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * spring value for control configs.
 *
 * @author shiyiyue
 */
public class SpringValueConfigsInitializer implements ControlConfigsInitializer {
    
    private static final String PREFIX = "nacos.plugin.control.";
    
    private static final String CONNECTION_RUNTIME_EJECTOR = PREFIX + "connection.runtime.ejector";
    
    private static final String CONTROL_MANAGER_TYPE = PREFIX + "manager.type";
    
    private static final String RULE_EXTERNAL_STORAGE = PREFIX + "rule.external.storage";
    
    private static final String LOCAL_RULE_STORAGE_BASE_DIR = PREFIX + "rule.local.basedir";
    
    private static final String DEFAULT_CONNECTION_RUNTIME_EJECTOR = "nacos";
    
    @Override
    public void initialize(ControlConfigs controlConfigs) {
        controlConfigs.setConnectionRuntimeEjector(
                EnvUtil.getProperty(CONNECTION_RUNTIME_EJECTOR, DEFAULT_CONNECTION_RUNTIME_EJECTOR));
        String localRuleStorageBaseDir = EnvUtil.getProperty(LOCAL_RULE_STORAGE_BASE_DIR);
        if (StringUtils.isNotBlank(localRuleStorageBaseDir)) {
            controlConfigs.setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        } else {
            controlConfigs.setLocalRuleStorageBaseDir(EnvUtil.getNacosHome());
        }
        controlConfigs.setRuleExternalStorage(EnvUtil.getProperty(RULE_EXTERNAL_STORAGE));
        controlConfigs.setControlManagerType(EnvUtil.getProperty(CONTROL_MANAGER_TYPE));
    }
}
