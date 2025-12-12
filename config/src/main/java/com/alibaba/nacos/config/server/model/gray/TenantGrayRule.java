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
 * Tenant gray rule for tenant filter.
 * @author AI Assistant
 */
public class TenantGrayRule extends AbstractGrayRule {
    
    private static final String TENANT_LABEL = "Tenant";
    
    public static final String TYPE_TENANT = "tenant";
    
    public static final String VERSION = "1.0.0";
    
    public static final int PRIORITY = Integer.MAX_VALUE - 4;
    
    private String tenantValue;
    
    public TenantGrayRule() {
        super();
    }
    
    public TenantGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        if (StringUtils.isBlank(rawGrayRule)) {
            return;
        }
        this.tenantValue = rawGrayRule;
    }
    
    @Override
    public boolean match(Map<String, String> labels) {
        return labels.containsKey(TENANT_LABEL) && tenantValue.equals(labels.get(TENANT_LABEL));
    }
    
    @Override
    public String getType() {
        return TYPE_TENANT;
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
        TenantGrayRule that = (TenantGrayRule) o;
        return Objects.equals(tenantValue, that.tenantValue);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantValue);
    }
}
