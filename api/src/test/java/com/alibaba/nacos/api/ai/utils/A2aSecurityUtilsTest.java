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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.a2a.SecurityRequirement;
import com.alibaba.nacos.api.ai.model.a2a.StringList;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class A2aSecurityUtilsTest extends BasicRequestTest {
    
    private static final String LEGACY_CARD =
        "\"securityRequirements\":[{\"cardOnly\":[\"read\"]}],";
    
    private MockedStatic<JsonUtils> json;
    
    @BeforeEach
    void bindTestJsonCodec() {
        // API does not depend on a runtime JSON adapter. Delegate only the codec boundary to
        // the existing test mapper; all A2A validation and compatibility code executes normally.
        json = mockStatic(JsonUtils.class);
        json.when(() -> JsonUtils.toObj(anyString(), any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(1);
            try {
                return mapper.readValue((String) invocation.getArgument(0), type);
            } catch (JsonProcessingException invalid) {
                throw new NacosDeserializationException(type, invalid);
            }
        });
        json.when(() -> JsonUtils.toJson(any())).thenAnswer(
            invocation -> mapper.writeValueAsString(invocation.getArgument(0)));
    }
    
    @AfterEach
    void closeTestJsonCodec() {
        json.close();
    }
    
    @Test
    void standardSkillDeclarationsDoNotEnterCardCompatibilityOrFillCardSecurity() {
        String input = "{\"skills\":[{\"securityRequirements\":[{\"schemes\":{"
            + "\"oauth2\":{\"list\":[\"read\",\"profile\"]},\"apiKey\":{\"list\":[]}}},"
            + "{\"schemes\":{\"bearer\":{}}}]}]}";
        AgentCard card = A2aSecurityUtils.readCard(input, AgentCard.class);
        assertNull(card.getSecurityRequirements());
        assertNull(card.getSecurity());
        List<SecurityRequirement> requirements = card.getSkills().get(0).getSecurityRequirements();
        assertEquals(2, requirements.size());
        assertEquals(Arrays.asList("read", "profile"),
            requirements.get(0).getSchemes().get("oauth2").getList());
        assertTrue(requirements.get(0).getSchemes().get("apiKey").getList().isEmpty());
        assertTrue(requirements.get(1).getSchemes().get("bearer").getList().isEmpty());
        A2aSecurityUtils.normalize(card);
        assertSame(requirements, card.getSkills().get(0).getSecurityRequirements());
        json.verify(() -> JsonUtils.toObj(input, Map.class), never());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]", "[{}]",
        "[{\"securityRequirements\":null}]", "[{\"securityRequirements\":[]}]",
        "[{\"securityRequirements\":[{\"schemes\":{}},{\"schemes\":{"
            + "\"oauth2\":{\"list\":[\"skill-read\"]},\"bearer\":{}}}]}]"})
    void historicalCardRetryPreservesIndependentSkillDeclarations(String skills) throws Exception {
        String input = "{" + LEGACY_CARD + "\"skills\":" + skills + "}";
        AgentCard expected = mapper.readValue("{\"skills\":" + skills + "}", AgentCard.class);
        AgentCard card = A2aSecurityUtils.readCard(input, AgentCard.class);
        assertEquals(expected.getSkills(), card.getSkills());
        assertEquals(Collections.singletonList("read"),
            card.getSecurityRequirements().get(0).getSchemes().get("cardOnly").getList());
        assertEquals(Collections.singletonList(Collections.singletonMap("cardOnly",
            Collections.singletonList("read"))), card.getSecurity());
        json.verify(() -> JsonUtils.toObj(input, Map.class));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"{}", "[1]", "[{\"securityRequirements\":{}}]",
        "[{\"securityRequirements\":[null]}]", "[{\"securityRequirements\":[{}]}]",
        "[{\"securityRequirements\":[{\"oauth2\":[\"read\"]}]}]",
        "[{\"securityRequirements\":[{\"schemes\":null}]}]",
        "[{\"securityRequirements\":[{\"schemes\":[]}]}]",
        "[{\"securityRequirements\":[{\"schemes\":{\"oauth2\":null}}]}]",
        "[{\"securityRequirements\":[{\"schemes\":{\"oauth2\":[]}}]}]",
        "[{\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":null}}}]}]",
        "[{\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":{}}}}]}]",
        "[{\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":[null]}}}]}]"})
    void invalidSkillsCannotBeRepairedByCardFallback(String skills) throws Exception {
        for (String root : Arrays.asList("", LEGACY_CARD)) {
            String input = "{" + root + "\"skills\":" + skills + "}";
            assertTrue(mapper.readTree(input).has("skills"));
            NacosDeserializationException exception = assertThrows(
                NacosDeserializationException.class,
                () -> A2aSecurityUtils.readCard(input, AgentCard.class));
            assertEquals(AgentCard.class, exception.getTargetClass());
        }
    }
    
    @ParameterizedTest
    @MethodSource("invalidTypedRequirements")
    void incompleteTypedSkillsFailBeforeChangingCardDeclarations(
        List<SecurityRequirement> requirements) {
        AgentSkill skill = new AgentSkill();
        skill.setSecurityRequirements(requirements);
        AgentCard card = new AgentCard();
        card.setSkills(Collections.singletonList(skill));
        List<Map<String, List<String>>> legacy = Collections.singletonList(
            Collections.singletonMap("cardOnly", Collections.singletonList("read")));
        card.setSecurity(legacy);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> A2aSecurityUtils.normalize(card));
        assertTrue(exception.getMessage().contains("AgentSkill securityRequirements"));
        assertSame(legacy, card.getSecurity());
        assertNull(card.getSecurityRequirements());
        assertSame(requirements, skill.getSecurityRequirements());
        assertThrows(IllegalArgumentException.class, () -> A2aSecurityUtils.toLegacy(requirements));
    }
    
    static Stream<List<SecurityRequirement>> invalidTypedRequirements() {
        StringList nullList = new StringList();
        nullList.setList(null);
        StringList nullScope = new StringList();
        nullScope.setList(Collections.singletonList(null));
        return Stream.of(Collections.singletonList(null),
            Collections.singletonList(new SecurityRequirement()),
            requirement(null, new StringList()), requirement("oauth2", null),
            requirement("oauth2", nullList), requirement("oauth2", nullScope));
    }
    
    @Test
    void strictDecoderCanRetryHistoricalCardWithoutChangingValidSkills() {
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AgentCard card = A2aSecurityUtils.readCard("{" + LEGACY_CARD
            + "\"skills\":[{\"id\":\"s\",\"securityRequirements\":[]}]}", AgentCard.class);
        assertTrue(A2aSecurityUtils.isNormalized(card));
        assertTrue(card.getSkills().get(0).getSecurityRequirements().isEmpty());
        assertEquals("read", card.getSecurity().get(0).get("cardOnly").get(0));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"name\":{},\"securityRequirements\":[]}",
        "{\"securityRequirements\":{}}", "{\"securityRequirements\":[null]}",
        "{\"securityRequirements\":[{\"oauth2\":null}]}",
        "{\"securityRequirements\":[{\"oauth2\":[null]}]}"})
    void unrelatedBindingErrorsAndInvalidHistoricalCardsStillFail(String input) {
        NacosDeserializationException exception = assertThrows(NacosDeserializationException.class,
            () -> A2aSecurityUtils.readCard(input, AgentCard.class));
        assertEquals(AgentCard.class, exception.getTargetClass());
    }
    
    @Test
    void cardConversionCopiesScopesAndKeepsExplicitEmptyRequirementsAuthoritative() {
        List<String> scopes = new ArrayList<>(Collections.singletonList("card-read"));
        List<Map<String, List<String>>> legacy = Collections.singletonList(
            Collections.singletonMap("cardOnly", scopes));
        List<SecurityRequirement> standard = A2aSecurityUtils.fromLegacy(legacy);
        List<Map<String, List<String>>> copied = A2aSecurityUtils.toLegacy(standard);
        copied.get(0).get("cardOnly").clear();
        assertEquals(Collections.singletonList("card-read"), scopes);
        assertEquals(scopes, standard.get(0).getSchemes().get("cardOnly").getList());
        AgentCard card = new AgentCard();
        card.setSecurity(legacy);
        A2aSecurityUtils.normalize(card);
        assertEquals(standard, card.getSecurityRequirements());
        card.setSecurityRequirements(Collections.emptyList());
        assertFalse(A2aSecurityUtils.isNormalized(card));
        A2aSecurityUtils.normalize(card);
        assertTrue(card.getSecurity().isEmpty());
        assertEquals(Collections.singletonList("card-read"), scopes);
        assertTrue(A2aSecurityUtils.isNormalized(card));
        assertNull(A2aSecurityUtils.fromLegacy(null));
        assertNull(A2aSecurityUtils.toLegacy(null));
        assertThrows(IllegalArgumentException.class, () -> A2aSecurityUtils.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> A2aSecurityUtils.fromLegacy(
            Collections.singletonList(Collections.singletonMap(null, scopes))));
    }
    
    private static List<SecurityRequirement> requirement(String name, StringList scopes) {
        SecurityRequirement requirement = new SecurityRequirement();
        requirement.setSchemes(Collections.singletonMap(name, scopes));
        return Collections.singletonList(requirement);
    }
}
