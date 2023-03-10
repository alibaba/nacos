/*
 *   Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigInfoAggrMapperByMySqlTest {
    
    private ConfigInfoAggrMapperByMySql configInfoAggrMapperByMySql;
    
    @Before
    public void setUp() throws Exception {
        configInfoAggrMapperByMySql = new ConfigInfoAggrMapperByMySql();
    }
    
    @Test
    public void testBatchRemoveAggr() {
        String sql = configInfoAggrMapperByMySql.batchRemoveAggr(5);
        Assert.assertEquals(sql, "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                + "AND datum_id IN (?, ?, ?, ?, ?)");
    }
    
    @Test
    public void testAggrConfigInfoCount() {
        String sql = configInfoAggrMapperByMySql.aggrConfigInfoCount(5, true);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                        + "AND datum_id IN (?, ?, ?, ?, ?)");
    }
    
    @Test
    public void testFindConfigInfoAggrIsOrdered() {
        String sql = configInfoAggrMapperByMySql.findConfigInfoAggrIsOrdered();
        Assert.assertEquals(sql, "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id");
    }
    
    @Test
    public void testFindConfigInfoAggrByPageFetchRows() {
        String sql = configInfoAggrMapperByMySql.findConfigInfoAggrByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE "
                        + "data_id= ? AND group_id= ? AND tenant_id= ? ORDER BY datum_id LIMIT 0,5");
    }
    
    @Test
    public void testFindAllAggrGroupByDistinct() {
        String sql = configInfoAggrMapperByMySql.findAllAggrGroupByDistinct();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr");
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configInfoAggrMapperByMySql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_AGGR);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configInfoAggrMapperByMySql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.MYSQL);
    }
}