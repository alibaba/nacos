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
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.DefaultConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.event.ConnectionLimitRuleChangeEvent;
import com.alibaba.nacos.plugin.control.event.TpsControlRuleChangeEvent;
import com.alibaba.nacos.plugin.control.rule.parser.ConnectionControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.parser.TpsControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.DefaultTpsControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * control manager center.
 *
 * @author shiyiyue
 */
public class ControlManagerCenter {
    
    static volatile ControlManagerCenter instance = null;
    
    private final RuleStorageProxy ruleStorageProxy;
    
    private final Object installationMonitor = new Object();
    
    private final Set<String> registeredTpsPoints = new LinkedHashSet<>();
    
    private final ConnectionControlManager bootstrapConnectionControlManager;
    
    private final TpsControlManager bootstrapTpsControlManager;
    
    private final AtomicReference<ControlManagerBundle> managerBundle;
    
    private final TpsControlManager tpsControlManagerFacade;
    
    private final ConnectionControlManager connectionControlManagerFacade;
    
    ControlManagerCenter() {
        ruleStorageProxy = RuleStorageProxy.getInstance();
        bootstrapConnectionControlManager = new BootstrapConnectionControlManager();
        bootstrapTpsControlManager = new DefaultTpsControlManager();
        managerBundle = new AtomicReference<>();
        tpsControlManagerFacade = new DelegatingTpsControlManager();
        connectionControlManagerFacade = new DelegatingConnectionControlManager();
    }
    
    public RuleStorageProxy getRuleStorageProxy() {
        return ruleStorageProxy;
    }
    
    public TpsControlManager getTpsControlManager() {
        return tpsControlManagerFacade;
    }
    
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManagerFacade;
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
    
    /**
     * Install the selected startup manager bundle.
     *
     * @param targetManagerBundle selected manager bundle
     */
    public void install(ControlManagerBundle targetManagerBundle) {
        Objects.requireNonNull(targetManagerBundle, "Control manager bundle cannot be null");
        synchronized (installationMonitor) {
            if (managerBundle.get() != null) {
                throw new IllegalStateException(
                    "Control manager bundle has already been installed");
            }
            for (String pointName : registeredTpsPoints) {
                targetManagerBundle.getTpsControlManager().registerTpsPoint(pointName);
            }
            managerBundle.set(targetManagerBundle);
            Loggers.CONTROL.info(
                "Installed control manager bundle, connection={}, tps={}",
                targetManagerBundle.getConnectionControlManager().getName(),
                targetManagerBundle.getTpsControlManager().getName());
        }
    }
    
    public void reloadTpsControlRule(String pointName, boolean external) {
        NotifyCenter.publishEvent(new TpsControlRuleChangeEvent(pointName, external));
    }
    
    public void reloadConnectionControlRule(boolean external) {
        NotifyCenter.publishEvent(new ConnectionLimitRuleChangeEvent(external));
    }
    
    private TpsControlManager currentTpsControlManager() {
        ControlManagerBundle current = managerBundle.get();
        return current == null ? bootstrapTpsControlManager : current.getTpsControlManager();
    }
    
    private ConnectionControlManager currentConnectionControlManager() {
        ControlManagerBundle current = managerBundle.get();
        return current == null ? bootstrapConnectionControlManager
            : current.getConnectionControlManager();
    }
    
    private final class DelegatingTpsControlManager extends TpsControlManager {
        
        @Override
        public TpsControlRuleParser getTpsControlRuleParser() {
            return currentTpsControlManager().getTpsControlRuleParser();
        }
        
        @Override
        public void registerTpsPoint(String pointName) {
            synchronized (installationMonitor) {
                registeredTpsPoints.add(pointName);
                ControlManagerBundle current = managerBundle.get();
                if (current != null) {
                    current.getTpsControlManager().registerTpsPoint(pointName);
                }
            }
        }
        
        @Override
        public Map<String, TpsBarrier> getPoints() {
            return currentTpsControlManager().getPoints();
        }
        
        @Override
        public Map<String, TpsControlRule> getRules() {
            return currentTpsControlManager().getRules();
        }
        
        @Override
        public void applyTpsRule(String pointName, TpsControlRule rule) {
            currentTpsControlManager().applyTpsRule(pointName, rule);
        }
        
        @Override
        public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
            return currentTpsControlManager().check(tpsRequest);
        }
        
        @Override
        public String getName() {
            return currentTpsControlManager().getName();
        }
    }
    
    private final class DelegatingConnectionControlManager extends ConnectionControlManager {
        
        private DelegatingConnectionControlManager() {
            super(false);
        }
        
        @Override
        public String getName() {
            return currentConnectionControlManager().getName();
        }
        
        @Override
        public ConnectionControlRuleParser getConnectionControlRuleParser() {
            return currentConnectionControlManager().getConnectionControlRuleParser();
        }
        
        @Override
        public ConnectionControlRule getConnectionLimitRule() {
            return currentConnectionControlManager().getConnectionLimitRule();
        }
        
        @Override
        public void applyConnectionLimitRule(ConnectionControlRule connectionControlRule) {
            currentConnectionControlManager().applyConnectionLimitRule(connectionControlRule);
        }
        
        @Override
        public ConnectionCheckResponse check(ConnectionCheckRequest connectionCheckRequest) {
            return currentConnectionControlManager().check(connectionCheckRequest);
        }
    }
    
    private static final class BootstrapConnectionControlManager
        extends DefaultConnectionControlManager {
        
        private BootstrapConnectionControlManager() {
            super(false);
        }
    }
    
}
