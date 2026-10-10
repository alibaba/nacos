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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.hub.HubShareSkillForm;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.ai.form.hub.HubAdjustShareForm;
import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.ai.form.hub.HubUnshareForm;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.ai.form.hub.HubForceUnshareForm;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.ai.param.HubSkillHttpParamExtractor;
import com.alibaba.nacos.ai.service.hub.HubSkillService;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP binding, validation, response and annotation tests for Hub Skill APIs.
 *
 * @author nacos
 */
class HubSkillAdminControllerTest {
    
    private final HubSkillService boundary = mock(HubSkillService.class);
    
    private MockMvc mvc;
    
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new HubSkillAdminController(boundary))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    }
    
    @Test
    void shareMapsExactSourceIdentityAndStringIds() throws Exception {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(9007199254740993L);
        result.setMetaVersion(7L);
        result.setDisplayName("find-skills");
        result.setOutcome(HubShareOutcome.CREATED);
        result.setActive(true);
        when(boundary.shareSkill(any())).thenReturn(result);
        String json = mvc.perform(post(Constants.Hub.ADMIN_PATH + "/share")
            .param("sourceNamespaceId", "source-a").param("sourceName", "find-skills")
            .param("sourceVersion", "1.0.0").param("expectedSourceResourceId", "9007199254740993"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ArgumentCaptor<HubShareSkillRequest> request =
            ArgumentCaptor.forClass(HubShareSkillRequest.class);
        verify(boundary).shareSkill(request.capture());
        assertEquals("source-a", request.getValue().getSourceNamespaceId());
        assertEquals("find-skills", request.getValue().getSourceName());
        assertEquals("1.0.0", request.getValue().getSourceVersion());
        assertEquals(9007199254740993L, request.getValue().getExpectedSourceResourceId());
        com.fasterxml.jackson.databind.JsonNode data = JacksonUtils.toObj(json).get("data");
        assertTrue(data.get("hubEntryId").isTextual());
        assertEquals("9007199254740993", data.get("hubEntryId").asText());
        assertEquals("7", data.get("metaVersion").asText());
        assertEquals("CREATED", data.get("outcome").asText());
        assertFalse(data.has("ext"));
        assertFalse(data.has("storage"));
    }
    
    @Test
    void optionalSourceResourceIdRemainsAbsent() throws Exception {
        mvc.perform(post(Constants.Hub.ADMIN_PATH + "/share")
            .param("sourceNamespaceId", "public").param("sourceName", "find-skills")
            .param("sourceVersion", "1.0.0")).andExpect(status().isOk());
        ArgumentCaptor<HubShareSkillRequest> request =
            ArgumentCaptor.forClass(HubShareSkillRequest.class);
        verify(boundary).shareSkill(request.capture());
        assertEquals(null, request.getValue().getExpectedSourceResourceId());
    }
    
    @Test
    void adjustPreservesExpectedMetaVersion() throws Exception {
        mvc.perform(post(Constants.Hub.ADMIN_PATH + "/adjust")
            .param("hubEntryId", "91").param("targetVersion", "2.0.0")
            .param("expectedMetaVersion", "8")).andExpect(status().isOk());
        ArgumentCaptor<HubAdjustShareRequest> request =
            ArgumentCaptor.forClass(HubAdjustShareRequest.class);
        verify(boundary).adjustShare(request.capture());
        assertEquals(91L, request.getValue().getHubEntryId());
        assertEquals(8L, request.getValue().getExpectedMetaVersion());
        assertEquals("2.0.0", request.getValue().getTargetVersion());
    }
    
    @Test
    void unsharePreservesEntryAndExpectedMetaVersion() throws Exception {
        mvc.perform(post(Constants.Hub.ADMIN_PATH + "/unshare")
            .param("hubEntryId", "91").param("expectedMetaVersion", "8"))
            .andExpect(status().isOk());
        ArgumentCaptor<HubUnshareRequest> request =
            ArgumentCaptor.forClass(HubUnshareRequest.class);
        verify(boundary).unshareSkill(request.capture());
        assertEquals(91L, request.getValue().getHubEntryId());
        assertEquals(8L, request.getValue().getExpectedMetaVersion());
    }
    
    @Test
    void governancePreservesExactReason() throws Exception {
        mvc.perform(post(Constants.Hub.ADMIN_PATH + "/force-unshare")
            .param("hubEntryId", "88").param("expectedMetaVersion", "7")
            .param("reason", "policy violation")).andExpect(status().isOk());
        ArgumentCaptor<HubForceUnshareRequest> request =
            ArgumentCaptor.forClass(HubForceUnshareRequest.class);
        verify(boundary).forceUnshare(request.capture());
        assertEquals(88L, request.getValue().getHubEntryId());
        assertEquals(7L, request.getValue().getExpectedMetaVersion());
        assertEquals("policy violation", request.getValue().getReason());
    }
    
    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidFormsReturn400WithoutDelegation(String suffix, Map<String, String> params)
        throws Exception {
        MockHttpServletRequestBuilder request = post(Constants.Hub.ADMIN_PATH + suffix);
        params.forEach(request::param);
        String json = mvc.perform(request).andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
            JacksonUtils.toObj(json).get("code").asInt());
        verifyNoInteractions(boundary);
    }
    
    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("/share", Map.of()),
            Arguments.of("/share",
                Map.of("sourceNamespaceId", "public", "sourceName", " ", "sourceVersion", "1.0.0")),
            Arguments.of("/share",
                Map.of("sourceNamespaceId", "__nacos_ai_hub__", "sourceName", "skill",
                    "sourceVersion", "1.0.0")),
            Arguments.of("/share",
                Map.of("sourceNamespaceId", "public", "sourceName", "skill", "sourceVersion",
                    "latest")),
            Arguments.of("/share",
                Map.of("sourceNamespaceId", "public", "sourceName", "skill", "sourceVersion",
                    "1.0.0", "expectedSourceResourceId", "0")),
            Arguments.of("/share",
                Map.of("sourceNamespaceId", "public", "sourceName", "skill", "sourceVersion",
                    "1.0.0", "expectedSourceResourceId", "bad")),
            Arguments.of("/adjust",
                Map.of("hubEntryId", "1", "expectedMetaVersion", "1", "targetVersion", "LATEST")),
            Arguments.of("/adjust", Map.of("hubEntryId", "1", "targetVersion", "2.0.0")),
            Arguments.of("/adjust",
                Map.of("hubEntryId", "0", "expectedMetaVersion", "1", "targetVersion", "2.0.0")),
            Arguments.of("/unshare", Map.of("hubEntryId", "abc", "expectedMetaVersion", "1")),
            Arguments.of("/unshare",
                Map.of("hubEntryId", "9223372036854775808", "expectedMetaVersion", "1")),
            Arguments.of("/unshare", Map.of("hubEntryId", "1", "expectedMetaVersion", "-1")),
            Arguments.of("/unshare", Map.of("expectedMetaVersion", "1")),
            Arguments.of("/force-unshare", Map.of("hubEntryId", "1", "expectedMetaVersion", "1")),
            Arguments.of("/force-unshare",
                Map.of("hubEntryId", "1", "expectedMetaVersion", "1", "reason", " ")),
            Arguments.of("/force-unshare",
                Map.of("hubEntryId", "1", "expectedMetaVersion", "1", "reason", "x".repeat(513))));
    }
    
    @Test
    void conflictRetains409AndDomainError() throws Exception {
        when(boundary.adjustShare(any()))
            .thenThrow(new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT, "stale metadata"));
        String json = mvc.perform(post(Constants.Hub.ADMIN_PATH + "/adjust")
            .param("hubEntryId", "91").param("targetVersion", "2.0.0")
            .param("expectedMetaVersion", "8")).andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(),
            JacksonUtils.toObj(json).get("code").asInt());
    }
    
    @Test
    void governanceDenialRetains403() throws Exception {
        when(boundary.forceUnshare(any())).thenThrow(
            new NacosApiException(403, ErrorCode.ACCESS_DENIED, "no Hub management permission"));
        mvc.perform(post(Constants.Hub.ADMIN_PATH + "/force-unshare")
            .param("hubEntryId", "88").param("expectedMetaVersion", "7")
            .param("reason", "policy violation")).andExpect(status().isForbidden());
    }
    
    @Test
    void allFourEndpointsUseProjectAnnotationsAndDedicatedResources() throws Exception {
        assertEquals(Constants.Hub.ADMIN_PATH,
            HubSkillAdminController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(HubSkillHttpParamExtractor.class, HubSkillAdminController.class
            .getAnnotation(ExtractorManager.Extractor.class).httpExtractor());
        assertEndpoint("share", HubShareSkillForm.class, "ai-hub/entry");
        assertEndpoint("adjust", HubAdjustShareForm.class, "ai-hub/entry");
        assertEndpoint("unshare", HubUnshareForm.class, "ai-hub/entry");
        assertEndpoint("forceUnshare", HubForceUnshareForm.class, "ai-hub/manage");
    }
    
    private void assertEndpoint(String name, Class<?> formType, String resource) throws Exception {
        Method method = HubSkillAdminController.class.getDeclaredMethod(name, formType);
        Secured secured = method.getAnnotation(Secured.class);
        assertEquals(ApiType.ADMIN_API, secured.apiType());
        assertTrue(Arrays.asList(secured.tags())
            .contains(com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY));
        assertEquals(resource, secured.resource());
        assertEquals("3.3.0", method.getAnnotation(Since.class).value());
    }
}
