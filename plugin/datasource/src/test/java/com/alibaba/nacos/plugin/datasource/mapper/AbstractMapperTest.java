/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantInfoMapperByMySql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractMapperTest {
    
    private AbstractMapper abstractMapper;
    
    @BeforeEach
    void setUp() throws Exception {
        abstractMapper = new TenantInfoMapperByMySql();
    }
    
    @Test
    void testSelect() {
        String sql = abstractMapper.select(Arrays.asList("id", "name"), Arrays.asList("id"));
        assertEquals("SELECT id,name FROM tenant_info WHERE id = ?", sql);
    }
    
    @Test
    void testInsert() {
        String sql = abstractMapper.insert(Arrays.asList("id", "name"));
        assertEquals("INSERT INTO tenant_info(id, name) VALUES(?,?)", sql);
    }
    
    @Test
    void testUpdate() {
        String sql = abstractMapper.update(Arrays.asList("id", "name"), Arrays.asList("id"));
        assertEquals("UPDATE tenant_info SET id = ?,name = ? WHERE id = ?", sql);
    }
    
    @Test
    void testDelete() {
        String sql = abstractMapper.delete(Arrays.asList("id"));
        assertEquals("DELETE FROM tenant_info WHERE id = ? ", sql);
    }
    
    @Test
    void testCount() {
        String sql = abstractMapper.count(Arrays.asList("id"));
        assertEquals("SELECT COUNT(*) FROM tenant_info WHERE id = ?", sql);
    }
    
    @Test
    void testGetPrimaryKeyGeneratedKeys() {
        String[] keys = abstractMapper.getPrimaryKeyGeneratedKeys();
        assertEquals("id", keys[0]);
    }
    
    @Test
    void testSelectAll() {
        String sql = abstractMapper.select(Arrays.asList("id", "name"), null);
        assertEquals("SELECT id,name FROM tenant_info ", sql);
    }
    
    @Test
    void testCountAll() {
        String sql = abstractMapper.count(null);
        assertEquals("SELECT COUNT(*) FROM tenant_info ", sql);
    }
}
