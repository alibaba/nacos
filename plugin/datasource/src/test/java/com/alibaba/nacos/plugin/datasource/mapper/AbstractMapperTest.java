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

import com.alibaba.nacos.plugin.datasource.enums.TrustedSqlFunctionEnum;
import com.alibaba.nacos.plugin.datasource.impl.mysql.TenantInfoMapperByMySql;
import com.alibaba.nacos.plugin.datasource.model.ColumnFunctionPair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class AbstractMapperTest {
    
    private AbstractMapper abstractMapper;
    
    @Before
    public void setUp() throws Exception {
        abstractMapper = new TenantInfoMapperByMySql();
    }
    
    @Test
    public void testSelect() {
        String sql = abstractMapper.select(Arrays.asList("id", "name"), Arrays.asList("id"));
        Assert.assertEquals(sql, "SELECT id,name FROM tenant_info WHERE id = ?");
    }

    @Test
    public void testInsert() {
        String sql = abstractMapper.insert(Arrays.asList(
                ColumnFunctionPair.withColumn("id"),
                ColumnFunctionPair.withColumn("name")));
        Assert.assertEquals(sql, "INSERT INTO tenant_info (id, name) VALUES (?, ?)");
    }

    @Test
    public void testInsertWithFunction() {
        String sql = abstractMapper.insert(Arrays.asList(
                ColumnFunctionPair.withColumn("id"),
                ColumnFunctionPair.withColumn("name"),
                ColumnFunctionPair.withColumnAndFunction("gmt_modified", TrustedSqlFunctionEnum.CURRENT_TIMESTAMP)));
        Assert.assertEquals(sql, "INSERT INTO tenant_info (id, name, gmt_modified) VALUES (?, ?, CURRENT_TIMESTAMP)");
    }

    @Test
    public void testUpdate() {
        String sql = abstractMapper.update(Arrays.asList(
                ColumnFunctionPair.withColumn("id"),
                ColumnFunctionPair.withColumn("name")), Arrays.asList("id"));
        Assert.assertEquals(sql, "UPDATE tenant_info SET id = ?, name = ? WHERE id = ?");
    }

    @Test
    public void testUpdateWithFunction() {
        String sql = abstractMapper.update(Arrays.asList(
                        ColumnFunctionPair.withColumn("id"),
                        ColumnFunctionPair.withColumn("name"),
                        ColumnFunctionPair.withColumnAndFunction("gmt_modified", TrustedSqlFunctionEnum.CURRENT_TIMESTAMP)),
                Arrays.asList("id"));
        Assert.assertEquals(sql, "UPDATE tenant_info SET id = ?, name = ?, gmt_modified = CURRENT_TIMESTAMP WHERE id = ?");
    }

    @Test
    public void testDelete() {
        String sql = abstractMapper.delete(Arrays.asList("id"));
        Assert.assertEquals(sql, "DELETE FROM tenant_info WHERE id = ? ");
    }

    @Test
    public void testCount() {
        String sql = abstractMapper.count(Arrays.asList("id"));
        Assert.assertEquals(sql, "SELECT COUNT(*) FROM tenant_info WHERE id = ?");
    }
    
    @Test
    public void testGetPrimaryKeyGeneratedKeys() {
        String[] keys = abstractMapper.getPrimaryKeyGeneratedKeys();
        Assert.assertEquals(keys[0], "id");
    }
    
    @Test
    public void testSelectAll() {
        String sql = abstractMapper.select(Arrays.asList("id", "name"), null);
        Assert.assertEquals(sql, "SELECT id,name FROM tenant_info ");
    }
    
    @Test
    public void testCountAll() {
        String sql = abstractMapper.count(null);
        Assert.assertEquals(sql, "SELECT COUNT(*) FROM tenant_info ");
    }
}