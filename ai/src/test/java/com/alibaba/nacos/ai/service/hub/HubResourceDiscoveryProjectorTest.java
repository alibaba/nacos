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
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubResourceDiscoveryInfo;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

class HubResourceDiscoveryProjectorTest {
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void sourceCoordinatesAreExposedOnlyAfterIndependentReadGrant(HubResourceType type)
        throws Exception {
        HubSkillReadPermissionGuard permissions = mock(HubSkillReadPermissionGuard.class);
        HubResourceDiscoveryProjector projector = new HubResourceDiscoveryProjector(
            mock(NamespaceOperationService.class), permissions);
        AiResource source = new AiResource();
        AiResource hub = new AiResource();
        hub.setId(501L);
        HubResourceDirectoryService.Entry entry = new HubResourceDirectoryService.Entry(hub, source,
            HubResourceReferenceJsonUtil.build("public", 101L, "source-name", 201L, "1.0.0"));
        HubResourceDiscoveryInfo denied = projector.project(type, entry);
        assertFalse(denied.isSourceReadable());
        assertNull(denied.getSourceNamespaceId());
        assertNull(denied.getSourceName());
        when(permissions.canReadSource(type, source)).thenReturn(true);
        HubResourceDiscoveryInfo allowed = projector.project(type, entry);
        assertTrue(allowed.isSourceReadable());
        assertEquals("public", allowed.getSourceNamespaceId());
        assertEquals("source-name", allowed.getSourceName());
        assertEquals("1.0.0", allowed.getVersion());
        when(permissions.canReadSource(type, source)).thenThrow(
            new com.alibaba.nacos.api.exception.NacosException(403, "denied"));
        assertFalse(projector.project(type, entry).isSourceReadable());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void exposesHubModificationTimeIndependentlyFromSourceDates(HubResourceType type)
        throws Exception {
        NamespaceOperationService namespaces = mock(NamespaceOperationService.class);
        HubResourceDiscoveryProjector projector =
            new HubResourceDiscoveryProjector(namespaces, mock(HubSkillReadPermissionGuard.class));
        AiResource hub = new AiResource();
        hub.setId(501L);
        hub.setGmtCreate(new Timestamp(1600000000000L));
        hub.setGmtModified(new Timestamp(1700000000000L));
        AiResource source = new AiResource();
        source.setOwner("alice");
        source.setGmtModified(new Timestamp(1800000000000L));
        HubResourceDirectoryService.Entry entry = new HubResourceDirectoryService.Entry(hub,
            source, HubResourceReferenceJsonUtil.build("public", 101L, "same-name", 201L,
                "1.0.0"));
        HubResourceDiscoveryInfo result = projector.project(type, entry);
        assertEquals(1700000000000L, result.getUpdateTime());
        assertEquals("public", result.getSourceDisplayName());
        source.setGmtModified(new Timestamp(1900000000000L));
        assertEquals(1700000000000L, projector.project(type, entry).getUpdateTime());
        hub.setGmtModified(new Timestamp(1700000005000L));
        assertEquals(1700000005000L, projector.project(type, entry).getUpdateTime());
        verifyNoInteractions(namespaces);
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void missingHubTimestampDoesNotSubstituteSourceOrCreationTime(HubResourceType type)
        throws Exception {
        AiResource hub = new AiResource();
        hub.setGmtCreate(new Timestamp(1600000000000L));
        AiResource source = new AiResource();
        source.setGmtModified(new Timestamp(1800000000000L));
        HubResourceDiscoveryProjector projector =
            new HubResourceDiscoveryProjector(mock(NamespaceOperationService.class),
                mock(HubSkillReadPermissionGuard.class));
        assertNull(projector.project(type, new HubResourceDirectoryService.Entry(hub, source,
            HubResourceReferenceJsonUtil.build("public", 101L, "same-name", 201L, "1.0.0")))
            .getUpdateTime());
    }
}
