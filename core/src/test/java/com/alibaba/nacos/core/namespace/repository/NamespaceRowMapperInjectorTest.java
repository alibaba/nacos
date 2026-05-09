/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.repository;

import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.alibaba.nacos.core.namespace.repository.NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;

class NamespaceRowMapperInjectorTest {
    
    @Test
    void testInitRegistersRowMapper() {
        NamespaceRowMapperInjector injector = new NamespaceRowMapperInjector();
        String key = TENANT_INFO_ROW_MAPPER.getClass().getCanonicalName();
        assertSame(TENANT_INFO_ROW_MAPPER, RowMapperManager.getRowMapper(key));
    }
    
    @Test
    void testTenantInfoRowMapperMapsRow() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString(eq("tenant_id"))).thenReturn("tid-1");
        Mockito.when(rs.getString(eq("tenant_name"))).thenReturn("tenant-name");
        Mockito.when(rs.getString(eq("tenant_desc"))).thenReturn("tenant-desc");
        
        TenantInfo info = TENANT_INFO_ROW_MAPPER.mapRow(rs, 1);
        
        assertEquals("tid-1", info.getTenantId());
        assertEquals("tenant-name", info.getTenantName());
        assertEquals("tenant-desc", info.getTenantDesc());
    }
    
    @Test
    void testTenantInfoRowMapperHandlesNullColumns() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(rs.getString(eq("tenant_id"))).thenReturn(null);
        Mockito.when(rs.getString(eq("tenant_name"))).thenReturn(null);
        Mockito.when(rs.getString(eq("tenant_desc"))).thenReturn(null);
        
        TenantInfo info = TENANT_INFO_ROW_MAPPER.mapRow(rs, 0);
        
        assertEquals(null, info.getTenantId());
        assertEquals(null, info.getTenantName());
        assertEquals(null, info.getTenantDesc());
    }
}
