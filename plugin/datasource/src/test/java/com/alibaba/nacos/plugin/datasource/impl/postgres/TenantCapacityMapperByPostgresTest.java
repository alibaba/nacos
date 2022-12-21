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

package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TenantCapacityMapperByPostgresTest {
    
    private TenantCapacityMapperByPostgres tenantCapacityMapperByPostgres;
    
    @Before
    public void setUp() throws Exception {
        tenantCapacityMapperByPostgres = new TenantCapacityMapperByPostgres();
    }
    
    @Test
    public void testGetTableName() {
        String tableName = tenantCapacityMapperByPostgres.getTableName();
        Assert.assertEquals(tableName, TableConstant.TENANT_CAPACITY);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = tenantCapacityMapperByPostgres.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
    
    @Test
    public void testIncrementUsageWithDefaultQuotaLimit() {
        String sql = tenantCapacityMapperByPostgres.incrementUsageWithDefaultQuotaLimit();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` <"
                        + " ? AND quota = 0");
    }
    
    @Test
    public void testIncrementUsageWithQuotaLimit() {
        String sql = tenantCapacityMapperByPostgres.incrementUsageWithQuotaLimit();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < "
                        + "quota AND quota != 0");
    }
    
    @Test
    public void testIncrementUsage() {
        String sql = tenantCapacityMapperByPostgres.incrementUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?");
    }
    
    @Test
    public void testDecrementUsage() {
        String sql = tenantCapacityMapperByPostgres.decrementUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0");
    }
    
    @Test
    public void testCorrectUsage() {
        String sql = tenantCapacityMapperByPostgres.correctUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                        + "gmt_modified = ? WHERE tenant_id = ?");
    }
    
    @Test
    public void testGetCapacityList4CorrectUsage() {
        String sql = tenantCapacityMapperByPostgres.getCapacityList4CorrectUsage();
        Assert.assertEquals(sql, "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?");
    }
    
    @Test
    public void testInsertTenantCapacity() {
        String sql = tenantCapacityMapperByPostgres.insertTenantCapacity();
        Assert.assertEquals(sql,
                "INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;");
    }
}