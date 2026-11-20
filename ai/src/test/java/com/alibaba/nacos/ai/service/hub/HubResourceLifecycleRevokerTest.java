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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Revocation decisions are evaluated again after source work and after every lost CAS. */
class HubResourceLifecycleRevokerTest {
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void sourceMutationCannotStartWithAnActiveShare(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        assertEquals("done", f.revoker.around(f.source, null, () -> {
            assertEquals("disable", f.entry.getStatus());
            return "done";
        }));
        assertEquals(2L, f.entry.getMetaVersion());
        assertEquals("reference-is-preserved", f.entry.getDesc());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void shareCreatedDuringSourceOperationIsRevokedBySecondFence(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        f.visible = false;
        f.revoker.around(f.source, "1.0.0", () -> {
            f.visible = true;
            return null;
        });
        assertEquals("disable", f.entry.getStatus());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void adjustmentToUnaffectedVersionWinsLostRevocationCas(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        when(f.storage.updateMetaCas(eq(type), any(), anyLong(), any())).thenAnswer(call -> {
            f.entry.setExt(HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
                "source", 101L, "same-name", 202L, "2.0.0")));
            f.entry.setMetaVersion(2L);
            return false;
        });
        assertFalse(f.revoker.revoke(f.source, "1.0.0"));
        assertEquals("enable", f.entry.getStatus());
        assertEquals("2.0.0",
            HubResourceReferenceJsonUtil.parse(f.entry.getExt()).getSourceVersion());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void exhaustedPreFenceDoesNotExecuteSourceMutation(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        when(f.storage.updateMetaCas(eq(type), any(), anyLong(), any())).thenReturn(false);
        AtomicBoolean called = new AtomicBoolean();
        assertEquals(NacosException.CONFLICT, assertThrows(NacosException.class,
            () -> f.revoker.around(f.source, null, () -> {
                called.set(true);
                return null;
            })).getErrCode());
        assertFalse(called.get());
        assertEquals("enable", f.entry.getStatus());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void failedSourceOperationKeepsItsErrorAndAttemptsFinalRevocation(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        f.visible = false;
        NacosException original = new NacosException(NacosException.SERVER_ERROR, "source failed");
        assertSame(original, assertThrows(NacosException.class,
            () -> f.revoker.around(f.source, null, () -> {
                f.visible = true;
                throw original;
            })));
        assertEquals("disable", f.entry.getStatus());
    }
    
    @Test
    void skillKeepsExistingRevocationPathAndUnknownTypesHaveNoSideEffects() throws Exception {
        HubEntryStorage storage = mock(HubEntryStorage.class);
        HubResourceLifecycleRevoker revoker = new HubResourceLifecycleRevoker(storage);
        AiResource source = new AiResource();
        source.setType("skill");
        assertTrue(revoker.around(source, null, () -> true));
        source.setType("future-type");
        assertTrue(revoker.around(source, null, () -> true));
        verifyNoInteractions(storage);
    }
    
    private static final class Fixture {
        
        private final HubEntryStorage storage = mock(HubEntryStorage.class);
        
        private final HubResourceLifecycleRevoker revoker =
            new HubResourceLifecycleRevoker(storage);
        
        private final AiResource source = new AiResource();
        
        private final AiResource entry = new AiResource();
        
        private boolean visible = true;
        
        private Fixture(HubResourceType type) throws Exception {
            source.setId(101L);
            source.setNamespaceId("source");
            source.setName("same-name");
            source.setType(type.resourceType());
            entry.setId(501L);
            entry.setStatus("enable");
            entry.setMetaVersion(1L);
            entry.setDesc("reference-is-preserved");
            entry.setExt(HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
                "source", 101L, "same-name", 201L, "1.0.0")));
            when(storage.findByInternalName(type,
                HubResourceNameUtils.buildInternalName("same-name", 101L)))
                .thenAnswer(call -> visible
                    ? JacksonUtils.toObj(JacksonUtils.toJson(entry), AiResource.class) : null);
            when(storage.updateMetaCas(eq(type), any(), anyLong(), any())).thenAnswer(call -> {
                long expected = call.getArgument(2);
                if (entry.getMetaVersion() != expected) {
                    return false;
                }
                AiResource next = call.getArgument(3);
                entry.setStatus(next.getStatus());
                entry.setMetaVersion(expected + 1);
                return true;
            });
        }
    }
}
