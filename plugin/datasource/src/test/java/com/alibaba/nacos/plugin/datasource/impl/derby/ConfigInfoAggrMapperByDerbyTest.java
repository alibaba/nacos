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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigInfoAggrMapperByDerbyTest {
    
    private ConfigInfoAggrMapperByDerby configInfoAggrMapperByDerby;
    
    @Before
    public void setUp() throws Exception {
        this.configInfoAggrMapperByDerby = new ConfigInfoAggrMapperByDerby();
    }
    
    @Test
    public void testBatchRemoveAggr() {
        String sql = configInfoAggrMapperByDerby.batchRemoveAggr(5);
        Assert.assertEquals(sql, "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                + "AND datum_id IN (?, ?, ?, ?, ?)");
    }
    
    @Test
    public void testAggrConfigInfoCount() {
        String sql = configInfoAggrMapperByDerby.aggrConfigInfoCount(5, true);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                        + "AND datum_id IN (?, ?, ?, ?, ?)");
    }
    
    @Test
    public void testFindConfigInfoAggrIsOrdered() {
        String sql = configInfoAggrMapperByDerby.findConfigInfoAggrIsOrdered();
        Assert.assertEquals(sql, "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id");
    }
    
    @Test
    public void testFindConfigInfoAggrByPageFetchRows() {
        String sql = configInfoAggrMapperByDerby.findConfigInfoAggrByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE "
                        + "data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindAllAggrGroupByDistinct() {
        String sql = configInfoAggrMapperByDerby.findAllAggrGroupByDistinct();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr");
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configInfoAggrMapperByDerby.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_AGGR);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configInfoAggrMapperByDerby.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.DERBY);
    }
}