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

import java.util.Map;
import java.util.Objects;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Percentage gray rule for percentage bucket.
 * @author AI Assistant
 */
public class PercentageGrayRule extends AbstractGrayRule {
    
    private static final String CLIENT_IP_LABEL = "ClientIp";
    
    public static final String TYPE_PERCENTAGE = "percentage";
    
    public static final String VERSION = "1.0.0";
    
    public static final int PRIORITY = Integer.MAX_VALUE - 2;
    
    private int percentage;
    
    public PercentageGrayRule() {
        super();
    }
    
    public PercentageGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        if (StringUtils.isBlank(rawGrayRule)) {
            return;
        }
        try {
            this.percentage = Integer.parseInt(rawGrayRule);
            if (this.percentage < 0 || this.percentage > 100) {
                throw new NacosException(NacosException.INVALID_PARAM, "Percentage must be between 0 and 100");
            }
        } catch (NumberFormatException e) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid percentage format: " + rawGrayRule);
        }
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        if (!labels.containsKey(CLIENT_IP_LABEL)) {
            return false;
        }
        
        String clientIp = labels.get(CLIENT_IP_LABEL);
        int hash = Math.abs(clientIp.hashCode());
        int bucket = hash % 100;
        
        return bucket < this.percentage;
    }
    
    @Override
    public String getType() {
        return TYPE_PERCENTAGE;
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
        PercentageGrayRule that = (PercentageGrayRule) o;
        return percentage == that.percentage;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(percentage);
    }
}
