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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.AuthorizedResources;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Visibility query advisor for range/list operations.
 *
 * @author xiweng.yy
 */
public class QueryAdvisor {
    
    private BaseVisibilityPredicate basePredicate = BaseVisibilityPredicate.PUBLIC_AND_OWNER;
    
    private List<String> publicScopes = Collections.singletonList(VisibilityConstants.SCOPE_PUBLIC);
    
    private AuthorizedResources authorizedPredicate = new AuthorizedResources();
    
    /**
     * Scopes used by the public branch of PUBLIC and PUBLIC_AND_OWNER predicates.
     */
    public List<String> getPublicScopes() {
        return publicScopes;
    }
    
    /**
     * Set the public scope branch using canonical names from the scope registry.
     * Empty means no public access. Invalid values are rejected, never treated as no filter.
     *
     * @param publicScopes non-null scope names, in uppercase without whitespace
     * @throws IllegalArgumentException if the list is null or contains an invalid name
     */
    public void setPublicScopes(List<String> publicScopes) {
        if (publicScopes == null) {
            throw new IllegalArgumentException("Public scopes must not be null");
        }
        List<String> scopes = new ArrayList<>(publicScopes);
        for (String scope : scopes) {
            if (scope == null || !scope.matches("[A-Z][A-Z0-9_]{0,15}")) {
                throw new IllegalArgumentException(
                    "Public scopes must contain canonical scope names");
            }
        }
        this.publicScopes = Collections.unmodifiableList(scopes);
    }
    
    public BaseVisibilityPredicate getBasePredicate() {
        return basePredicate;
    }
    
    public void setBasePredicate(BaseVisibilityPredicate basePredicate) {
        this.basePredicate = basePredicate;
    }
    
    public AuthorizedResources getAuthorizedPredicate() {
        return authorizedPredicate;
    }
    
    public void setAuthorizedPredicate(AuthorizedResources authorizedPredicate) {
        this.authorizedPredicate = authorizedPredicate;
    }
}
