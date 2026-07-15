/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.tps;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.rule.parser.NacosTpsControlRuleParser;
import com.alibaba.nacos.plugin.control.rule.storage.ExternalRuleStorage;
import com.alibaba.nacos.plugin.control.rule.storage.LocalDiskRuleStorage;
import com.alibaba.nacos.plugin.control.rule.storage.RuleStorageProxy;
import com.alibaba.nacos.plugin.control.tps.barrier.DefaultNacosTpsBarrier;
import com.alibaba.nacos.plugin.control.tps.barrier.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.barrier.creator.DefaultNacosTpsBarrierCreator;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.rule.RuleDetail;
import com.alibaba.nacos.plugin.control.tps.rule.TpsControlRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TpsControlManagerTest {
    
    @TempDir
    private Path tempDir;
    
    private ExternalRuleStorage originalExternalStorage;
    
    @BeforeEach
    void setUp() throws Exception {
        originalExternalStorage = getExternalRuleStorage();
        ((LocalDiskRuleStorage) RuleStorageProxy.getInstance().getLocalDiskStorage())
            .setLocalRuleBaseDir(tempDir.toString());
    }
    
    @AfterEach
    void tearDown() throws Exception {
        setExternalRuleStorage(originalExternalStorage);
    }
    
    @Test
    void testDefaultParserAndBarrierCreator() {
        TestTpsControlManager manager = new TestTpsControlManager();
        
        assertInstanceOf(NacosTpsControlRuleParser.class, manager.getTpsControlRuleParser());
        assertEquals("nacos", manager.tpsBarrierCreator.getName());
        assertInstanceOf(DefaultNacosTpsBarrier.class,
            manager.tpsBarrierCreator.createTpsBarrier("point"));
        
        DefaultNacosTpsBarrierCreator creator = new DefaultNacosTpsBarrierCreator();
        assertEquals("nacos", creator.getName());
        assertInstanceOf(DefaultNacosTpsBarrier.class, creator.createTpsBarrier("point"));
    }
    
    @Test
    void testInitTpsRuleLoadsLocalRule() throws Exception {
        TpsControlRule rule = createRule("localPoint", 3L);
        RuleStorageProxy.getInstance().getLocalDiskStorage()
            .saveTpsRule("localPoint", JacksonUtils.toJson(rule));
        setExternalRuleStorage(new MemoryExternalRuleStorage(createRule("localPoint", 9L)));
        
        TestTpsControlManager manager = new TestTpsControlManager();
        manager.initRule("localPoint");
        
        assertEquals(3L, manager.appliedRules.get("localPoint").getPointRule().getMaxCount());
    }
    
    @Test
    void testInitTpsRuleLoadsExternalRuleWhenLocalMissing() throws Exception {
        setExternalRuleStorage(new MemoryExternalRuleStorage(createRule("externalPoint", 7L)));
        
        TestTpsControlManager manager = new TestTpsControlManager();
        manager.initRule("externalPoint");
        
        assertEquals(7L, manager.appliedRules.get("externalPoint").getPointRule().getMaxCount());
    }
    
    @Test
    void testInitTpsRuleSkipsBlankRule() throws Exception {
        setExternalRuleStorage(new MemoryExternalRuleStorage(null));
        
        TestTpsControlManager manager = new TestTpsControlManager();
        manager.initRule("missingPoint");
        
        assertNull(manager.appliedRules.get("missingPoint"));
    }
    
    private TpsControlRule createRule(String pointName, long maxCount) {
        TpsControlRule result = new TpsControlRule();
        result.setPointName(pointName);
        RuleDetail detail = new RuleDetail();
        detail.setRuleName(pointName + "Rule");
        detail.setMaxCount(maxCount);
        detail.setMonitorType(MonitorType.INTERCEPT.getType());
        result.setPointRule(detail);
        return result;
    }
    
    private ExternalRuleStorage getExternalRuleStorage() throws Exception {
        Field field = RuleStorageProxy.class.getDeclaredField("externalRuleStorage");
        field.setAccessible(true);
        return (ExternalRuleStorage) field.get(RuleStorageProxy.getInstance());
    }
    
    private void setExternalRuleStorage(ExternalRuleStorage storage) throws Exception {
        Field field = RuleStorageProxy.class.getDeclaredField("externalRuleStorage");
        field.setAccessible(true);
        field.set(RuleStorageProxy.getInstance(), storage);
    }
    
    private static class TestTpsControlManager extends TpsControlManager {
        
        private final Map<String, TpsControlRule> appliedRules = new LinkedHashMap<>();
        
        void initRule(String pointName) {
            initTpsRule(pointName);
        }
        
        @Override
        public void registerTpsPoint(String pointName) {
        }
        
        @Override
        public Map<String, TpsBarrier> getPoints() {
            return new LinkedHashMap<>();
        }
        
        @Override
        public Map<String, TpsControlRule> getRules() {
            return appliedRules;
        }
        
        @Override
        public void applyTpsRule(String pointName, TpsControlRule rule) {
            appliedRules.put(pointName, rule);
        }
        
        @Override
        public TpsCheckResponse check(TpsCheckRequest tpsRequest) {
            return null;
        }
        
        @Override
        public String getName() {
            return "test";
        }
    }
    
    private static class MemoryExternalRuleStorage implements ExternalRuleStorage {
        
        private final TpsControlRule tpsControlRule;
        
        MemoryExternalRuleStorage(TpsControlRule tpsControlRule) {
            this.tpsControlRule = tpsControlRule;
        }
        
        @Override
        public String getName() {
            return "memory";
        }
        
        @Override
        public void saveConnectionRule(String ruleContent) throws IOException {
        }
        
        @Override
        public String getConnectionRule() {
            return null;
        }
        
        @Override
        public void saveTpsRule(String pointName, String ruleContent) throws IOException {
        }
        
        @Override
        public String getTpsRule(String pointName) {
            return tpsControlRule == null ? null : JacksonUtils.toJson(tpsControlRule);
        }
    }
}
