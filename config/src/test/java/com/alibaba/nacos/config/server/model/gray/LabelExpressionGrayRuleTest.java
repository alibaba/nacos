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

package com.alibaba.nacos.config.server.model.gray;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelExpressionGrayRuleTest {
    
    @Test
    void matchShouldReturnTrueForSingleLabelExpression() {
        GrayRule rule = new LabelExpressionGrayRule("region == 'hz'", 100);
        
        assertTrue(rule.isValid());
        assertTrue(rule.match(Map.of("region", "hz")));
        assertFalse(rule.match(Map.of("region", "sh")));
    }
    
    @Test
    void matchShouldRequireAllConditionsForAndExpression() {
        GrayRule rule = new LabelExpressionGrayRule("env == 'prod' && appVersion == '2.3.1'", 100);
        
        assertTrue(rule.isValid());
        assertTrue(rule.match(Map.of("env", "prod", "appVersion", "2.3.1")));
        assertFalse(rule.match(Map.of("env", "prod", "appVersion", "2.3.0")));
        assertFalse(rule.match(Map.of("env", "test", "appVersion", "2.3.1")));
    }
    
    @Test
    void matchShouldReturnTrueWhenAnyConditionMatchesForOrExpression() {
        GrayRule rule = new LabelExpressionGrayRule("cluster == 'canary' || tenant == 'vip'", 100);
        
        assertTrue(rule.isValid());
        assertTrue(rule.match(Map.of("cluster", "canary", "tenant", "normal")));
        assertTrue(rule.match(Map.of("cluster", "default", "tenant", "vip")));
        assertFalse(rule.match(Map.of("cluster", "default", "tenant", "normal")));
    }
    
    @Test
    void matchShouldReturnFalseWhenExpectedLabelDoesNotExist() {
        GrayRule rule = new LabelExpressionGrayRule("region == 'hz'", 100);
        
        assertTrue(rule.isValid());
        assertFalse(rule.match(Map.of("env", "prod")));
        assertFalse(rule.match(Map.of()));
    }
    
    @Test
    void invalidExpressionShouldMarkRuleAsInvalid() {
        GrayRule rule = new LabelExpressionGrayRule("env = 'prod' &&", 100);
        
        assertFalse(rule.isValid());
        assertFalse(rule.match(Map.of("env", "prod")));
    }
    
    @Test
    void shouldExposeExpectedTypeVersionAndPriority() {
        GrayRule rule = new LabelExpressionGrayRule("region == 'hz'", 123);
        
        assertEquals("label_expr", rule.getType());
        assertEquals("1.0.0", rule.getVersion());
        assertEquals(123, rule.getPriority());
        assertEquals("region == 'hz'", rule.getRawGrayRuleExp());
    }
}
