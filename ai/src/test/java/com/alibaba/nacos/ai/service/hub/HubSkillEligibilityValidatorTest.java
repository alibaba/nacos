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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * S1-2 identity and source-eligibility tests. Authorization is tested in S1-3.
 */
@ExtendWith(MockitoExtension.class)
class HubSkillEligibilityValidatorTest {
    
    private static final String SOURCE_NAMESPACE = "team-a";
    
    private static final String SOURCE_NAME = "find-skills";
    
    private static final String SOURCE_VERSION = "1.0.0";
    
    @Mock
    private AiResourceManager resourceManager;
    
    private HubSkillEligibilityValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new HubSkillEligibilityValidator(resourceManager);
    }
    
    @Test
    void validPublicOnlineSourceShouldProducePinnedReference() throws NacosException {
        AiResource meta = source(101L);
        AiResourceVersion version = version(501L, AiResourceConstants.VERSION_STATUS_ONLINE);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill")).thenReturn(meta);
        when(resourceManager.findVersion(SOURCE_NAMESPACE, SOURCE_NAME, "skill", SOURCE_VERSION))
            .thenReturn(version);
        HubShareSkillRequest request = request(101L);
        AiResource resolved = validator.requireSourceForShare(request);
        HubEligibleSkillVersion eligible =
            validator.requireShareableVersion(resolved, SOURCE_VERSION);
        HubResourceReference reference = eligible.getReference();
        assertNotNull(reference);
        assertEquals(101L, reference.getSourceResourceId());
        assertEquals(501L, reference.getSourceVersionId());
        assertEquals(SOURCE_NAMESPACE, reference.getSourceNamespaceId());
        assertEquals(SOURCE_NAME, reference.getSourceName());
        assertEquals(SOURCE_VERSION, reference.getSourceVersion());
        assertEquals(meta, eligible.getSourceMeta());
        assertEquals(version, eligible.getSourceVersion());
        assertEquals(reference.getSourceVersionId(),
            HubResourceReferenceJsonUtil.parse(HubResourceReferenceJsonUtil.toJson(reference))
                .getSourceVersionId());
    }
    
    @Test
    void missingSourceShouldBeNotFound() {
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill")).thenReturn(null);
        assertError(NacosException.NOT_FOUND, () -> validator.requireSourceForShare(request(null)));
    }
    
    @Test
    void expectedSourceResourceIdMismatchShouldConflict() {
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source(202L));
        assertError(NacosException.CONFLICT, () -> validator.requireSourceForShare(request(101L)));
    }
    
    @Test
    void missingResourceIdShouldBeRejected() {
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source(null));
        assertError(NacosException.CONFLICT, () -> validator.requireSourceForShare(request(null)));
    }
    
    @Test
    void hubNamespaceMustNeverBeUsedAsSource() {
        HubShareSkillRequest request = request(null);
        request.setSourceNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        assertError(NacosException.INVALID_PARAM,
            () -> validator.requireSourceForShare(request));
        verifyNoInteractions(resourceManager);
    }
    
    @Test
    void identityChangeBetweenAuthorizationAndQualificationMustConflict() throws NacosException {
        AiResource first = source(101L);
        AiResource replacement = source(202L);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(first, replacement);
        AiResource loaded = validator.requireSourceForShare(request(null));
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(loaded, SOURCE_VERSION));
        verify(resourceManager, never()).findVersion(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void privateSourceMustNotBeShareable() {
        AiResource source = source(101L);
        source.setScope(VisibilityConstants.SCOPE_PRIVATE);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
        verify(resourceManager, never()).findVersion(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void disabledSourceMustNotBeShareable() {
        AiResource source = source(101L);
        source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"draft", "reviewing", "reviewed", "offline"})
    void nonOnlineSourceVersionsMustNotBeShareable(String status) {
        AiResource source = source(101L);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        when(resourceManager.findVersion(SOURCE_NAMESPACE, SOURCE_NAME, "skill", SOURCE_VERSION))
            .thenReturn(version(501L, status));
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
    }
    
    @Test
    void sourceVersionNotFoundShouldBeNotFound() {
        AiResource source = source(101L);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        assertError(NacosException.NOT_FOUND,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
    }
    
    @Test
    void badVersionIdMustNotBeUsedForHubReference() {
        AiResource source = source(101L);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        when(resourceManager.findVersion(SOURCE_NAMESPACE, SOURCE_NAME, "skill", SOURCE_VERSION))
            .thenReturn(version(0L, AiResourceConstants.VERSION_STATUS_ONLINE));
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
    }
    
    @Test
    void inconsistentVersionNameMustBeRejected() {
        AiResource source = source(101L);
        AiResourceVersion version = version(501L, AiResourceConstants.VERSION_STATUS_ONLINE);
        version.setName("other-skill");
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        when(resourceManager.findVersion(SOURCE_NAMESPACE, SOURCE_NAME, "skill", SOURCE_VERSION))
            .thenReturn(version);
        assertError(NacosException.CONFLICT,
            () -> validator.requireShareableVersion(source, SOURCE_VERSION));
    }
    
    @Test
    void labelLatestAndUntrimmedVersionMustBeRejected() {
        AiResource source = source(101L);
        assertError(NacosException.INVALID_PARAM,
            () -> validator.requireShareableVersion(source, "latest"));
        assertError(NacosException.INVALID_PARAM,
            () -> validator.requireShareableVersion(source, " 1.0.0 "));
        verifyNoInteractions(resourceManager);
    }
    
    @Test
    void unshareCanResolveDisabledPrivateSourceWithoutOnlineVersion() throws NacosException {
        AiResource source = source(101L);
        source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        source.setScope(VisibilityConstants.SCOPE_PRIVATE);
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source);
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            SOURCE_NAMESPACE, 101L, SOURCE_NAME, 501L, SOURCE_VERSION);
        assertEquals(source, validator.requireReferencedSource(reference));
        verify(resourceManager, never()).findVersion(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void unshareMustRejectSameNameRecreatedResource() throws NacosException {
        when(resourceManager.findMeta(SOURCE_NAMESPACE, SOURCE_NAME, "skill"))
            .thenReturn(source(202L));
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            SOURCE_NAMESPACE, 101L, SOURCE_NAME, 501L, SOURCE_VERSION);
        assertError(NacosException.CONFLICT,
            () -> validator.requireReferencedSource(reference));
    }
    
    private HubShareSkillRequest request(Long resourceId) {
        HubShareSkillRequest request = new HubShareSkillRequest();
        request.setSourceNamespaceId(SOURCE_NAMESPACE);
        request.setSourceName(SOURCE_NAME);
        request.setSourceVersion(SOURCE_VERSION);
        request.setExpectedSourceResourceId(resourceId);
        return request;
    }
    
    private AiResource source(Long id) {
        AiResource meta = new AiResource();
        meta.setId(id);
        meta.setNamespaceId(SOURCE_NAMESPACE);
        meta.setName(SOURCE_NAME);
        meta.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        return meta;
    }
    
    private AiResourceVersion version(Long id, String status) {
        AiResourceVersion version = new AiResourceVersion();
        version.setId(id);
        version.setNamespaceId(SOURCE_NAMESPACE);
        version.setName(SOURCE_NAME);
        version.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        version.setVersion(SOURCE_VERSION);
        version.setStatus(status);
        return version;
    }
    
    private void assertError(int code, CheckedAction action) {
        NacosApiException exception = assertThrows(NacosApiException.class, action::run);
        assertEquals(code, exception.getErrCode());
    }
    
    @FunctionalInterface
    private interface CheckedAction {
        
        void run() throws NacosException;
    }
}
