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

package com.alibaba.nacos.config.server.model.gray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Composite gray rule for multiple conditions.
 * @author AI Assistant
 */
public class CompositeGrayRule extends AbstractGrayRule {
    
    public static final String TYPE_COMPOSITE = "composite";
    
    public static final String VERSION = "1.0.0";
    
    public static final int PRIORITY = Integer.MAX_VALUE - 3;
    
    private List<GrayRule> rules = new ArrayList<>();
    
    public CompositeGrayRule() {
        super();
    }
    
    public CompositeGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        if (StringUtils.isBlank(rawGrayRule)) {
            return;
        }
        
        // Parse composite rule in JSON format
        // Example: {"rules": [{"type": "tenant", "value": "tenant1"}, {"type": "percentage", "value": "50"}]}
        // For simplicity, we'll use a simplified format for now
        // Format: type1:value1;type2:value2;...
        String[] ruleParts = rawGrayRule.split(";");
        for (String part : ruleParts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            
            String type = kv[0].trim();
            String value = kv[1].trim();
            
            GrayRule rule = createRule(type, value);
            if (rule != null) {
                rules.add(rule);
            }
        }
    }
    
    private GrayRule createRule(String type, String value) throws NacosException {
        switch (type) {
            case "tenant":
                return new TenantGrayRule(value, PRIORITY);
            case "namespace":
                return new NamespaceGrayRule(value, PRIORITY);
            case "percentage":
                return new PercentageGrayRule(value, PRIORITY);
            case "tag":
                return new TagGrayRule(value, PRIORITY);
            case "beta":
                return new BetaGrayRule(value, PRIORITY);
            default:
                return null;
        }
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        for (GrayRule rule : rules) {
            if (!rule.match(labels)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String getType() {
        return TYPE_COMPOSITE;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompositeGrayRule that = (CompositeGrayRule) o;
        return Objects.equals(rules, that.rules);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(rules);
    }
}
