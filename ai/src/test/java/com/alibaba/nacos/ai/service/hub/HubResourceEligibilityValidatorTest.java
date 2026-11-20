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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Common publication boundaries are verified independently for all five resource types. */
class HubResourceEligibilityValidatorTest {
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void exactPublishedVersionIsPinnedAndReplacementIsRejected(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        HubResourceReference pinned = f.validator.requireShareableVersion(type, f.source, "1.0.0");
        assertEquals(101L, pinned.getSourceResourceId());
        assertEquals(201L, pinned.getSourceVersionId());
        assertSame(f.source, f.validator.requirePinnedSource(type, pinned));
        f.version.setId(202L);
        assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
        f.version.setId(201L);
        f.source.setId(102L);
        assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void sourceDisablePrivateAndEveryNonOnlineStateInvalidatePinnedReference(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        HubResourceReference pinned = f.validator.requireShareableVersion(type, f.source, "1.0.0");
        f.source.setStatus("disable");
        assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
        f.source.setStatus("enable");
        f.source.setScope("PRIVATE");
        if (type == HubResourceType.PROMPT) {
            assertSame(f.source, f.validator.requirePinnedSource(type, pinned));
        } else {
            assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
        }
        f.source.setScope("PUBLIC");
        for (String state : new String[] {"draft", "reviewing", "reviewed", "offline"}) {
            f.version.setStatus(state);
            assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
        }
        f.version.setStatus("online");
        when(f.manager.findMeta("source", "same-name", type.resourceType())).thenReturn(null);
        assertThrows(NacosException.class, () -> f.validator.requirePinnedSource(type, pinned));
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void namespaceTypeAndExactVersionAreNeverInferred(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        assertThrows(NacosException.class, () -> f.validator.requireSource(type,
            AiConstants.Hub.NAMESPACE_ID, "same-name", 101L));
        for (String version : new String[] {null, "", "latest", "LATEST", " 1.0.0"}) {
            assertThrows(NacosException.class,
                () -> f.validator.requireShareableVersion(type, f.source, version));
        }
        f.version.setType("different-type");
        assertThrows(NacosException.class,
            () -> f.validator.requireShareableVersion(type, f.source, "1.0.0"));
        f.source.setType("different-type");
        assertThrows(NacosException.class,
            () -> f.validator.requireSource(type, "source", "same-name", 101L));
    }
    
    @ParameterizedTest
    @EnumSource(HubResourceType.class)
    void malformedSourceDoesNotReachPersistence(HubResourceType type) {
        AiResourceManager manager = mock(AiResourceManager.class);
        HubResourceEligibilityValidator validator = new HubResourceEligibilityValidator(manager);
        assertThrows(NacosException.class, () -> validator.requireSource(type, "source", " ", 1L));
        assertThrows(NacosException.class,
            () -> validator.requireSource(type, "source", "same-name", -1L));
        assertThrows(NacosException.class,
            () -> validator.requireShareableVersion(type, null, "1.0.0"));
        verifyNoInteractions(manager);
    }
    
    private static final class Fixture {
        
        private final AiResourceManager manager = mock(AiResourceManager.class);
        
        private final HubResourceEligibilityValidator validator =
            new HubResourceEligibilityValidator(manager);
        
        private final AiResource source = new AiResource();
        
        private final AiResourceVersion version = new AiResourceVersion();
        
        private Fixture(HubResourceType type) {
            source.setId(101L);
            source.setNamespaceId("source");
            source.setName("same-name");
            source.setType(type.resourceType());
            source.setScope("PUBLIC");
            source.setStatus("enable");
            version.setId(201L);
            version.setNamespaceId("source");
            version.setName("same-name");
            version.setType(type.resourceType());
            version.setVersion("1.0.0");
            version.setStatus("online");
            when(manager.findMeta("source", "same-name", type.resourceType())).thenReturn(source);
            when(manager.findVersion("source", "same-name", type.resourceType(), "1.0.0"))
                .thenReturn(version);
        }
    }
}
