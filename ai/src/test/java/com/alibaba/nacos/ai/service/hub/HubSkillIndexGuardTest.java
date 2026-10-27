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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Index protection regressions: disabled / stale / private sources are not indexable.
 */
class HubSkillIndexGuardTest {
    
    private AiResourceManager manager;
    
    private HubEntryStorage storage;
    
    private HubSkillIndexGuard guard;
    
    private AiResource hub;
    
    private AiResourceVersion hubVersion;
    
    private AiResource source;
    
    private AiResourceVersion sourceVersion;
    
    @BeforeEach
    void setUp() throws NacosException {
        manager = mock(AiResourceManager.class);
        storage = mock(HubEntryStorage.class);
        guard = new HubSkillIndexGuard(manager, storage);
        HubResourceReference ref = HubResourceReferenceJsonUtil.build(
            "team-a", 41L, "find-skills", 1001L, "1.0.0");
        hub = new AiResource();
        hub.setId(501L);
        hub.setName("find-skills-41");
        hub.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        hub.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        hub.setFrom("hub");
        hub.setScope(VisibilityConstants.SCOPE_PUBLIC);
        hub.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        hub.setMetaVersion(7L);
        hub.setExt(HubResourceReferenceJsonUtil.toJson(ref));
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setLabels(Collections.singletonMap(AiResourceConstants.LABEL_LATEST, "1.0.0"));
        hub.setVersionInfo(JacksonUtils.toJson(info));
        hubVersion = new AiResourceVersion();
        hubVersion.setName(hub.getName());
        hubVersion.setNamespaceId(hub.getNamespaceId());
        hubVersion.setType(hub.getType());
        hubVersion.setVersion("1.0.0");
        hubVersion.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        hubVersion.setStorage(HubResourceReferenceJsonUtil.toJson(ref));
        source = new AiResource();
        source.setNamespaceId("team-a");
        source.setName("find-skills");
        source.setType(AiResourceConstants.RESOURCE_TYPE_SKILL);
        source.setId(41L);
        source.setScope(VisibilityConstants.SCOPE_PUBLIC);
        source.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        sourceVersion = new AiResourceVersion();
        sourceVersion.setId(1001L);
        sourceVersion.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE);
        when(storage.findByEntryId(501L)).thenReturn(hub);
        when(storage.findByInternalName(hub.getName())).thenReturn(hub);
        when(manager.findMeta("team-a", "find-skills", "skill")).thenReturn(source);
        when(manager.findVersion("team-a", "find-skills", "skill", "1.0.0"))
            .thenReturn(sourceVersion);
    }
    
    @Test
    void activeAndExactSourceCanBeIndexed() {
        assertTrue(guard.isIndexable(hub, hubVersion));
        assertSame(hub, guard.findHubByInternalName(hub.getName()));
    }
    
    @Test
    void disabledHubMustDisappear() {
        hub.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void privateSourceMustDisappear() {
        source.setScope(VisibilityConstants.SCOPE_PRIVATE);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void disabledSourceMustDisappear() {
        source.setStatus(AiResourceConstants.META_STATUS_DISABLE);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void offlineSourceVersionMustDisappear() {
        sourceVersion.setStatus(AiResourceConstants.VERSION_STATUS_OFFLINE);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void nameReuseWithNewResourceIdCannotInheritShare() {
        source.setId(42L);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void newSourceVersionWithSameVersionStringCannotInheritShare() {
        sourceVersion.setId(1002L);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void tamperedImmutableStorageMustDisappear() throws NacosException {
        HubResourceReference other = HubResourceReferenceJsonUtil.build(
            "team-a", 41L, "find-skills", 9999L, "1.0.0");
        hubVersion.setStorage(HubResourceReferenceJsonUtil.toJson(other));
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void missingCurrentVersionLabelMustDisappear() {
        ResourceVersionInfo info = new ResourceVersionInfo();
        info.setLabels(Collections.emptyMap());
        hub.setVersionInfo(JacksonUtils.toJson(info));
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void unrelatedHubOriginMustDisappear() {
        hub.setFrom("local");
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void hubMetaCasChangeDuringProjectionMustDisappear() {
        AiResource changed = new AiResource();
        changed.setId(501L);
        changed.setMetaVersion(8L);
        changed.setExt(hub.getExt());
        changed.setStatus(hub.getStatus());
        when(storage.findByEntryId(501L)).thenReturn(changed);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void corruptSourceReferenceMustDisappear() {
        hub.setExt("{bad-json");
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
    
    @Test
    void deletedSourceMustDisappear() {
        when(manager.findMeta("team-a", "find-skills", "skill")).thenReturn(null);
        assertFalse(guard.isIndexable(hub, hubVersion));
    }
}
