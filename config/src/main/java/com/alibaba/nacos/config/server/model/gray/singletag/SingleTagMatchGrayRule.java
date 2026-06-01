/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.gray.AbstractTagMatchGrayRule;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.api.common.Constants.TAG_V2;

/**
 * Single tag match gray rule.单标签匹配规则.
 *
 * @author shiyiyue
 */
public class SingleTagMatchGrayRule extends AbstractTagMatchGrayRule {
    
    private String tagKey;
    
    private Set<String> tagValueSet;
    
    private static final int KEY_VALUE_ARRAY_LENGTH = 2;
    
    private static final String EXPRESSION_PATTERN =
        "^\\s*" + KEY_PATTERN + "\\s*" + EQUAL_PATTERN + "\\s*" + VALUE_PATTERN + "\\s*$";
    
    public SingleTagMatchGrayRule() {
        super();
    }
    
    public SingleTagMatchGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    /**
     * parse rule, accept key=value1,value2,value3.
     *
     * @param rawRule raw rule
     */
    @Override
    protected void parse(String rawRule) throws NacosException {
        this.isPatternMatch(rawRule, EXPRESSION_PATTERN);
        String[] keyValueArray = rawRule.trim().split(EQUAL_PATTERN);
        if (keyValueArray.length != KEY_VALUE_ARRAY_LENGTH) {
            throw new NacosException(NacosException.INVALID_PARAM, String.format(
                "gray rule parse failed: raw rule[%s] doesn't match pattern[%s].",
                rawRule, KEY_PATTERN + EQUAL_PATTERN + VALUE_PATTERN));
        }
        isPatternMatch(keyValueArray[0].trim(), KEY_PATTERN);
        isPatternMatch(keyValueArray[1].trim(), VALUE_PATTERN);
        this.tagKey = keyValueArray[0].trim();
        this.tagValueSet = Arrays.stream(keyValueArray[1].trim().split(VALUE_SPLITER_PATTERN))
            .map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        if (labels.containsKey(tagKey) && tagValueSet.contains(labels.get(tagKey))) {
            return true;
        }
        return false;
    }
    
    public static final String TYPE_TAGV2 = TAG_V2;
    
    public static final String VERSION_1_0_0 = "1.0.0";
    
    @Override
    public String getType() {
        return TYPE_TAGV2;
    }
    
    @Override
    public String getVersion() {
        return VERSION_1_0_0;
    }
}
