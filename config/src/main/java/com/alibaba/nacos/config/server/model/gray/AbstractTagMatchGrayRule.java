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

package com.alibaba.nacos.config.server.model.gray;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * description.
 *
 * @author rong
 * @date 2024-03-13 14:31
 */
public abstract class AbstractTagMatchGrayRule extends AbstractGrayRule {
    
    protected static final String EQUAL_PATTERN = "=";
    
    protected static final String KEY_PATTERN = "[a-zA-Z0-9-_:\\.]+";
    
    protected static final String VALUE_SPLITER_PATTERN = ",";
    
    protected static final String VALUE_PATTERN =
        KEY_PATTERN + "(\\s*" + VALUE_SPLITER_PATTERN + "\\s*" + KEY_PATTERN + ")*";
    
    public AbstractTagMatchGrayRule() {
        super();
    }
    
    public AbstractTagMatchGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    protected void isPatternMatch(String rawString, String pattern) throws NacosException {
        if (!rawString.matches(pattern)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                String.format(
                    "tagv2 gray rule parse failed: " + "raw string [%s] doesn't match pattern[%s].",
                    rawString, pattern));
        }
    }
    
    /**
     * Check whether another tag match gray rule has the same expression, priority, type and version.
     *
     * @param obj another object.
     * @return true if equals.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof AbstractTagMatchGrayRule) {
            AbstractTagMatchGrayRule other = (AbstractTagMatchGrayRule) obj;
            return this.rawGrayRuleExp.equals(other.rawGrayRuleExp)
                && this.priority == other.priority && this.getType()
                    .equals(other.getType())
                && this.getVersion().equals(other.getVersion());
        }
        return false;
    }
}
