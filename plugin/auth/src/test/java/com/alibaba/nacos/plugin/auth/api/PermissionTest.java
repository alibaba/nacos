/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class PermissionTest {
    
    private Permission permission;
    
    @Before
    public void setUp() throws Exception {
        permission = new Permission(Resource.EMPTY_RESOURCE, ActionTypes.WRITE.toString());
    }
    
    @Test
    public void testToString() {
        assertEquals(
                "Permission{resource='Resource{namespaceId='', group='', name='', type='', properties=null}', action='w'}",
                permission.toString());
    }
    
    @Test
    public void testSetResource() {
        Permission permission = new Permission();
        Properties properties = new Properties();
        Resource resource = new Resource("NS", "G", "N", "TEST", properties);
        permission.setResource(resource);
        assertEquals("NS", permission.getResource().getNamespaceId());
        assertEquals("G", permission.getResource().getGroup());
        assertEquals("N", permission.getResource().getName());
        assertEquals("TEST", permission.getResource().getType());
        assertEquals(properties, permission.getResource().getProperties());
    }
    
    @Test
    public void testSetAction() {
        Permission permission = new Permission();
        permission.setAction(ActionTypes.READ.toString());
        assertEquals(ActionTypes.READ.toString(), permission.getAction());
    }
}
