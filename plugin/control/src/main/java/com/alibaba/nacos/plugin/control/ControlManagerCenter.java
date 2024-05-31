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

package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.DefaultConnectionControlManager;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.spi.ControlManagerBuilder;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.DefaultTpsControlManager;

import java.util.Optional;

/**
 * control manager center.
 *
 * @author shiyiyue
 */
public class ControlManagerCenter {
    
    static volatile ControlManagerCenter instance = null;
    
    private final RuleStorageProxy ruleStorageProxy;
    
    private TpsControlManager tpsControlManager;
    
    private ConnectionControlManager connectionControlManager;
    
    private ControlManagerCenter() {
        ruleStorageProxy = RuleStorageProxy.getInstance();
        Optional<ControlManagerBuilder> controlManagerBuilder = findTargetControlManagerBuilder();
        if (controlManagerBuilder.isPresent()) {
            initConnectionManager(controlManagerBuilder.get());
            initTpsControlManager(controlManagerBuilder.get());
        } else {
            buildNoLimitControlManagers();
        }
    }
    
    private void initConnectionManager(ControlManagerBuilder controlManagerBuilder) {
        try {
            connectionControlManager = controlManagerBuilder.buildConnectionControlManager();
            Loggers.CONTROL.info("Build connection control manager, class={}",
                    connectionControlManager.getClass().getCanonicalName());
        } catch (Exception e) {
            Loggers.CONTROL.warn("Build connection control manager failed, use no limit manager replaced.", e);
            connectionControlManager = new DefaultConnectionControlManager();
        }
    }
    
    private void initTpsControlManager(ControlManagerBuilder controlManagerBuilder) {
        try {
            tpsControlManager = controlManagerBuilder.buildTpsControlManager();
            Loggers.CONTROL
                    .info("Build tps control manager, class={}", tpsControlManager.getClass().getCanonicalName());
        } catch (Exception e) {
            Loggers.CONTROL.warn("Build tps control manager failed, use no limit manager replaced.", e);
            tpsControlManager = new DefaultTpsControlManager();
        }
    }
    
    private Optional<ControlManagerBuilder> findTargetControlManagerBuilder() {
        String controlManagerType = ControlConfigs.getInstance().getControlManagerType();
        if (StringUtils.isEmpty(controlManagerType)) {
            Loggers.CONTROL.info("Not configure type of control plugin, no limit control for current node.");
            return Optional.empty();
        }
        for (ControlManagerBuilder each : NacosServiceLoader.load(ControlManagerBuilder.class)) {
            Loggers.CONTROL.info("Found control manager plugin of name={}", each.getName());
            if (controlManagerType.equalsIgnoreCase(each.getName())) {
                return Optional.of(each);
            }
        }
        Loggers.CONTROL.warn("Not found control manager plugin of name");
        return Optional.empty();
    }
    
    private void buildNoLimitControlManagers() {
        connectionControlManager = new DefaultConnectionControlManager();
        tpsControlManager = new DefaultTpsControlManager();
    }
    
    public RuleStorageProxy getRuleStorageProxy() {
        return ruleStorageProxy;
    }
    
    public TpsControlManager getTpsControlManager() {
        return tpsControlManager;
    }
    
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManager;
    }
    
    public static ControlManagerCenter getInstance() {
        if (instance == null) {
            synchronized (ControlManagerCenter.class) {
                if (instance == null) {
                    instance = new ControlManagerCenter();
                }
            }
        }
        return instance;
    }
    
    public void reloadTpsControlRule(String pointName, boolean external) {
        NotifyCenter.publishEvent(new TpsControlRuleChangeEvent(pointName, external));
    }
    
    public void reloadConnectionControlRule(boolean external) {
        NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(external));
    }
    
}
