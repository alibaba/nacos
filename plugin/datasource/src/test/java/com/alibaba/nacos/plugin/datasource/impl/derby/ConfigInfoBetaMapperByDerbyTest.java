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
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ConfigInfoBetaMapperByDerbyTest {
    
    private ConfigInfoBetaMapperByDerby configInfoBetaMapperByDerby;
    
    @Before
    public void setUp() throws Exception {
        configInfoBetaMapperByDerby = new ConfigInfoBetaMapperByDerby();
    }
    
    @Test
    public void testUpdateConfigInfo4BetaCas() {
        MapperContext context = new MapperContext();
        context.putUpdateParameter("content", "content");
        context.putUpdateParameter("md5", "md5-update");
        context.putUpdateParameter("beta_ips", "beta_ips");
        context.putUpdateParameter("src_ip", "src_ip");
        context.putUpdateParameter("src_user", "src_user");
        context.putUpdateParameter("gmt_modified", "gmt_modified");
        context.putUpdateParameter("app_name", "app_name");
    
        context.putWhereParameter("data_id", "data_id");
        context.putWhereParameter("group_id", "group_id");
        context.putWhereParameter("tenant_id", "tenant_id");
        context.putWhereParameter("md5", "md5-where");
    
        MapperResult mapperResult = configInfoBetaMapperByDerby.updateConfigInfo4BetaCas(context);
        
        String sql = mapperResult.getSql();
        List<Object> paramList = mapperResult.getParamList();
        Assert.assertEquals(sql, "UPDATE config_info_beta SET content = ?,md5 = ?,beta_ips = ?,src_ip = ?,src_user = ?,"
                + "gmt_modified = ?,app_name = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND "
                + "(md5 = ? OR md5 is null OR md5 = '')");
        Assert.assertEquals(paramList, Arrays.asList("content", "md5-update", "beta_ips", "src_ip",
                "src_user", "gmt_modified", "app_name", "data_id", "group_id", "tenant_id", "md5-where"));
    }
    
    @Test
    public void testFindAllConfigInfoBetaForDumpAllFetchRows() {
        MapperContext context = new MapperContext();
        context.setStartRow(0);
        context.setPageSize(5);
        MapperResult result = configInfoBetaMapperByDerby.findAllConfigInfoBetaForDumpAllFetchRows(context);
        String sql = result.getSql();
        List<Object> paramList = result.getParamList();
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips  FROM "
                        + "(  SELECT id FROM config_info_beta ORDER BY id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY  ) g, "
                        + "config_info_beta t WHERE g.id = t.id");
        Assert.assertEquals(paramList, Arrays.asList(0, 5));
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