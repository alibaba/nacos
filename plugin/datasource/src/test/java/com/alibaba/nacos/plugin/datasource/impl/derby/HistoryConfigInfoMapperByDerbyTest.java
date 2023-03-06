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

public class HistoryConfigInfoMapperByDerbyTest {
    
    private HistoryConfigInfoMapperByDerby historyConfigInfoMapperByDerby;
    
    @Before
    public void setUp() throws Exception {
        historyConfigInfoMapperByDerby = new HistoryConfigInfoMapperByDerby();
    }
    
    @Test
    public void testRemoveConfigHistory() {
        String sql = historyConfigInfoMapperByDerby.removeConfigHistory();
        Assert.assertEquals(sql,
                "DELETE FROM his_config_info WHERE id IN( SELECT id FROM his_config_info WHERE gmt_modified < ? "
                        + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY)");
    }
    
    @Test
    public void testFindConfigHistoryCountByTime() {
        String sql = historyConfigInfoMapperByDerby.findConfigHistoryCountByTime();
        Assert.assertEquals(sql, "SELECT count(*) FROM his_config_info WHERE gmt_modified < ?");
    }
    
    @Test
    public void testFindDeletedConfig() {
        String sql = historyConfigInfoMapperByDerby.findDeletedConfig();
        Assert.assertEquals(sql,
                "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
                        + "gmt_modified >= ? AND gmt_modified <= ?");
    }
    
    @Test
    public void testFindConfigHistoryFetchRows() {
        String sql = historyConfigInfoMapperByDerby.findConfigHistoryFetchRows();
        Assert.assertEquals(sql,
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC");
    }
    
    @Test
    public void testDetailPreviousConfigHistory() {
        String sql = historyConfigInfoMapperByDerby.detailPreviousConfigHistory();
        Assert.assertEquals(sql,
                "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,"
                        + "gmt_modified FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)");
    }
    
    @Test
    public void testGetTableName() {
        String tableName = historyConfigInfoMapperByDerby.getTableName();
        Assert.assertEquals(tableName, TableConstant.HIS_CONFIG_INFO);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByDerby.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.DERBY);
    }
}