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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTagsRelationMapperByMySqlTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String[] tagArr = new String[] {"tag1", "tag3", "tag2", "tag4", "tag5"};
    
    MapperContext context;
    
    private ConfigTagsRelationMapperByMySql configTagsRelationMapperByMySql;
    
    @BeforeEach
    void setUp() throws Exception {
        configTagsRelationMapperByMySql = new ConfigTagsRelationMapperByMySql();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageCountRows(context);
        assertEquals(mapperResult.getSql(), "SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE "
                + "a.tenant_id LIKE ?  AND "
                + " ( b.tag_name LIKE ?  OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  ) ");
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfo4PageCountRows(context);
        assertEquals(mapperResult.getSql(), "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE  a.tenant_id=?  AND b.tag_name IN (?, ?, ?, ?, ?) ");
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfo4PageFetchRows(context);
        String sql = mapperResult.getSql();
        
        // 验证 SQL 包含子查询结构，确保能返回完整的标签信息
        assertEquals(true, sql.contains("SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,"
                + "c.md5,c.type,c.encrypted_data_key,c.c_desc"));
        assertEquals(true, sql.contains("GROUP_CONCAT(DISTINCT d.tag_name SEPARATOR ',') as config_tags"));
        assertEquals(true, sql.contains("FROM ("));  // 子查询
        assertEquals(true, sql.contains("SELECT DISTINCT a.id"));  // 内层查询
        assertEquals(true, sql.contains("LEFT JOIN config_tags_relation d ON c.id=d.id"));  // 外层关联获取所有标签
        assertEquals(true, sql.contains("b.tag_name IN"));  // 内层标签筛选条件
        
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRowss() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE a.tenant_id LIKE ?  AND a.data_id LIKE ?  AND a.group_id LIKE ?  AND a.app_name = ?  "
                + "AND a.content LIKE ?  AND "
                + " ( b.tag_name LIKE ?  OR b.tag_name LIKE ? "
                + " OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  ) ", mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void tsetFindConfigInfoLike4PageFetchRows() {
        context.putWhereParameter(FieldConstant.DATA_ID, "dataID1");
        context.putWhereParameter(FieldConstant.GROUP_ID, "groupID1");
        context.putWhereParameter(FieldConstant.APP_NAME, "AppName1");
        context.putWhereParameter(FieldConstant.CONTENT, "Content1");
        MapperResult mapperResult = configTagsRelationMapperByMySql.findConfigInfoLike4PageFetchRows(context);
        String sql = mapperResult.getSql();
        
        // 验证 SQL 包含子查询结构，确保能返回完整的标签信息
        assertEquals(true, sql.contains("SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,"
                + "c.md5,c.encrypted_data_key,c.type,c.c_desc"));
        assertEquals(true, sql.contains("GROUP_CONCAT(DISTINCT d.tag_name SEPARATOR ',') as config_tags"));
        assertEquals(true, sql.contains("FROM ("));  // 子查询
        assertEquals(true, sql.contains("SELECT DISTINCT a.id"));  // 内层查询
        assertEquals(true, sql.contains("LEFT JOIN config_tags_relation d ON c.id=d.id"));  // 外层关联获取所有标签
        assertEquals(true, sql.contains("b.tag_name LIKE"));  // 内层标签筛选条件
        assertEquals(true, sql.contains("LIKE"));
        
        List<Object> list = CollectionUtils.list(tenantId);
        list.add("dataID1");
        list.add("groupID1");
        list.add("AppName1");
        list.add("Content1");
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configTagsRelationMapperByMySql.getTableName();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configTagsRelationMapperByMySql.getDataSource();
        assertEquals(DataSourceConstant.MYSQL, dataSource);
    }
}
