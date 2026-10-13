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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import com.alibaba.nacos.ai.service.hub.HubSkillPublicationInvalidator.InvalidationReason;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Source identity, version-scope and malformed-descriptor lifecycle coverage. */
class HubSkillSourceLifecycleRevokerTest {
    
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    private final HubSkillPublicationInvalidator invalidator =
        mock(HubSkillPublicationInvalidator.class);
    private final HubSkillSourceLifecycleRevoker subject =
        new HubSkillSourceLifecycleRevoker(storage, invalidator);
    
    @Test
    void disablesOnlyThePinnedVersionGoingOffline() throws Exception {
        setup();
        when(invalidator.disableIf(eq(901L), any()))
            .thenAnswer(inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1))
                .test(hub("team-a", "1.0.0")));
        assertTrue(subject.revokeVersion(source(), "1.0.0"));
        assertFalse(subject.revokeVersion(source(), "2.0.0"));
    }
    
    @Test
    void deleteDisableAndPrivateInvalidateEveryPinnedVersion() throws Exception {
        setup();
        when(invalidator.disableIf(eq(901L), any()))
            .thenAnswer(inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1))
                .test(hub("team-a", "2.0.0")));
        assertTrue(subject.revokeSource(source()));
    }
    
    @Test
    void sourceNamespaceMismatchCannotTakeDownAnotherOwnersShare() throws Exception {
        setup();
        when(invalidator.disableIf(eq(901L), any()))
            .thenAnswer(inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1))
                .test(hub("team-b", "1.0.0")));
        assertFalse(subject.revokeSource(source()));
    }
    
    @Test
    void malformedDescriptorWithMatchingCanonicalIdentityMustFailClosed() throws Exception {
        setup();
        AiResource corrupt = hub("team-a", "1.0.0");
        corrupt.setExt("{broken-json");
        when(invalidator.disableIf(eq(901L), any()))
            .thenAnswer(inv -> ((InvalidationReason) inv.<InvalidationReason>getArgument(1))
                .test(corrupt));
        assertTrue(subject.revokeVersion(source(), "1.0.0"));
    }
    
    @Test
    void absentPublicationDoesNotTouchOtherNamespaces() throws Exception {
        assertFalse(subject.revokeSource(source()));
        verify(invalidator, never()).disableIf(eq(901L), any());
    }
    
    @Test
    void refusesHubMetadataAsARealSource() {
        AiResource hub = source();
        hub.setNamespaceId("__nacos_ai_hub__");
        assertThrows(NacosException.class, () -> subject.revokeSource(hub));
    }
    
    @Test
    void snapshotsTheAuthorizedSourceIdentityBeforeStorageReads() throws Exception {
        AiResource source = source();
        when(storage.findByInternalName("find-skills-101")).thenAnswer(invocation -> {
            source.setNamespaceId("another-team");
            source.setId(102L);
            return hub("team-a", "1.0.0");
        });
        when(invalidator.disableIf(eq(901L), any())).thenAnswer(invocation -> invocation
            .<InvalidationReason>getArgument(1).test(hub("team-a", "1.0.0")));
        assertTrue(subject.revokeSource(source));
    }
    
    @Test
    void lookupFailureStopsRevocationAndRedactsStorageDetails() throws Exception {
        when(storage.findByInternalName("find-skills-101"))
            .thenThrow(new IllegalStateException("private SQL details"));
        NacosException failure = assertThrows(NacosException.class,
            () -> subject.revokeSource(source()));
        org.junit.jupiter.api.Assertions.assertEquals(500, failure.getErrCode());
        assertFalse(failure.getMessage().contains("SQL"));
        verify(invalidator, never()).disableIf(eq(901L), any());
    }
    
    private void setup() throws Exception {
        when(storage.findByInternalName(HubResourceNameUtils.buildInternalName(
            "find-skills", 101L))).thenReturn(hub("team-a", "1.0.0"));
    }
    
    private AiResource source() {
        AiResource resource = new AiResource();
        resource.setId(101L);
        resource.setNamespaceId("team-a");
        resource.setName("find-skills");
        resource.setType("skill");
        return resource;
    }
    
    private AiResource hub(String namespace, String version) throws Exception {
        AiResource entry = new AiResource();
        entry.setId(901L);
        entry.setName(HubResourceNameUtils.buildInternalName("find-skills", 101L));
        entry.setExt(HubResourceReferenceJsonUtil.toJson(
            HubResourceReferenceJsonUtil.build(namespace, 101L, "find-skills", 501L, version)));
        return entry;
    }
}
