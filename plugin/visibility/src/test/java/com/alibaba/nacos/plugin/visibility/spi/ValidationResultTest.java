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
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ValidationResultTest {
    
    @Test
    void testAllowAndDenyFactories() {
        ValidationResult allow = ValidationResult.allow();
        ValidationResult deny = ValidationResult.deny("private");
        
        assertTrue(allow.isAllowed());
        assertNull(allow.getReason());
        assertFalse(deny.isAllowed());
        assertEquals("private", deny.getReason());
    }
    
    @Test
    void testQueryAdvisorAccessors() {
        QueryAdvisor advisor = new QueryAdvisor();
        AuthorizedResources resources = new AuthorizedResources();
        
        advisor.setBasePredicate(BaseVisibilityPredicate.ALL);
        advisor.setAuthorizedPredicate(resources);
        
        assertEquals(BaseVisibilityPredicate.ALL, advisor.getBasePredicate());
        assertSame(resources, advisor.getAuthorizedPredicate());
    }
    
    @Test
    void testVisibilityServiceDefaultMethods() {
        VisibilityService service = new FakeVisibilityService();
        
        service.init(new Properties());
        
        assertEquals(VisibilityConstants.SCOPE_PRIVATE,
            service.resolveDefaultScopeForCreate("user", "console", "skill"));
        assertTrue(service.validateVisibility("user", "r", "console", null).isAllowed());
        assertEquals("fake", service.getVisibilityServiceName());
        assertEquals(BaseVisibilityPredicate.PUBLIC_AND_OWNER,
            service.adviseQuery("user", "r", "console", new VisibilityQueryContext())
                .getBasePredicate());
    }
    
    private static class FakeVisibilityService implements VisibilityService {
        
        @Override
        public ValidationResult validateVisibility(String identity, String action, String apiType,
            VisibilityResource resource) {
            return ValidationResult.allow();
        }
        
        @Override
        public QueryAdvisor adviseQuery(String identity, String action, String apiType,
            VisibilityQueryContext context) {
            return new QueryAdvisor();
        }
        
        @Override
        public String getVisibilityServiceName() {
            return "fake";
        }
    }
}
