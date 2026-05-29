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

package com.alibaba.nacos.config.server.model.gray.multitag;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTagMatchGrayRuleTest {
    
    @Test
    void matchWithAndExpression() {
        MultiTagMatchGrayRule rule = new MultiTagMatchGrayRule("region=hz&&env=prod", 1);
        Map<String, String> labels = new HashMap<>();
        labels.put("region", "hz");
        labels.put("env", "prod");
        
        assertTrue(rule.isValid());
        assertTrue(rule.match(labels));
    }
    
    @Test
    void matchWithOrExpressionAndMultiValues() {
        MultiTagMatchGrayRule rule =
            new MultiTagMatchGrayRule("region=hz&&env=prod,pre||tenant=vip", 1);
        Map<String, String> labels = new HashMap<>();
        labels.put("tenant", "vip");
        
        assertTrue(rule.match(labels));
        
        labels.clear();
        labels.put("region", "hz");
        labels.put("env", "pre");
        assertTrue(rule.match(labels));
    }
    
    @Test
    void notMatchWhenAndExpressionMissesOneLabel() {
        MultiTagMatchGrayRule rule = new MultiTagMatchGrayRule("region=hz&&env=prod", 1);
        Map<String, String> labels = new HashMap<>();
        labels.put("region", "hz");
        
        assertFalse(rule.match(labels));
    }
    
    @Test
    void invalidWhenSameKeyAppearsInOneAndExpression() {
        MultiTagMatchGrayRule rule = new MultiTagMatchGrayRule("region=hz&&region=sh", 1);
        
        assertFalse(rule.isValid());
    }
    
    @Test
    void equalRuleItemsHaveSameHashCode() {
        MultiTagMatchGrayRule.TagV2GrayRuleItem item =
            new MultiTagMatchGrayRule.TagV2GrayRuleItem("region",
                new HashSet<>(Arrays.asList("hz", "sh")));
        MultiTagMatchGrayRule.TagV2GrayRuleItem sameItem =
            new MultiTagMatchGrayRule.TagV2GrayRuleItem("region",
                new HashSet<>(Arrays.asList("sh", "hz")));
        
        assertEquals(item, sameItem);
        assertEquals(item.hashCode(), sameItem.hashCode());
    }
}
