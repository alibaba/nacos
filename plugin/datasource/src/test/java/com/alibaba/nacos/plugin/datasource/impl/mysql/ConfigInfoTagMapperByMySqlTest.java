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
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.List;

public class ConfigInfoTagMapperByMySqlTest {
    
    private ConfigInfoTagMapperByMySql configInfoTagMapperByMySql;
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String appName = "appName";
    
    String tenantId = "tenantId";
    
    String id = "123";
    
    List<Long> ids = Lists.newArrayList(1L, 2L, 3L, 5L, 144L);
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        configInfoTagMapperByMySql = new ConfigInfoTagMapperByMySql();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.ID, id);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.IDS, ids);
        
    }
    
    @Test
    public void testUpdateConfigInfo4TagCas() {
        String newContent = "new Content";
        String newMD5 = "newMD5";
        String srcIp = "1.1.1.1";
        Object srcUser = "nacos";
        Object time = new Timestamp(System.currentTimeMillis());
        Object appNameTmp = "newAppName";
        Object desc = "description";
        Object use = "use";
        Object effect = "effect";
        Object type = "type";
        Object schema = "schema";
        
        context.putUpdateParameter(FieldConstant.CONTENT, newContent);
        context.putUpdateParameter(FieldConstant.MD5, newMD5);
        context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
        context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, time);
        context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
        context.putUpdateParameter(FieldConstant.C_DESC, desc);
        context.putUpdateParameter(FieldConstant.C_USE, use);
        context.putUpdateParameter(FieldConstant.EFFECT, effect);
        context.putUpdateParameter(FieldConstant.TYPE, type);
        context.putUpdateParameter(FieldConstant.C_SCHEMA, schema);
        
        Object dataId = "dataId";
        Object group = "group";
        Object md5 = "md5";
        Object tagId = "tagId";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TAG_ID, tagId);
        context.putWhereParameter(FieldConstant.MD5, md5);
        
        MapperResult mapperResult = configInfoTagMapperByMySql.updateConfigInfo4TagCas(context);
        
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE config_info_tag SET content = ?, md5 = ?, src_ip = ?,src_user = ?,gmt_modified = ?,"
                        + "app_name = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND tag_id = ? AND "
                        + "(md5 = ? OR md5 IS NULL OR md5 = '')");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(),
                new Object[] {newContent, newMD5, srcIp, srcUser, time, appNameTmp, dataId, group, tenantId, tagId,
                        md5});
    }
    
    @Test
    public void testFindAllConfigInfoTagForDumpAllFetchRows() {
        MapperResult mapperResult = configInfoTagMapperByMySql.findAllConfigInfoTagForDumpAllFetchRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                " SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified  FROM (  "
                        + "SELECT id FROM config_info_tag  ORDER BY id LIMIT " + startRow + "," + pageSize
                        + " ) g, config_info_tag t  WHERE g.id = t.id  ");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configInfoTagMapperByMySql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_TAG);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configInfoTagMapperByMySql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.MYSQL);
    }
}
