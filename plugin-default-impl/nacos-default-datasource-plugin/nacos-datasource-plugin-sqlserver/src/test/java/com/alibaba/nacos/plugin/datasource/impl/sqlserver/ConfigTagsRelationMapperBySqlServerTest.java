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

package com.alibaba.nacos.plugin.datasource.impl.sqlserver;

import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTagsRelationMapperBySqlServerTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String appName = "appName";
    
    String content = "content";
    
    String tag = "tag";
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigTagsRelationMapperBySqlServer configTagsRelationMapperBySqlServer;
    
    @BeforeEach
    void setUp() throws Exception {
        configTagsRelationMapperBySqlServer = new ConfigTagsRelationMapperBySqlServer();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, content);
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {tag});
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "properties"});
    }
    
    @Test
    void testGetTableName() {
        String tableName = configTagsRelationMapperBySqlServer.getTableName();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configTagsRelationMapperBySqlServer.getDataSource();
        assertEquals(DatabaseTypeConstant.SQLSERVER, dataSource);
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult = configTagsRelationMapperBySqlServer.findConfigInfo4PageFetchRows(context);
        String expectedInnerSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id=? AND a.data_id LIKE ? "
                + "AND a.group_id LIKE ? AND a.app_name = ? AND a.content LIKE ? AND b.tag_name IN (?) "
                + "GROUP BY a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc "
                + "ORDER BY a.id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        String expectedSql =
            "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.type,c.encrypted_data_key,c.c_desc,"
                + "STRING_AGG(d.tag_name, ',') WITHIN GROUP (ORDER BY d.tag_name) as config_tags "
                + "FROM (" + expectedInnerSql + ") c "
                + "LEFT JOIN config_tags_relation d ON c.id=d.id "
                + "GROUP BY c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.type,c.encrypted_data_key,c.c_desc";
        assertEquals(expectedSql, mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, dataId, groupId, appName, content, tag},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult = configTagsRelationMapperBySqlServer.findConfigInfoLike4PageCountRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT count(*) FROM ( SELECT a.id FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE a.tenant_id LIKE ? AND a.data_id LIKE ? AND a.group_id LIKE ? AND a.app_name = ? "
                + "AND a.content LIKE ? AND b.tag_name IN (?) GROUP BY a.id ) t");
        assertArrayEquals(new Object[] {tenantId, dataId, groupId, appName, content, tag},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult = configTagsRelationMapperBySqlServer.findConfigInfoLike4PageFetchRows(context);
        String expectedInnerSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc,a.gmt_modified "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id LIKE ? "
                + "AND a.data_id LIKE ? AND a.group_id LIKE ? AND a.app_name = ? AND a.content LIKE ? AND b.tag_name IN (?) "
                + "AND a.type IN (?, ?) GROUP BY a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.encrypted_data_key,"
                + "a.type,a.c_desc,a.gmt_modified ORDER BY a.id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        String expectedSql =
            "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,c.type,c.c_desc,c.gmt_modified,"
                + "STRING_AGG(d.tag_name, ',') WITHIN GROUP (ORDER BY d.tag_name) as config_tags "
                + "FROM (" + expectedInnerSql + ") c "
                + "LEFT JOIN config_tags_relation d ON c.id=d.id "
                + "GROUP BY c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,c.type,c.c_desc,c.gmt_modified";
        assertEquals(expectedSql, mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, dataId, groupId, appName, content, tag, "yaml", "properties"},
            mapperResult.getParamList().toArray());
    }
}
