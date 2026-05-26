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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.gray.multitag.MultiTagMatchGrayRule;
import com.alibaba.nacos.config.server.model.gray.singletag.SingleTagMatchGrayRule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.TAG_V2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void constructSingleTagV2RuleBySpi() {
        ConfigGrayPersistInfo persistInfo = new ConfigGrayPersistInfo(TAG_V2,
            SingleTagMatchGrayRule.VERSION_1_0_0, "region=hz", 1);
        
        assertInstanceOf(SingleTagMatchGrayRule.class,
            GrayRuleManager.constructGrayRule(persistInfo));
    }
    
    @Test
    void constructMultiTagV2RuleBySpi() {
        ConfigGrayPersistInfo persistInfo = new ConfigGrayPersistInfo(TAG_V2,
            MultiTagMatchGrayRule.VERSION_1_1_0, "region=hz&&env=prod", 1);
        
        assertInstanceOf(MultiTagMatchGrayRule.class,
            GrayRuleManager.constructGrayRule(persistInfo));
    }
    
    @Test
    void testTagGrayRuleBlankExpressionAndEqualsSameInstance() {
        TagGrayRule rule = new TagGrayRule("", TagGrayRule.PRIORITY);
        
        assertEquals(TagGrayRule.TYPE_TAG, rule.getType());
        assertEquals(TagGrayRule.VERSION, rule.getVersion());
        assertEquals(rule, rule);
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
        assertTrue(betaRule.equals(betaRule));
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
    
    @Test
    void testConstructGrayRuleThrowsWhenRegisteredClassHasNoExpectedConstructor()
        throws Exception {
        Map<String, Class<?>> grayRuleMap = getGrayRuleMap();
        String key = BrokenGrayRule.TYPE + GrayRuleManager.SPLIT + BrokenGrayRule.VERSION;
        grayRuleMap.put(key, BrokenGrayRule.class);
        ConfigGrayPersistInfo info = new ConfigGrayPersistInfo(BrokenGrayRule.TYPE,
            BrokenGrayRule.VERSION, "broken", 1);
        try {
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> GrayRuleManager.constructGrayRule(info));
            assertTrue(exception.getMessage().contains(BrokenGrayRule.TYPE));
            assertTrue(exception.getMessage().contains(BrokenGrayRule.VERSION));
        } finally {
            grayRuleMap.remove(key);
        }
    }
    
    @Test
    void testAbstractGrayRuleDefaultConstructor() {
        ValidGrayRule rule = new ValidGrayRule();
        
        assertTrue(rule.isValid());
        assertNull(rule.getRawGrayRuleExp());
        assertEquals(0, rule.getPriority());
    }
    
    @Test
    void testAbstractGrayRuleMarksInvalidWhenParseThrows() {
        InvalidGrayRule rule = new InvalidGrayRule("invalid", 7);
        
        assertFalse(rule.isValid());
        assertEquals("invalid", rule.getRawGrayRuleExp());
        assertEquals(0, rule.getPriority());
        rule.setPriority(9);
        assertEquals(9, rule.getPriority());
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Class<?>> getGrayRuleMap() throws Exception {
        Field field = GrayRuleManager.class.getDeclaredField("GRAY_RULE_MAP");
        field.setAccessible(true);
        return (Map<String, Class<?>>) field.get(null);
    }
    
    private static class BrokenGrayRule extends AbstractGrayRule {
        
        private static final String TYPE = "broken";
        
        private static final String VERSION = "v1";
        
        @Override
        protected void parse(String rawGrayRule) {
        }
        
        @Override
        public boolean match(Map<String, String> labels) {
            return false;
        }
        
        @Override
        public String getType() {
            return TYPE;
        }
        
        @Override
        public String getVersion() {
            return VERSION;
        }
    }
    
    private static class ValidGrayRule extends AbstractGrayRule {
        
        private static final String TYPE = "valid";
        
        private static final String VERSION = "v1";
        
        @Override
        protected void parse(String rawGrayRule) {
        }
        
        @Override
        public boolean match(Map<String, String> labels) {
            return false;
        }
        
        @Override
        public String getType() {
            return TYPE;
        }
        
        @Override
        public String getVersion() {
            return VERSION;
        }
    }
    
    private static class InvalidGrayRule extends AbstractGrayRule {
        
        private static final String TYPE = "invalid";
        
        private static final String VERSION = "v1";
        
        InvalidGrayRule(String rawGrayRuleExp, int priority) {
            super(rawGrayRuleExp, priority);
        }
        
        @Override
        protected void parse(String rawGrayRule) throws NacosException {
            throw new NacosException(NacosException.INVALID_PARAM, "invalid");
        }
        
        @Override
        public boolean match(Map<String, String> labels) {
            return false;
        }
        
        @Override
        public String getType() {
            return TYPE;
        }
        
        @Override
        public String getVersion() {
            return VERSION;
        }
    }
}
