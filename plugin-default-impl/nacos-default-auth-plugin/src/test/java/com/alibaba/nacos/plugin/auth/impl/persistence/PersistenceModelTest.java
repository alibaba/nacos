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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceModelTest {
    
    @Test
    void testRoleInfoAccessorsAndToString() {
        RoleInfo roleInfo = new RoleInfo();
        
        roleInfo.setRole("ROLE_ADMIN");
        roleInfo.setUsername("nacos");
        
        assertEquals("ROLE_ADMIN", roleInfo.getRole());
        assertEquals("nacos", roleInfo.getUsername());
        assertEquals("RoleInfo{role='ROLE_ADMIN', username='nacos'}", roleInfo.toString());
    }
    
    @Test
    void testPermissionInfoAccessors() {
        PermissionInfo permissionInfo = new PermissionInfo();
        
        permissionInfo.setRole("ROLE_ADMIN");
        permissionInfo.setResource("public:*:*");
        permissionInfo.setAction("rw");
        
        assertEquals("ROLE_ADMIN", permissionInfo.getRole());
        assertEquals("public:*:*", permissionInfo.getResource());
        assertEquals("rw", permissionInfo.getAction());
    }
}
