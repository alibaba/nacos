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

package com.alibaba.nacos.plugin.control.rule.storage;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.rule.ControlRuleChangeActivator;
import com.alibaba.nacos.plugin.control.spi.ExternalRuleStorageBuilder;
import org.slf4j.Logger;

import java.util.Collection;

/**
 * rule storage proxy.
 *
 * @author shiyiyue
 */
public class RuleStorageProxy {
    
    private static final Logger LOGGER = Loggers.CONTROL;
    
    private static final RuleStorageProxy INSTANCE = new RuleStorageProxy();
    
    private LocalDiskRuleStorage localDiskRuleStorage = null;
    
    private ExternalRuleStorage externalRuleStorage = null;
    
    ControlRuleChangeActivator controlRuleChangeActivator = null;
    
    private RuleStorageProxy() {
        String externalStorageType = ControlConfigs.getInstance().getRuleExternalStorage();
        if (StringUtils.isNotEmpty(externalStorageType)) {
            buildExternalStorage(externalStorageType);
        }
        initLocalStorage();
        controlRuleChangeActivator = new ControlRuleChangeActivator();
    }
    
    private void buildExternalStorage(String externalStorageType) {
        Collection<ExternalRuleStorageBuilder> externalRuleStorageBuilders = NacosServiceLoader
                .load(ExternalRuleStorageBuilder.class);
        for (ExternalRuleStorageBuilder each : externalRuleStorageBuilders) {
            LOGGER.info("Found persist rule storage of name : {}", externalStorageType);
            if (externalStorageType.equalsIgnoreCase(each.getName())) {
                try {
                    externalRuleStorage = each.buildExternalRuleStorage();
                } catch (Exception e) {
                    LOGGER.warn("Build external rule storage failed, the rules will not be persisted", e);
                }
                LOGGER.info("Build external rule storage of name {} finished", externalStorageType);
                break;
            }
        }
        if (externalRuleStorage == null && StringUtils.isNotBlank(externalStorageType)) {
            LOGGER.error("Fail to found persist rule storage of name : {}", externalStorageType);
        }
    }
    
    private void initLocalStorage() {
        localDiskRuleStorage = new LocalDiskRuleStorage();
        if (StringUtils.isNotBlank(ControlConfigs.getInstance().getLocalRuleStorageBaseDir())) {
            localDiskRuleStorage.setLocalRuleBaseDir(ControlConfigs.getInstance().getLocalRuleStorageBaseDir());
        }
    }
    
    public RuleStorage getLocalDiskStorage() {
        return localDiskRuleStorage;
    }
    
    public RuleStorage getExternalStorage() {
        return externalRuleStorage;
    }
    
    public static RuleStorageProxy getInstance() {
        return INSTANCE;
    }
}
