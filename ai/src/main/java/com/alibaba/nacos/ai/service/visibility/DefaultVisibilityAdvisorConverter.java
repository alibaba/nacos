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
 * <p>Let {@code F} be the business filters already present on the incoming {@link QueryCondition},
 * {@code B} be the {@link BaseVisibilityPredicate}, and {@code G} be {@code name IN authorizedResources}.
 * The query this converter must produce is {@code F AND (B OR G)}.
 *
 * <p>To do that correctly, {@code B} is first resolved into a self-contained {@link BaseResolution}
 * (always true / always false / a set of OR branches) without touching the condition. Only afterwards
 * is that resolution unioned with {@code G} and, only then, simplified into a concrete
 * {@link QueryCondition} shape (a hard field, an OR group, or {@code alwaysEmpty}). This ordering
 * matters: if {@code B} were collapsed into the condition before {@code G} is known, the union with
 * {@code G} could silently turn into an AND, or an impossible {@code B} could mark the whole query
 * {@code alwaysEmpty} even though {@code F AND G} could still match.
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
        BaseResolution resolution = resolveBase(base, result, identity);
        
        List<String> authorized =
            advisor.getAuthorizedPredicate() == null ? null : advisor.getAuthorizedPredicate()
                .getResources();
        boolean hasAuthorized = authorized != null && !authorized.isEmpty();
        if (hasAuthorized) {
            result.setAuthorizedResourceNames(authorized);
        }
        applyResolution(result, resolution, hasAuthorized ? authorized : null);
        return result;
    }
    
    private BaseResolution resolveBase(BaseVisibilityPredicate base, QueryCondition condition,
        String identity) {
        switch (base) {
            case ALL:
                return BaseResolution.alwaysTrue();
            case PUBLIC:
                return resolvePublic(condition);
            case OWNER:
                return resolveOwner(condition, identity);
            case PUBLIC_AND_OWNER:
            default:
                return resolvePublicAndOwner(condition, identity);
        }
    }
    
    private BaseResolution resolvePublic(QueryCondition condition) {
        if (StringUtils.isBlank(condition.getScope())) {
            return BaseResolution.branches(singleBranch("scope", VisibilityConstants.SCOPE_PUBLIC));
        }
        if (VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(condition.getScope())) {
            return BaseResolution.alwaysTrue();
        }
        return BaseResolution.alwaysFalse();
    }
    
    private BaseResolution resolveOwner(QueryCondition condition, String identity) {
        if (StringUtils.isBlank(identity)) {
            // Anonymous callers are not part of the supported authorization model: an
            // authorized-resource grant must never rescue a missing identity, so this is
            // forced empty rather than merely "always false" (which G could rescue).
            return BaseResolution.forcedEmpty();
        }
        if (StringUtils.isBlank(condition.getOwner())) {
            return BaseResolution.branches(singleBranch("owner", identity));
        }
        if (identity.equals(condition.getOwner())) {
            return BaseResolution.alwaysTrue();
        }
        return BaseResolution.alwaysFalse();
    }
    
    private BaseResolution resolvePublicAndOwner(QueryCondition condition, String identity) {
        if (StringUtils.isBlank(identity)) {
            return resolvePublic(condition);
        }
        boolean scopeIsPublic = VisibilityConstants.SCOPE_PUBLIC.equalsIgnoreCase(condition.getScope());
        boolean hasScope = StringUtils.isNotBlank(condition.getScope());
        boolean ownerIsIdentity = identity.equals(condition.getOwner());
        boolean hasOwner = StringUtils.isNotBlank(condition.getOwner());
        if (scopeIsPublic || ownerIsIdentity) {
            return BaseResolution.alwaysTrue();
        }
        // this condition means scope != public and owner != identity.
        // it conflicts with visibility `public or owner is identity`, so this branch of B is
        // impossible -- but that no longer means the whole query is empty, since G may still match.
        if (hasScope && hasOwner) {
            return BaseResolution.alwaysFalse();
        }
        Map<String, Object> branches = new LinkedHashMap<>();
        if (!hasScope) {
            branches.put("scope", VisibilityConstants.SCOPE_PUBLIC);
        }
        if (!hasOwner) {
            branches.put("owner", identity);
        }
        return BaseResolution.branches(branches);
    }
    
    private Map<String, Object> singleBranch(String field, Object value) {
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put(field, value);
        return branch;
    }
    
    /**
     * Union the resolved base predicate {@code B} with {@code G} (authorized resource names,
     * or {@code null}/empty when there are none) and collapse the result into the condition.
     */
    private void applyResolution(QueryCondition condition, BaseResolution resolution,
        List<String> authorized) {
        switch (resolution.getKind()) {
            case ALWAYS_TRUE:
                // B alone already guarantees a match. B OR G is still always true, so G cannot
                // narrow it any further -- nothing else needs to be applied.
                return;
            case FORCED_EMPTY:
                condition.setAlwaysEmpty(true);
                return;
            case ALWAYS_FALSE:
                // B alone can never match. B OR G collapses to G: keep G if it exists, otherwise
                // the whole predicate is empty.
                if (authorized != null) {
                    condition.putOrGroup("name", authorized);
                } else {
                    condition.setAlwaysEmpty(true);
                }
                return;
            case BRANCHES:
            default:
                Map<String, Object> combined = new LinkedHashMap<>(resolution.getBranches());
                if (authorized != null) {
                    combined.put("name", authorized);
                }
                condition.setOrGroup(combined);
                simplifyOrGroup(condition);
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
    
    /**
     * Self-contained resolution of the base visibility predicate {@code B}, computed independently
     * of {@code G} so the two can be unioned correctly before any simplification happens.
     */
    private static final class BaseResolution {
        
        private enum Kind {
            ALWAYS_TRUE,
            ALWAYS_FALSE,
            FORCED_EMPTY,
            BRANCHES
        }
        
        private final Kind kind;
        
        private final Map<String, Object> branches;
        
        private BaseResolution(Kind kind, Map<String, Object> branches) {
            this.kind = kind;
            this.branches = branches;
        }
        
        static BaseResolution alwaysTrue() {
            return new BaseResolution(Kind.ALWAYS_TRUE, null);
        }
        
        static BaseResolution alwaysFalse() {
            return new BaseResolution(Kind.ALWAYS_FALSE, null);
        }
        
        static BaseResolution forcedEmpty() {
            return new BaseResolution(Kind.FORCED_EMPTY, null);
        }
        
        static BaseResolution branches(Map<String, Object> branches) {
            return new BaseResolution(Kind.BRANCHES, branches);
        }
        
        Kind getKind() {
            return kind;
        }
        
        Map<String, Object> getBranches() {
            return branches;
        }
    }
}
