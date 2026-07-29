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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiVisibilityResourceLocatorTest {
    
    @Test
    void findResourceShouldUseDefaultNamespaceAndReturnVisibilityResource() {
        AiResourcePersistService persistService = mock(AiResourcePersistService.class);
        AiResource resource = new AiResource();
        resource.setNamespaceId(DEFAULT_NAMESPACE_ID);
        resource.setName("demo-skill");
        resource.setType("skill");
        when(persistService.find(DEFAULT_NAMESPACE_ID, "demo-skill", "skill"))
            .thenReturn(resource);
        AiVisibilityResourceLocator locator = new AiVisibilityResourceLocator(persistService);
        
        Optional<VisibilityResource> result = locator.findResource("", "skill", "demo-skill");
        
        assertTrue(result.isPresent());
        assertSame(resource, result.get());
        verify(persistService).find(DEFAULT_NAMESPACE_ID, "demo-skill", "skill");
    }
    
    @Test
    void findResourceShouldReturnEmptyWhenResourceDoesNotExist() {
        AiResourcePersistService persistService = mock(AiResourcePersistService.class);
        when(persistService.find("public", "missing-skill", "skill")).thenReturn(null);
        AiVisibilityResourceLocator locator = new AiVisibilityResourceLocator(persistService);
        
        Optional<VisibilityResource> result =
            locator.findResource("public", "skill", "missing-skill");
        
        assertTrue(result.isEmpty());
        verify(persistService).find("public", "missing-skill", "skill");
    }
}
