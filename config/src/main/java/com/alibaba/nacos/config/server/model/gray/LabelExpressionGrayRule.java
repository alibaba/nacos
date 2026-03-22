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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LabelExpressionGrayRule extends AbstractGrayRule {
    
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "\\s*([A-Za-z0-9_.-]+)\\s*==\\s*'([^']*)'\\s*");
    
    public static final String TYPE_LABEL_EXPR = "label_expr";
    
    public static final String VERSION = "1.0.0";
    
    // Outer list is OR groups; inner list is AND conditions.
    // 外层表示 OR 分组，内层表示 AND 条件。
    private List<List<Condition>> orGroups;
    
    public LabelExpressionGrayRule() {
        super();
    }
    
    public LabelExpressionGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        // Reject blank expressions early.
        // 空表达式直接判为非法。
        if (StringUtils.isBlank(rawGrayRule)) {
            throw new NacosException(NacosException.INVALID_PARAM, "Label expression can not be blank.");
        }
        
        List<List<Condition>> parsedGroups = new ArrayList<>();
        // Split by OR first.
        // 先按 OR 拆分。
        String[] orParts = rawGrayRule.split("\\|\\|");
        for (String orPart : orParts) {
            List<Condition> andConditions = new ArrayList<>();
            // Then split each group by AND.
            // 再按 AND 拆分组内条件。
            String[] andParts = orPart.split("&&");
            for (String andPart : andParts) {
                // Parse atomic condition: key == 'value'.
                // 解析原子条件：key == 'value'。
                Matcher matcher = CONDITION_PATTERN.matcher(andPart);
                if (!matcher.matches()) {
                    // Any invalid segment makes the whole rule invalid.
                    // 任一片段非法则整条规则非法。
                    throw new NacosException(NacosException.INVALID_PARAM,
                            "Invalid label expression segment: " + andPart);
                }
                andConditions.add(new Condition(matcher.group(1), matcher.group(2)));
            }
            // Guard against empty groups.
            // 保护空分组场景。
            if (andConditions.isEmpty()) {
                throw new NacosException(NacosException.INVALID_PARAM, "Label expression can not be empty.");
            }
            parsedGroups.add(andConditions);
        }
        // Replace parsed result only after full validation.
        // 全部校验通过后再整体替换结果。
        if (parsedGroups.isEmpty()) {
            throw new NacosException(NacosException.INVALID_PARAM, "Label expression can not be empty.");
        }
        this.orGroups = parsedGroups;
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        if (!isValid() || labels == null || labels.isEmpty()) {
            return false;
        }
        
        // Match succeeds if any OR group matches.
        // 任一 OR 分组命中即可。
        for (List<Condition> andGroup : orGroups) {
            boolean matched = true;
            // All conditions inside one group must pass.
            // 同组内条件必须全部满足。
            for (Condition condition : andGroup) {
                if (!condition.match(labels)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String getType() {
        return TYPE_LABEL_EXPR;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LabelExpressionGrayRule that = (LabelExpressionGrayRule) o;
        return priority == that.priority && Objects.equals(rawGrayRuleExp, that.rawGrayRuleExp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(rawGrayRuleExp, priority);
    }
    
    private static final class Condition {
        
        private final String key;
        
        private final String value;
        
        private Condition(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        private boolean match(Map<String, String> labels) {
            return value.equals(labels.get(key));
        }
    }
}
