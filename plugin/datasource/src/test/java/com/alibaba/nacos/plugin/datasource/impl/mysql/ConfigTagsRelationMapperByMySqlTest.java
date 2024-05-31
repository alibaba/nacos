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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ConfigTagsRelationMapperByMySqlTest {
    
    private ConfigTagsRelationMapperByMySql configTagsRelationMapperByMySql;
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String[] tagArr = new String[] {"tag1", "tag3", "tag2", "tag4", "tag5"};
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        configTagsRelationMapperByMySql = new ConfigTagsRelationMapperByMySql();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
    }
    
    @Test
    public void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageCountRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id  WHERE  "
                        + "a.tenant_id LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?) ");
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    public void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfo4PageCountRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id "
                        + "WHERE  a.tenant_id=?  AND b.tag_name IN (?, ?, ?, ?, ?) ");
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    public void testFindConfigInfo4PageFetchRows() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfo4PageFetchRows(context);
        Assert.assertEquals(
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  "
                        + "a LEFT JOIN config_tags_relation b ON a.id=b.id "
                        + "WHERE  a.tenant_id=?  AND a.data_id=?  AND a.group_id=?  AND a.app_name=?  AND a.content LIKE ? "
                        + " AND b.tag_name IN (?, ?, ?, ?, ?)  LIMIT "
                        + startRow + "," + pageSize, mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    public void testFindConfigInfoLike4PageCountRowss() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageCountRows(context);
        Assert.assertEquals("SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id  "
                + "WHERE  a.tenant_id LIKE ?  AND a.data_id LIKE ?  AND a.group_id LIKE ?  AND a.app_name = ?  "
                + "AND a.content LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?) ", mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    public void tsetFindConfigInfoLike4PageFetchRows() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageFetchRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info a LEFT JOIN"
                        + " config_tags_relation b ON a.id=b.id  WHERE  a.tenant_id LIKE ?  AND a.data_id LIKE ?  "
                        + "AND a.group_id LIKE ?  AND a.app_name = ?  AND a.content LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?)  LIMIT "
                        + startRow + "," + pageSize);
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    public void testGetTableName() {
        String tableName = configTagsRelationMapperByMySql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_TAGS_RELATION);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = configTagsRelationMapperByMySql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.MYSQL);
    }
}
