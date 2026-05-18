/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.gray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrayRuleManagerTest {
    
    @Test
    void testConstructGrayRuleForBeta() {
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo(
            BetaGrayRule.TYPE_BETA, BetaGrayRule.VERSION,
            "1.1.1.1,2.2.2.2", BetaGrayRule.PRIORITY);
        GrayRule rule = GrayRuleManager.constructGrayRule(info);
        assertNotNull(rule);
        assertTrue(rule instanceof BetaGrayRule);
        assertEquals("1.1.1.1,2.2.2.2", rule.getRawGrayRuleExp());
        assertEquals(BetaGrayRule.PRIORITY, rule.getPriority());
    }
    
    @Test
    void testConstructGrayRuleForTag() {
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo(
            TagGrayRule.TYPE_TAG, TagGrayRule.VERSION,
            "myTag", TagGrayRule.PRIORITY);
        GrayRule rule = GrayRuleManager.constructGrayRule(info);
        assertNotNull(rule);
        assertTrue(rule instanceof TagGrayRule);
        assertEquals("myTag", rule.getRawGrayRuleExp());
    }
    
    @Test
    void testConstructGrayRuleUnknownTypeReturnsNull() {
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo(
            "unknown_type", "v999", "expr", 1);
        GrayRule rule = GrayRuleManager.constructGrayRule(info);
        assertNull(rule);
    }
    
    @Test
    void testConstructConfigGrayPersistInfo() {
        BetaGrayRule betaRule = new BetaGrayRule("1.1.1.1", BetaGrayRule.PRIORITY);
        ConfigGrayPersistInfo info =
            GrayRuleManager.constructConfigGrayPersistInfo(betaRule);
        assertEquals(BetaGrayRule.TYPE_BETA, info.getType());
        assertEquals(BetaGrayRule.VERSION, info.getVersion());
        assertEquals("1.1.1.1", info.getExpr());
        assertEquals(BetaGrayRule.PRIORITY, info.getPriority());
    }
    
    @Test
    void testSerializeAndDeserialize() {
        ConfigGrayPersistInfo original = new ConfigGrayPersistInfo(
            BetaGrayRule.TYPE_BETA, BetaGrayRule.VERSION,
            "10.0.0.1", BetaGrayRule.PRIORITY);
        String json = GrayRuleManager.serializeConfigGrayPersistInfo(original);
        assertNotNull(json);
        assertTrue(json.contains("beta"));
        
        ConfigGrayPersistInfo deserialized =
            GrayRuleManager.deserializeConfigGrayPersistInfo(json);
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.getExpr(), deserialized.getExpr());
        assertEquals(original.getPriority(), deserialized.getPriority());
    }
    
    @Test
    void testGetClassByTypeAndVersion() {
        assertNotNull(GrayRuleManager.getClassByTypeAndVersion(
            BetaGrayRule.TYPE_BETA, BetaGrayRule.VERSION));
        assertNotNull(GrayRuleManager.getClassByTypeAndVersion(
            TagGrayRule.TYPE_TAG, TagGrayRule.VERSION));
        assertNull(GrayRuleManager.getClassByTypeAndVersion(
            "nonexist", "v1"));
    }
}
