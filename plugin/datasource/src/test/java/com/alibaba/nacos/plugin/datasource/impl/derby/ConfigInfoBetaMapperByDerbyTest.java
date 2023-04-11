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

public class ConfigInfoBetaMapperByDerbyTest {
    
    private ConfigInfoBetaMapperByDerby configInfoBetaMapperByDerby;
    
    @Before
    public void setUp() throws Exception {
        configInfoBetaMapperByDerby = new ConfigInfoBetaMapperByDerby();
    }
    
    @Test
    public void testUpdateConfigInfo4BetaCas() {
        String sql = configInfoBetaMapperByDerby.updateConfigInfo4BetaCas();
        Assert.assertEquals(sql, "UPDATE config_info_beta SET content = ?,md5 = ?,beta_ips = ?,src_ip = ?,src_user = ?,"
                + "gmt_modified = ?,app_name = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND "
                + "(md5 = ? OR md5 is null OR md5 = '')");
    }
    
    @Test
    public void testFindAllConfigInfoBetaForDumpAllFetchRows() {
        String sql = configInfoBetaMapperByDerby.findAllConfigInfoBetaForDumpAllFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips  FROM "
                        + "(  SELECT id FROM config_info_beta ORDER BY id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY  ) g, "
                        + "config_info_beta t WHERE g.id = t.id");
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configInfoBetaMapperByDerby.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_BETA);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configInfoBetaMapperByDerby.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.DERBY);
    }
}