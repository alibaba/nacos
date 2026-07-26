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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddedRolePersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    private EmbeddedRolePersistServiceImpl embeddedRolePersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        when(databaseOperate.queryOne(any(String.class), any(Object[].class), eq(Integer.class)))
            .thenReturn(0);
        embeddedRolePersistService = new EmbeddedRolePersistServiceImpl(databaseOperate);
    }
    
    @Test
    void testGetRoles() {
        Page<RoleInfo> roles = embeddedRolePersistService.getRoles(1, 10);
        assertNotNull(roles);
    }
    
    @Test
    void testGetRolesByUserName() {
        Page<RoleInfo> page =
            embeddedRolePersistService.getRolesByUserNameAndRoleName("userName", "roleName", 1, 10);
        
        assertNotNull(page);
    }
    
    @Test
    void testAddRole() {
        embeddedRolePersistService.addRole("role", "userName");
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        assertEquals(0, currentSqlContext.size());
    }
    
    @Test
    void testDeleteRole() {
        embeddedRolePersistService.deleteRole("role");
        embeddedRolePersistService.deleteRole("role", "userName");
        
        List<ModifyRequest> currentSqlContext = EmbeddedStorageContextHolder.getCurrentSqlContext();
        
        assertEquals(0, currentSqlContext.size());
    }
    
    @Test
    void testFindRolesLikeRoleName() {
        
        List<String> role = embeddedRolePersistService.findRolesLikeRoleName("role");
        
        assertEquals(0, role.size());
    }
    
    @Test
    void testFindRolesLikeAndGenerateLikeArgument() {
        assertEquals("ro\\_le%", embeddedRolePersistService.generateLikeArgument("ro_le*"));
        assertEquals("plain", embeddedRolePersistService.generateLikeArgument("plain"));
        
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRole("role");
        roleInfo.setUsername("userName");
        Page<RoleInfo> page = new Page<>();
        page.setPageItems(Collections.singletonList(roleInfo));
        page.setTotalCount(1);
        AuthPaginationHelper<RoleInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        EmbeddedRolePersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(page);
        
        assertSame(page, service.findRolesLike4Page("user*", "ro_le*", 1, 10));
        assertSame(page, service.findRolesLike4Page("", "", 1, 10));
        assertSame(page, service.getRolesByUserNameAndRoleName("", "", 1, 10));
    }
    
    @Test
    void testGetRolesReturnsEmptyPageWhenHelperReturnsNull() {
        AuthPaginationHelper<RoleInfo> helper = Mockito.mock(AuthPaginationHelper.class);
        EmbeddedRolePersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(null);
        
        Page<RoleInfo> result = service.getRoles(1, 10);
        
        assertEquals(0, result.getTotalCount());
        assertEquals(Collections.emptyList(), result.getPageItems());
    }
    
    private EmbeddedRolePersistServiceImpl serviceWithHelper(
        AuthPaginationHelper<RoleInfo> helper) {
        return new EmbeddedRolePersistServiceImpl(databaseOperate) {
            
            @Override
            @SuppressWarnings("unchecked")
            public <E> AuthPaginationHelper<E> createPaginationHelper() {
                return (AuthPaginationHelper<E>) helper;
            }
        };
    }
}
