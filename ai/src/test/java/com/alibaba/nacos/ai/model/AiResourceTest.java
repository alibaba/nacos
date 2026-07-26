/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.model;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link AiResource} unit test focusing on VisibilityResource integration.
 *
 * @author xiweng.yy
 */
class AiResourceTest {
    
    @Test
    void testExtendsVisibilityResource() {
        AiResource resource = new AiResource();
        assertInstanceOf(VisibilityResource.class, resource);
    }
    
    @Test
    void testDefaultScopeIsPrivate() {
        AiResource resource = new AiResource();
        assertEquals(VisibilityConstants.SCOPE_PRIVATE, resource.getScope());
    }
    
    @Test
    void testDefaultOwnerIsEmpty() {
        AiResource resource = new AiResource();
        assertEquals("", resource.getOwner());
    }
    
    @Test
    void testGetResourceNameDelegatesToName() {
        AiResource resource = new AiResource();
        resource.setName("my-skill");
        assertEquals("my-skill", resource.getResourceName());
    }
    
    @Test
    void testGetResourceTypeDelegatesToType() {
        AiResource resource = new AiResource();
        resource.setType("skill");
        assertEquals("skill", resource.getResourceType());
    }
    
    @Test
    void testGetNamespaceIdDelegatesToField() {
        AiResource resource = new AiResource();
        resource.setNamespaceId("public");
        assertEquals("public", resource.getNamespaceId());
    }
    
    @Test
    void testScopeAndOwnerGetterSetter() {
        AiResource resource = new AiResource();
        resource.setScope(VisibilityConstants.SCOPE_PUBLIC);
        resource.setOwner("alice");
        assertEquals(VisibilityConstants.SCOPE_PUBLIC, resource.getScope());
        assertEquals("alice", resource.getOwner());
    }
    
    @Test
    void testFromGetterSetter() {
        AiResource resource = new AiResource();
        resource.setFrom("sync");
        assertEquals("sync", resource.getFrom());
    }
}
