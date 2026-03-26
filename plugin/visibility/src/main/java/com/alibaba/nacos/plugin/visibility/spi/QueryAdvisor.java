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

import com.alibaba.nacos.plugin.visibility.model.AuthorizedResources;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;

/**
 * Visibility query advisor for range/list operations.
 *
 * @author xiweng.yy
 */
public class QueryAdvisor {
    
    private BaseVisibilityPredicate basePredicate = BaseVisibilityPredicate.PUBLIC_AND_OWNER;
    
    private AuthorizedResources authorizedPredicate = new AuthorizedResources();
    
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
