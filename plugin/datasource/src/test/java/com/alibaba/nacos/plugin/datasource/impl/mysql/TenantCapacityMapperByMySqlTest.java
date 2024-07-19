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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantCapacityMapperByMySqlTest {
    
    String tenantId = "tenantId";
    
    MapperContext context;
    
    private TenantCapacityMapperByMySql tenantCapacityMapperByMySql;
    
    private Object modified = new Timestamp(System.currentTimeMillis());
    
    private Object oldModified = new Timestamp(System.currentTimeMillis());
    
    private Object usage = 1;
    
    @BeforeEach
    void setUp() throws Exception {
        tenantCapacityMapperByMySql = new TenantCapacityMapperByMySql();
        context = new MapperContext();
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modified);
        context.putWhereParameter(FieldConstant.GMT_MODIFIED, oldModified);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.USAGE, usage);
        
    }
    
    @Test
    void testGetTableName() {
        String tableName = tenantCapacityMapperByMySql.getTableName();
        assertEquals(TableConstant.TENANT_CAPACITY, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = tenantCapacityMapperByMySql.getDataSource();
        assertEquals(DataSourceConstant.MYSQL, dataSource);
    }
    
    @Test
    void testIncrementUsageWithDefaultQuotaLimit() {
        MapperResult mapperResult = tenantCapacityMapperByMySql.incrementUsageWithDefaultQuotaLimit(context);
        assertEquals(mapperResult.getSql(),
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage <" + " ? AND quota = 0");
        assertArrayEquals(new Object[] {modified, tenantId, usage}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testIncrementUsageWithQuotaLimit() {
        MapperResult mapperResult = tenantCapacityMapperByMySql.incrementUsageWithQuotaLimit(context);
        assertEquals(mapperResult.getSql(),
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage < " + "quota AND quota != 0");
        assertArrayEquals(new Object[] {modified, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testIncrementUsage() {
        MapperResult mapperResult = tenantCapacityMapperByMySql.incrementUsage(context);
        assertEquals("UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {modified, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testDecrementUsage() {
        MapperResult mapperResult = tenantCapacityMapperByMySql.decrementUsage(context);
        assertEquals("UPDATE tenant_capacity SET usage = usage - 1, gmt_modified = ? WHERE tenant_id = ? AND usage > 0",
                mapperResult.getSql());
        assertArrayEquals(new Object[] {modified, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testCorrectUsage() {
        MapperResult mapperResult = tenantCapacityMapperByMySql.correctUsage(context);
        assertEquals(mapperResult.getSql(), "UPDATE tenant_capacity SET usage = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                + "gmt_modified = ? WHERE tenant_id = ?");
        assertArrayEquals(new Object[] {tenantId, modified, tenantId}, mapperResult.getParamList().toArray());
        
    }
    
    @Test
    void testGetCapacityList4CorrectUsage() {
        Object id = 1;
        Object limit = 10;
        context.putWhereParameter(FieldConstant.ID, id);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limit);
        MapperResult mapperResult = tenantCapacityMapperByMySql.getCapacityList4CorrectUsage(context);
        assertEquals("SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {id, limit}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testInsertTenantCapacity() {
        Object group = "group";
        Object quota = "quota";
        Object maxAggrSize = 10;
        Object maxAggrCount = 3;
        Object maxSize = 1;
        Object createTime = new Timestamp(System.currentTimeMillis());
        
        context.putUpdateParameter(FieldConstant.TENANT_ID, tenantId);
        context.putUpdateParameter(FieldConstant.GROUP_ID, group);
        context.putUpdateParameter(FieldConstant.QUOTA, quota);
        context.putUpdateParameter(FieldConstant.MAX_SIZE, maxSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, maxAggrSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, maxAggrCount);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modified);
        
        context.putUpdateParameter(FieldConstant.GMT_CREATE, createTime);
        
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        
        MapperResult mapperResult = tenantCapacityMapperByMySql.insertTenantCapacity(context);
        assertEquals(mapperResult.getSql(),
                "INSERT INTO tenant_capacity (tenant_id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;");
        assertArrayEquals(new Object[] {tenantId, quota, maxSize, maxAggrCount, maxAggrSize, createTime, modified, tenantId},
                mapperResult.getParamList().toArray());
    }
}
