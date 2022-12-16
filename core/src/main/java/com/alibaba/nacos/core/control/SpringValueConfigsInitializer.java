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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * spring value for control configs.
 *
 * @author shiyiyue
 */
@Component
public class SpringValueConfigsInitializer implements ControlConfigsInitializer {
    
    @Value("${nacos.plugin.control.tps.barrier.creator:nacos}")
    private String tpsBarrierCreator = "nacos";
    
    @Value("${nacos.plugin.control.tps.barrier.rule.creator:nacos}")
    private String tpsRuleBarrierCreator = "nacos";
    
    @Value("${nacos.plugin.control.connection.runtime.ejector:nacos}")
    private String connectionRuntimeEjector = "nacos";
    
    @Value("${nacos.plugin.control.connection.manager:nacos}")
    private String connectionManager = "nacos";
    
    @Value("${nacos.plugin.control.tps.manager:nacos}")
    private String tpsManager = "nacos";
    
    @Value("${nacos.plugin.control.rule.external.storage:}")
    private String ruleExternalStorage = "";
    
    @Value("${nacos.plugin.control.rule.parser:nacos}")
    private String ruleParser = "nacos";
    
    @Value("${nacos.plugin.control.rule.local.basedir:}")
    private String localRuleStorageBaseDir = "";
    
    @Override
    public void initialize(ControlConfigs controlConfigs) {
        controlConfigs.setTpsManager(tpsManager);
        controlConfigs.setTpsBarrierCreator(tpsBarrierCreator);
        controlConfigs.setTpsRuleBarrierCreator(tpsRuleBarrierCreator);
        
        controlConfigs.setConnectionRuntimeEjector(connectionRuntimeEjector);
        controlConfigs.setConnectionManager(connectionManager);
        
        controlConfigs.setRuleParser(ruleParser);
        if (StringUtils.isNotBlank(localRuleStorageBaseDir)) {
            controlConfigs.setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        } else {
            controlConfigs.setLocalRuleStorageBaseDir(EnvUtil.getNacosHome());
        }
        controlConfigs.setRuleExternalStorage(ruleExternalStorage);
        
    }
}
