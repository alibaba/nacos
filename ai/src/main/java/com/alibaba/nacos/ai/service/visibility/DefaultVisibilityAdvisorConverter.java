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

package com.alibaba.nacos.ai.service.visibility;

import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default converter from advisor to table query condition.
 *
 * @author nacos
 */
public class DefaultVisibilityAdvisorConverter implements VisibilityAdvisorConverter {
    
    @Override
    public QueryCondition convert(QueryCondition condition, String identity, QueryAdvisor advisor,
        VisibilityQueryContext context) {
        QueryCondition result = condition == null ? new QueryCondition() : condition;
        result.setOrGroup(new LinkedHashMap<>());
        if (advisor == null || advisor.getBasePredicate() == null) {
            return result;
        }
        BaseVisibilityPredicate base = advisor.getBasePredicate();
        switch (base) {
            case ALL:
                break;
            case PUBLIC:
                applyPublic(result);
                break;
            case OWNER:
                applyOwner(result, identity);
                break;
            case PUBLIC_AND_OWNER:
            default:
                applyPublicAndOwner(result, identity);
                break;
        }
        // TODO: stage-2 authorized resources integration.
        List<String> authorized =
            advisor.getAuthorizedPredicate() == null ? null : advisor.getAuthorizedPredicate()
                .getResources();
        if (authorized != null && !authorized.isEmpty()) {
            result.setAuthorizedResourceNames(authorized);
            result.putOrGroup("name", authorized);
        }
        simplifyOrGroup(result);
        return result;
    }
    
    private void applyPublic(QueryCondition condition) {
        if (StringUtils.isBlank(condition.getScope())) {
            condition.setScope(VisibilityConstants.SCOPE_PUBLIC);
            return;
        }
        if (!VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(condition.getScope())) {
            condition.setAlwaysEmpty(true);
        }
    }
    
    private void applyOwner(QueryCondition condition, String identity) {
        if (StringUtils.isBlank(identity)) {
            condition.setAlwaysEmpty(true);
            return;
        }
        if (StringUtils.isBlank(condition.getOwner())) {
            condition.setOwner(identity);
            return;
        }
        if (!identity.equals(condition.getOwner())) {
            condition.setAlwaysEmpty(true);
        }
    }
    
    private void applyPublicAndOwner(QueryCondition condition, String identity) {
        if (StringUtils.isBlank(identity)) {
            applyPublic(condition);
            return;
        }
        boolean scopeIsPublic =
            VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(condition.getScope());
        boolean hasScope = StringUtils.isNotBlank(condition.getScope());
        boolean ownerIsIdentity = identity.equals(condition.getOwner());
        boolean hasOwner = StringUtils.isNotBlank(condition.getOwner());
        if (scopeIsPublic || ownerIsIdentity) {
            return;
        }
        // this condition means scope != public and owner != identity.
        // it conflicts with visibility `public or owner is identity`, so it must be always empty
        if (hasScope && hasOwner) {
            condition.setAlwaysEmpty(true);
            return;
        }
        if (!hasScope) {
            condition.putOrGroup("scope", VisibilityConstants.SCOPE_PUBLIC);
        }
        if (!hasOwner) {
            condition.putOrGroup("owner", identity);
        }
    }
    
    private void simplifyOrGroup(QueryCondition condition) {
        Map<String, Object> orGroup = condition.getOrGroup();
        if (orGroup == null || orGroup.isEmpty()) {
            return;
        }
        if (orGroup.size() != 1) {
            return;
        }
        Map.Entry<String, Object> only = orGroup.entrySet().iterator().next();
        String key = only.getKey();
        Object value = only.getValue();
        if ("scope".equals(key) && StringUtils.isBlank(condition.getScope())) {
            condition.setScope(String.valueOf(value));
            condition.setOrGroup(new LinkedHashMap<>());
            return;
        }
        if ("owner".equals(key) && StringUtils.isBlank(condition.getOwner())) {
            condition.setOwner(String.valueOf(value));
            condition.setOrGroup(new LinkedHashMap<>());
        }
    }
}
