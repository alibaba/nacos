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

package com.alibaba.nacos.config.server.model.gray.singletag;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTagMatchGrayRuleTest {
    
    @Test
    void matchWhenLabelValueInRuleValues() {
        SingleTagMatchGrayRule rule = new SingleTagMatchGrayRule("region=hz,sh", 1);
        Map<String, String> labels = new HashMap<>();
        labels.put("region", "hz");
        
        assertTrue(rule.isValid());
        assertTrue(rule.match(labels));
    }
    
    @Test
    void notMatchWhenLabelMissingOrValueNotInRuleValues() {
        SingleTagMatchGrayRule rule = new SingleTagMatchGrayRule("region=hz,sh", 1);
        Map<String, String> labels = new HashMap<>();
        labels.put("region", "bj");
        
        assertFalse(rule.match(labels));
        assertFalse(rule.match(new HashMap<>()));
    }
    
    @Test
    void invalidWhenExpressionFormatInvalid() {
        SingleTagMatchGrayRule rule = new SingleTagMatchGrayRule("region==hz", 1);
        
        assertFalse(rule.isValid());
    }
}
