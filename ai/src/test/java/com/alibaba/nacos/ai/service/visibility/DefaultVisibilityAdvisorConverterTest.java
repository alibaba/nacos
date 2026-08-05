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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    void convertShouldRecordAuthorizedResourceNamesButNotRestrictAllPredicate() {
        // ALL OR G is still ALL: authorized resources must not narrow an unrestricted predicate.
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.ALL);
        advisor.setAuthorizedPredicate(authorizedResources("skillA", "skillB"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertEquals(List.of("skillA", "skillB"), actual.getAuthorizedResourceNames());
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldKeepOwnerInOrGroupWhenAuthorizedResourcesAlsoExist() {
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertTrue(actual.getOwner() == null || actual.getOwner().isEmpty());
        assertEquals("userA", actual.getOrGroup().get("owner"));
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    // ---- Issue #15603: B OR G union, per predicate ----
    
    @Test
    void convertShouldUnionOwnerBranchWithAuthorizedResourcesWhenOwnerBlank() {
        // Case 1: OWNER + G, owner not yet set -> owner = currentUser OR name IN G.
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertTrue(actual.getOwner() == null || actual.getOwner().isEmpty());
        assertEquals("userA", actual.getOrGroup().get("owner"));
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldReduceOwnerToAuthorizedResourcesWhenIdentityMismatched() {
        // OWNER conflict rescued by G: B alone is impossible, B OR G collapses to G.
        QueryCondition condition = new QueryCondition();
        condition.setOwner("anotherUser");
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertFalse(actual.isAlwaysEmpty());
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    @Test
    void convertShouldUnionOwnerBranchWithAuthorizedResourcesWhenIdentityBlank() {
        // Anonymous callers still get F AND (B OR G): the default visibility implementation
        // never populates G for them in practice, but this generic converter must not force
        // alwaysEmpty and discard G whenever a custom visibility plugin does supply one.
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, null, advisor, new VisibilityQueryContext());
        
        assertFalse(actual.isAlwaysEmpty());
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    @Test
    void convertShouldUnionPublicScopeWithAuthorizedResourcesWhenScopeBlank() {
        // Case 2: PUBLIC + G, scope not yet set -> scope = PUBLIC OR name IN G.
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertTrue(actual.getScope() == null || actual.getScope().isEmpty());
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, actual.getOrGroup().get("scope"));
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldReduceScopeToAuthorizedResourcesWhenScopeConflicts() {
        // PUBLIC conflict rescued by G: B alone is impossible, B OR G collapses to G, while the
        // pre-existing (non-visibility) scope filter is left untouched.
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertFalse(actual.isAlwaysEmpty());
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, actual.getScope());
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    @Test
    void convertShouldNotRestrictAllPredicateEvenWithAuthorizedResources() {
        // Case 3: ALL + G -> no visibility restriction, since ALL OR G is still ALL.
        QueryCondition condition = new QueryCondition();
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.ALL);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertTrue(actual.getOrGroup().isEmpty());
        assertFalse(actual.isAlwaysEmpty());
    }
    
    @Test
    void convertShouldRescuePublicAndOwnerConflictWithAuthorizedResources() {
        // Case 4: PUBLIC_AND_OWNER + G, scope and owner both already conflict -> previously this
        // forced alwaysEmpty before G was considered; now B OR G collapses to G.
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        condition.setOwner("anotherUser");
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor, new VisibilityQueryContext());
        
        assertFalse(actual.isAlwaysEmpty());
        assertEquals(List.of("skillA"), actual.getOrGroup().get("name"));
    }
    
    @Test
    void convertShouldStillMarkAlwaysEmptyForConflictsWhenNoAuthorizedResources() {
        // Existing behavior is unchanged when AuthorizedResources.resources is empty.
        QueryCondition condition = new QueryCondition();
        condition.setScope(VisibilityConstants.SCOPE_PRIVATE);
        condition.setOwner("anotherUser");
        
        QueryCondition actual =
            converter.convert(condition, "userA", advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER),
                new VisibilityQueryContext());
        
        assertTrue(actual.isAlwaysEmpty());
    }
    
    // ---- Issue #15603: full F AND (B OR G) matrix for the non-empty AuthorizedResources case ----
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("nonEmptyAuthorizedResourcesMatrix")
    void convertShouldMatchFAndBOrGMatrix(String description, BaseVisibilityPredicate predicate,
        String initialScope, String initialOwner, String identity, String expectedScope,
        String expectedOwner, Map<String, Object> expectedOrGroup) {
        QueryCondition condition = new QueryCondition();
        condition.setScope(initialScope);
        condition.setOwner(initialOwner);
        QueryAdvisor advisor = advisor(predicate);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition actual =
            converter.convert(condition, identity, advisor, new VisibilityQueryContext());
        
        assertFalse(actual.isAlwaysEmpty());
        assertEquals(expectedScope, actual.getScope());
        assertEquals(expectedOwner, actual.getOwner());
        assertEquals(expectedOrGroup, actual.getOrGroup());
    }
    
    private static Stream<Arguments> nonEmptyAuthorizedResourcesMatrix() {
        return Stream.of(
            Arguments.of("PUBLIC + scope=PUBLIC + G -> F", BaseVisibilityPredicate.PUBLIC,
                VisibilityConstants.SCOPE_PUBLIC, null, "userA",
                VisibilityConstants.SCOPE_PUBLIC, null, Map.of()),
            Arguments.of("OWNER + owner=identity + G -> F", BaseVisibilityPredicate.OWNER,
                null, "userA", "userA",
                null, "userA", Map.of()),
            Arguments.of("OWNER + blank identity + G -> F AND G", BaseVisibilityPredicate.OWNER,
                null, null, null,
                null, null, Map.of("name", List.of("skillA"))),
            Arguments.of("PUBLIC_AND_OWNER + blank scope/owner + G -> F AND (P OR O OR G)",
                BaseVisibilityPredicate.PUBLIC_AND_OWNER, null, null, "userA",
                null, null,
                orGroup("scope", VisibilityConstants.SCOPE_PUBLIC, "owner", "userA", "name",
                    List.of("skillA"))),
            Arguments.of(
                "PUBLIC_AND_OWNER + blank scope, owner!=identity + G -> F AND (P OR G)",
                BaseVisibilityPredicate.PUBLIC_AND_OWNER, null, "anotherUser", "userA",
                null, "anotherUser",
                orGroup("scope", VisibilityConstants.SCOPE_PUBLIC, "name", List.of("skillA"))),
            Arguments.of("PUBLIC_AND_OWNER + scope=PUBLIC + G -> F",
                BaseVisibilityPredicate.PUBLIC_AND_OWNER, VisibilityConstants.SCOPE_PUBLIC, null,
                "userA", VisibilityConstants.SCOPE_PUBLIC, null, Map.of()),
            Arguments.of("PUBLIC_AND_OWNER + owner=identity + G -> F",
                BaseVisibilityPredicate.PUBLIC_AND_OWNER, null, "userA", "userA",
                null, "userA", Map.of()));
    }
    
    private static Map<String, Object> orGroup(Object... keyValuePairs) {
        Map<String, Object> orGroup = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            orGroup.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return orGroup;
    }
    
    @Test
    void convertShouldProduceEquivalentResultWhetherScopeFilterIsAppliedBeforeOrAfterConversion() {
        // F AND (B OR G) must hold regardless of when the caller's business filter F (here,
        // scope=PUBLIC) is combined with the visibility conversion: pre-narrowing the incoming
        // condition, or feeding an already-converted condition back in with the filter now
        // applied, must converge on the same final query.
        QueryAdvisor advisor = advisor(BaseVisibilityPredicate.PUBLIC_AND_OWNER);
        advisor.setAuthorizedPredicate(authorizedResources("skillA"));
        
        QueryCondition filterAppliedBefore = new QueryCondition();
        filterAppliedBefore.setScope(VisibilityConstants.SCOPE_PUBLIC);
        QueryCondition resultBefore =
            converter.convert(filterAppliedBefore, "userA", advisor, new VisibilityQueryContext());
        
        QueryCondition unfiltered = new QueryCondition();
        QueryCondition intermediate =
            converter.convert(unfiltered, "userA", advisor, new VisibilityQueryContext());
        intermediate.setScope(VisibilityConstants.SCOPE_PUBLIC);
        QueryCondition resultAfter =
            converter.convert(intermediate, "userA", advisor, new VisibilityQueryContext());
        
        assertEquals(resultBefore.getScope(), resultAfter.getScope());
        assertEquals(resultBefore.getOwner(), resultAfter.getOwner());
        assertEquals(resultBefore.getOrGroup(), resultAfter.getOrGroup());
        assertEquals(resultBefore.isAlwaysEmpty(), resultAfter.isAlwaysEmpty());
        assertFalse(resultAfter.isAlwaysEmpty());
        assertTrue(resultAfter.getOrGroup().isEmpty());
    }
    
    private AuthorizedResources authorizedResources(String... names) {
        AuthorizedResources authorizedResources = new AuthorizedResources();
        authorizedResources.setResources(List.of(names));
        return authorizedResources;
    }
    
    private QueryAdvisor advisor(BaseVisibilityPredicate predicate) {
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setBasePredicate(predicate);
        return advisor;
    }
}
