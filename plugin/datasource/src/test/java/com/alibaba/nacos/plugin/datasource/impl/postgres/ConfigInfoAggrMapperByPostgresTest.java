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

package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class ConfigInfoAggrMapperByPostgresTest {
    
    private ConfigInfoAggrMapperByPostgres configInfoAggrMapperByPostgres;
    
    @Before
    public void setUp() throws Exception {
        configInfoAggrMapperByPostgres = new ConfigInfoAggrMapperByPostgres();
    }
    
    @Test
    public void testBatchRemoveAggr() {
        String sql = configInfoAggrMapperByPostgres.batchRemoveAggr(Arrays.asList("1", "2"));
        Assert.assertEquals(sql, "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                + "AND datum_id IN ('1','2')");
    }
    
    @Test
    public void testAggrConfigInfoCount() {
        String sql = configInfoAggrMapperByPostgres.aggrConfigInfoCount(5, true);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                        + "AND datum_id IN (?, ?, ?, ?, ?)");
    }
    
    @Test
    public void testFindConfigInfoAggrIsOrdered() {
        String sql = configInfoAggrMapperByPostgres.findConfigInfoAggrIsOrdered();
        Assert.assertEquals(sql, "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id");
    }
    
    @Test
    public void testFindConfigInfoAggrByPageFetchRows() {
        String sql = configInfoAggrMapperByPostgres.findConfigInfoAggrByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE "
                        + "data_id= ? AND group_id= ? AND tenant_id= ? ORDER BY datum_id LIMIT 5 OFFSET 0");
    }
    
    @Test
    public void testFindAllAggrGroupByDistinct() {
        String sql = configInfoAggrMapperByPostgres.findAllAggrGroupByDistinct();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr");
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configInfoAggrMapperByPostgres.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_AGGR);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configInfoAggrMapperByPostgres.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}