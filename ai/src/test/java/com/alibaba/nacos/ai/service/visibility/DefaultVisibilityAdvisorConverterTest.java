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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.AuthorizedResources;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVisibilityAdvisorConverterTest {
    
    private final DefaultVisibilityAdvisorConverter converter =
        new DefaultVisibilityAdvisorConverter();
    
    @Test
    void convertShouldReturnDefaultConditionWhenConditionAndAdvisorAreNull() {
        QueryCondition actual =
            converter.convert(null, "userA", null, new VisibilityQueryContext());
        
        assertNotNull(actual);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, actual.getNamespaceId());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepConditionWhenBasePredicateIsNull() {
        QueryCondition condition = new QueryCondition();
        condition.putOrGroup("scope", "PUBLIC");
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setBasePredicate(null);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertSame(condition, actual);
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepConditionForAllPredicate() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        condition.setOwner("ownerA");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.ALL),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual.getScope());
        assertEquals("ownerA", actual.getOwner());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldSetPublicScopeForPublicPredicateWhenScopeIsBlank() {
        QueryCondition condition = new QueryCondition();
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual.getScope());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldMarkAlwaysEmptyForPublicPredicateWhenScopeIsPrivate() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC),
                new VisibilityQueryContext());
        
        assertTrue(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepPublicScopeForPublicPredicateWhenScopeIsPublic() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PUBLIC);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual.getScope());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldMarkAlwaysEmptyForOwnerPredicateWhenIdentityIsBlank() {
        QueryCondition condition = new QueryCondition();
        
        QueryCondition actual =
            converter.convert(condition, null, advisor(BaseVisibilityPredicate.OWNER),
                new VisibilityQueryContext());
        
        assertTrue(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldFillOwnerForOwnerPredicateWhenOwnerIsBlank() {
        QueryCondition condition = new QueryCondition();
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.OWNER),
                new VisibilityQueryContext());
        
        assertEquals("userA", actual.getOwner());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldMarkAlwaysEmptyForOwnerPredicateWhenOwnerMismatched() {
        QueryCondition condition = new QueryCondition();
        condition.setOwner("anotherUser");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.OWNER),
                new VisibilityQueryContext());
        
        assertTrue(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepOwnerForOwnerPredicateWhenOwnerMatched() {
        QueryCondition condition = new QueryCondition();
        condition.setOwner("userA");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.OWNER),
                new VisibilityQueryContext());
        
        assertEquals("userA", actual.getOwner());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldFallbackToPublicForPublicAndOwnerWhenIdentityBlank() {
        QueryCondition condition = new QueryCondition();
        
        QueryCondition actual =
            converter.convert(condition, "", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual.getScope());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepConditionForPublicAndOwnerWhenScopeIsPublic() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PUBLIC);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepConditionForPublicAndOwnerWhenOwnerIsIdentity() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        condition.setOwner("userA");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual.getScope());
        assertEquals("userA", actual.getOwner());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldMarkAlwaysEmptyForPublicAndOwnerWhenScopeAndOwnerConflict() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        condition.setOwner("anotherUser");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertTrue(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldBuildOrGroupForPublicAndOwnerWhenNoScopeAndNoOwner() {
        QueryCondition condition = new QueryCondition();
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("scope", VisibilityConstants.SCOPE_PUBLIC);
        expected.put("owner", "userA");
        assertEquals(expected, actual.getOrGroup());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldSimplifyToOwnerForPublicAndOwnerWhenOnlyScopeExists() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertEquals("userA", actual.getOwner());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldSimplifyToPublicForPublicAndOwnerWhenOnlyOwnerExists() {
        QueryCondition condition = new QueryCondition();
        condition.setOwner("anotherUser");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual.getScope());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldAddAuthorizedResourcesIntoCondition() {
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.ALL);
        AuthorizedResources authorizedResources = new AuthorizedResources();
        authorizedResources.setResources(List.of("skillA", "skillB"));
        advisor.setAuthorizedPredicate(authorizedResources);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertEquals(List.of("skillA", "skillB"), actual.getAuthorizedResourceNames());
        assertEquals(List.of("skillA", "skillB"), actual.getOrGroup().get("name"));
    }
    
    @Test
    void convertShouldKeepOwnerInOrGroupWhenAuthorizedResourcesAlsoExist() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        AuthorizedResources authorizedResources = new AuthorizedResources();
        authorizedResources.setResources(List.of("skillA"));
        advisor.setAuthorizedPredicate(authorizedResources);
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertTrue(actual.getOwner() == null || actual.getOwner().isEmpty());
        assertEquals("userA", actual.getOrGroup().get("owner"));
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    private QueryAdvisor advisor(BaseVisibilityPredicate predicate) {
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setBasePredicate(predicate);
        return advisor;
    }
}
