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

import java.util.Map;

/**
 * Gray rule. type with version determined parse logic.
 *
 * @author shiyiyue
 */
public abstract class AbstractGrayRule implements GrayRule {
    
    protected String rawGrayRuleExp;
    
    protected int priority;
    
    protected volatile boolean valid = true;
    
    public AbstractGrayRule() {
    }
    
    public AbstractGrayRule(String rawGrayRuleExp, int priority) {
        try {
            parse(rawGrayRuleExp);
            this.priority = priority;
        } catch (NacosException e) {
            valid = false;
        }
        this.rawGrayRuleExp = rawGrayRuleExp;
    }
    
    /**
     * parse gray rule.
     *
     * @param rawGrayRule raw gray rule.
     * @throws NacosException if parse failed.
     * @date 2024/3/14
     */
    protected abstract void parse(String rawGrayRule) throws NacosException;
    
    /**
     * match gray rule.
     *
     * @param labels conn labels.
     * @return true if match.
     * @date 2024/3/14
     */
    public abstract boolean match(Map<String, String> labels);
    
    public boolean isValid() {
        return valid;
    }
    
    /**
     * get type.
     *
     * @return gray rule type.
     * @date 2024/3/14
     */
    public abstract String getType();
    
    /**
     * get version.
     *
     * @return gray rule version.
     * @date 2024/3/14
     */
    public abstract String getVersion();
    
    public String getRawGrayRuleExp() {
        return rawGrayRuleExp;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
}
