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

package com.alibaba.nacos.plugin.control.configs;

import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * control configs params.
 *
 * @author shiyiyue
 */
@Component
public class ControlConfigs {
    
    private static ControlConfigs instance = null;
    
    public static ControlConfigs getInstance() {
        if (instance == null) {
            synchronized (ControlConfigs.class) {
                try {
                    instance = ApplicationUtils.getBean(ControlConfigs.class);
                } catch (Throwable throwable) {
                    Loggers.CONTROL
                            .warn("Fail to get control configs bean from spring context,use default constructor instance",
                                    throwable);
                }
                if (instance == null) {
                    instance = new ControlConfigs();
                }
            }
        }
        
        return instance;
    }
    
    public static void setInstance(ControlConfigs instance) {
        ControlConfigs.instance = instance;
    }
    
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
    
    public String getTpsBarrierCreator() {
        return tpsBarrierCreator;
    }
    
    public void setTpsBarrierCreator(String tpsBarrierCreator) {
        this.tpsBarrierCreator = tpsBarrierCreator;
    }
    
    public String getTpsRuleBarrierCreator() {
        return tpsRuleBarrierCreator;
    }
    
    public void setTpsRuleBarrierCreator(String tpsRuleBarrierCreator) {
        this.tpsRuleBarrierCreator = tpsRuleBarrierCreator;
    }
    
    public String getRuleExternalStorage() {
        return ruleExternalStorage;
    }
    
    public void setRuleExternalStorage(String ruleExternalStorage) {
        this.ruleExternalStorage = ruleExternalStorage;
    }
    
    public String getRuleParser() {
        return ruleParser;
    }
    
    public void setRuleParser(String ruleParser) {
        this.ruleParser = ruleParser;
    }
    
    public String getConnectionManager() {
        return connectionManager;
    }
    
    public void setConnectionManager(String connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    public String getConnectionRuntimeEjector() {
        return connectionRuntimeEjector;
    }
    
    public void setConnectionRuntimeEjector(String connectionRuntimeEjector) {
        this.connectionRuntimeEjector = connectionRuntimeEjector;
    }
    
    public String getTpsManager() {
        return tpsManager;
    }
    
    public void setTpsManager(String tpsManager) {
        this.tpsManager = tpsManager;
    }
}
