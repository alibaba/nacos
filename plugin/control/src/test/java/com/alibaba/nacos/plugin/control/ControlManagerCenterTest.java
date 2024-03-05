/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import com.alibaba.nacos.plugin.control.utils.EnvUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;

public class ControlManagerCenterTest {
    
    @Before
    public void initInstance() throws NoSuchFieldException, IllegalAccessException {
        //reset instance for reload spi
        Field instanceControlConfigs = ControlConfigs.class.getDeclaredField("instance");
        instanceControlConfigs.setAccessible(true);
        instanceControlConfigs.set(null, null);
        Field instanceControlManagerCenter = ControlManagerCenter.class.getDeclaredField("instance");
        instanceControlManagerCenter.setAccessible(true);
        instanceControlManagerCenter.set(null, null);
    }
    
    private void resetRuleStorageProxy() {
        try {
            //reset instance for reload spi
            Field instanceRuleStorageProxy = RuleStorageProxy.class.getDeclaredField("INSTANCE");
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(instanceRuleStorageProxy, instanceRuleStorageProxy.getModifiers() & ~Modifier.FINAL);
            instanceRuleStorageProxy.setAccessible(true);
            Constructor<RuleStorageProxy> constructor = RuleStorageProxy.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Field modifiersFieldConstructor = Constructor.class.getDeclaredField("modifiers");
            modifiersFieldConstructor.setAccessible(true);
            modifiersFieldConstructor.setInt(constructor, constructor.getModifiers() & ~Modifier.PRIVATE);
            instanceRuleStorageProxy.set(null, constructor.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void testGetInstance() {
        ControlConfigs.getInstance().setControlManagerType("test");
        ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        ConnectionControlManager connectionControlManager = controlManagerCenter.getConnectionControlManager();
        Assert.assertEquals("testConnection", connectionControlManager.getName());
        TpsControlManager tpsControlManager = controlManagerCenter.getTpsControlManager();
        Assert.assertEquals("testTps", tpsControlManager.getName());
        Assert.assertNotNull(controlManagerCenter.getRuleStorageProxy());
    }
    
    @Test
    public void testGetInstanceWithDefault() {
        ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        ConnectionControlManager connectionControlManager = controlManagerCenter.getConnectionControlManager();
        Assert.assertEquals("noLimit", connectionControlManager.getName());
        TpsControlManager tpsControlManager = controlManagerCenter.getTpsControlManager();
        Assert.assertEquals("noLimit", tpsControlManager.getName());
    }
    
    @Test
    public void testReloadTpsControlRule() throws Exception {
        String localRuleStorageBaseDir =
                EnvUtils.getNacosHome() + File.separator + "tmpTps" + File.separator + "tps" + File.separator;
        ControlConfigs.getInstance().setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        resetRuleStorageProxy();
        final ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName("test");
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(100);
        ruleDetail.setRuleName("test");
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        String ruleContent = JacksonUtils.toJson(tpsControlRule);
        controlManagerCenter.getRuleStorageProxy().getLocalDiskStorage().saveTpsRule("test", ruleContent);
        controlManagerCenter.getTpsControlManager().applyTpsRule("test", tpsControlRule);
        TpsControlRule testTpsControlRule = controlManagerCenter.getTpsControlManager().getRules().get("test");
        
        Assert.assertEquals(100, testTpsControlRule.getPointRule().getMaxCount());
        Assert.assertEquals("test", testTpsControlRule.getPointRule().getRuleName());
        
        TpsControlRule tpsControlRule2 = new TpsControlRule();
        tpsControlRule2.setPointName("test");
        RuleDetail ruleDetail2 = new RuleDetail();
        ruleDetail2.setMaxCount(200);
        ruleDetail2.setRuleName("test2");
        ruleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail2.setPeriod(TimeUnit.SECONDS);
        tpsControlRule2.setPointRule(ruleDetail2);
        String ruleContent2 = JacksonUtils.toJson(tpsControlRule2);
        controlManagerCenter.getRuleStorageProxy().getLocalDiskStorage().saveTpsRule("test", ruleContent2);
        controlManagerCenter.reloadTpsControlRule("test", false);
        
        //wait event
        TimeUnit.SECONDS.sleep(1);
        TpsControlRule testTpsControlRule2 = controlManagerCenter.getTpsControlManager().getRules().get("test");
        Assert.assertEquals(200, testTpsControlRule2.getPointRule().getMaxCount());
        Assert.assertEquals("test2", testTpsControlRule2.getPointRule().getRuleName());
    }
    
    @Test
    public void testReloadTpsControlRuleExternal() throws Exception {
        String localRuleStorageBaseDir =
                EnvUtils.getNacosHome() + File.separator + "tmpTps" + File.separator + "tpsExternal" + File.separator;
        ControlConfigs.getInstance().setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        ControlConfigs.getInstance().setRuleExternalStorage("test");
        resetRuleStorageProxy();
        final ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        
        TpsControlRule tpsControlRule = new TpsControlRule();
        tpsControlRule.setPointName("test");
        RuleDetail ruleDetail = new RuleDetail();
        ruleDetail.setMaxCount(100);
        ruleDetail.setRuleName("test");
        ruleDetail.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail.setPeriod(TimeUnit.SECONDS);
        tpsControlRule.setPointRule(ruleDetail);
        String ruleContent = JacksonUtils.toJson(tpsControlRule);
        controlManagerCenter.getRuleStorageProxy().getExternalStorage().saveTpsRule("test", ruleContent);
        controlManagerCenter.getTpsControlManager().applyTpsRule("test", tpsControlRule);
        TpsControlRule testTpsControlRule = controlManagerCenter.getTpsControlManager().getRules().get("test");
        
        Assert.assertEquals(100, testTpsControlRule.getPointRule().getMaxCount());
        Assert.assertEquals("test", testTpsControlRule.getPointRule().getRuleName());
        
        TpsControlRule tpsControlRule2 = new TpsControlRule();
        tpsControlRule2.setPointName("test");
        RuleDetail ruleDetail2 = new RuleDetail();
        ruleDetail2.setMaxCount(200);
        ruleDetail2.setRuleName("test2");
        ruleDetail2.setMonitorType(MonitorType.INTERCEPT.getType());
        ruleDetail2.setPeriod(TimeUnit.SECONDS);
        tpsControlRule2.setPointRule(ruleDetail2);
        String ruleContent2 = JacksonUtils.toJson(tpsControlRule2);
        controlManagerCenter.getRuleStorageProxy().getExternalStorage().saveTpsRule("test", ruleContent2);
        controlManagerCenter.reloadTpsControlRule("test", true);
        
        //wait event
        TimeUnit.SECONDS.sleep(1);
        TpsControlRule testTpsControlRule2 = controlManagerCenter.getTpsControlManager().getRules().get("test");
        Assert.assertEquals(200, testTpsControlRule2.getPointRule().getMaxCount());
        Assert.assertEquals("test2", testTpsControlRule2.getPointRule().getRuleName());
    }
    
    @Test
    public void testReloadConnectionControlRule() throws Exception {
        String localRuleStorageBaseDir =
                EnvUtils.getNacosHome() + File.separator + "tmpConnection" + File.separator + "connection"
                        + File.separator;
        ControlConfigs.getInstance().setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        resetRuleStorageProxy();
        ConnectionControlRule connectionLimitRule = new ConnectionControlRule();
        connectionLimitRule.setCountLimit(100);
        String ruleContent = JacksonUtils.toJson(connectionLimitRule);
        
        ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        controlManagerCenter.getRuleStorageProxy().getLocalDiskStorage().saveConnectionRule(ruleContent);
        ConnectionControlManager connectionControlManager = controlManagerCenter.getConnectionControlManager();
        //apply rule
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        ConnectionControlRule connectionLimitRule1 = connectionControlManager.getConnectionLimitRule();
        Assert.assertEquals(100, connectionLimitRule1.getCountLimit());
        
        ConnectionControlRule connectionLimitRule2 = new ConnectionControlRule();
        connectionLimitRule2.setCountLimit(200);
        String ruleContent2 = JacksonUtils.toJson(connectionLimitRule2);
        controlManagerCenter.getRuleStorageProxy().getLocalDiskStorage().saveConnectionRule(ruleContent2);
        //reload new rule
        controlManagerCenter.reloadConnectionControlRule(false);
        
        //wait event
        TimeUnit.SECONDS.sleep(1);
        ConnectionControlRule connectionLimitRule3 = connectionControlManager.getConnectionLimitRule();
        Assert.assertEquals(200, connectionLimitRule3.getCountLimit());
    }
    
    @Test
    public void testReloadConnectionControlRuleExternal() throws Exception {
        String localRuleStorageBaseDir =
                EnvUtils.getNacosHome() + File.separator + "tmpConnection" + File.separator + "connectionExternal"
                        + File.separator;
        ControlConfigs.getInstance().setLocalRuleStorageBaseDir(localRuleStorageBaseDir);
        ControlConfigs.getInstance().setRuleExternalStorage("test");
        resetRuleStorageProxy();
        ConnectionControlRule connectionLimitRule = new ConnectionControlRule();
        connectionLimitRule.setCountLimit(100);
        String ruleContent = JacksonUtils.toJson(connectionLimitRule);
        
        ControlManagerCenter controlManagerCenter = ControlManagerCenter.getInstance();
        controlManagerCenter.getRuleStorageProxy().getExternalStorage().saveConnectionRule(ruleContent);
        ConnectionControlManager connectionControlManager = controlManagerCenter.getConnectionControlManager();
        //apply rule
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        ConnectionControlRule connectionLimitRule1 = connectionControlManager.getConnectionLimitRule();
        Assert.assertEquals(100, connectionLimitRule1.getCountLimit());
        
        ConnectionControlRule connectionLimitRule2 = new ConnectionControlRule();
        connectionLimitRule2.setCountLimit(200);
        String ruleContent2 = JacksonUtils.toJson(connectionLimitRule2);
        controlManagerCenter.getRuleStorageProxy().getExternalStorage().saveConnectionRule(ruleContent2);
        //reload new rule
        controlManagerCenter.reloadConnectionControlRule(true);
        
        //wait event
        TimeUnit.SECONDS.sleep(1);
        ConnectionControlRule connectionLimitRule3 = connectionControlManager.getConnectionLimitRule();
        Assert.assertEquals(200, connectionLimitRule3.getCountLimit());
    }
}
