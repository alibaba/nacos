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

class ConfigInfoTagsRelationMapperByDerbyTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String[] tagArr = new String[] {"tag1", "tag3", "tag2", "tag4", "tag5"};
    
    MapperContext context;
    
    private ConfigInfoTagsRelationMapperByDerby configInfoTagsRelationMapperByDerby;
    
    @BeforeEach
    void setUp() throws Exception {
        this.configInfoTagsRelationMapperByDerby = new ConfigInfoTagsRelationMapperByDerby();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByDerby.findConfigInfoLike4PageCountRows(context);
        // Verify LIKE ESCAPE is used for tenant_id and tag_name
        assertEquals("SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b "
            + "ON a.id=b.id WHERE a.tenant_id LIKE ? ESCAPE '\\'  AND "
            + " ( b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\' "
            + " OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  ) ",
            mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByDerby.findConfigInfo4PageFetchRows(context);
        // 验证增强后的 SQL，包含新字段但不包含 configTags（Derby 不支持 GROUP_CONCAT）
        String expectedSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc"
                + " FROM config_info  a LEFT JOIN "
                + "config_tags_relation b ON a.id=b.id WHERE  a.tenant_id=?  AND b.tag_name IN (?, ?, ?, ?, ?)  "
                + "OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
        assertEquals(expectedSql, mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
        
        // Test with content to verify LIKE ESCAPE
        context.putWhereParameter(FieldConstant.CONTENT, "test_content");
        mapperResult = configInfoTagsRelationMapperByDerby.findConfigInfo4PageFetchRows(context);
        expectedSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,"
                + "a.encrypted_data_key,a.c_desc" + " FROM config_info  a LEFT JOIN "
                + "config_tags_relation b ON a.id=b.id WHERE  a.tenant_id=?  AND a.content LIKE ? ESCAPE '\\' "
                + " AND b.tag_name IN (?, ?, ?, ?, ?)  " + "OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
        assertEquals(expectedSql, mapperResult.getSql());
        list = CollectionUtils.list(tenantId, "test_content");
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByDerby.findConfigInfoLike4PageCountRows(context);
        // Verify LIKE ESCAPE is used for all LIKE conditions
        assertEquals("SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b "
            + "ON a.id=b.id WHERE a.tenant_id LIKE ? ESCAPE '\\'  AND "
            + " ( b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\' "
            + " OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  ) ",
            mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void tsetFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByDerby.findConfigInfoLike4PageFetchRows(context);
        // 验证增强后的 SQL，包含新字段但不包含 configTags（Derby 不支持 GROUP_CONCAT），并验证 LIKE ESCAPE
        String expectedSql =
            "SELECT a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,"
                + "a.encrypted_data_key,a.type,a.c_desc FROM config_info a "
                + "LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id LIKE ? ESCAPE '\\'  AND "
                + " ( b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\' "
                + " OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  )"
                + "  OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
        assertEquals(expectedSql, mapperResult.getSql());
        List<Object> list = CollectionUtils.list(tenantId);
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
        
        // Test with dataId, group, and content to verify LIKE ESCAPE
        context.putWhereParameter(FieldConstant.DATA_ID, "test_data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "test_group");
        context.putWhereParameter(FieldConstant.CONTENT, "test_content");
        mapperResult =
            configInfoTagsRelationMapperByDerby.findConfigInfoLike4PageFetchRows(context);
        expectedSql = "SELECT a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,"
            + "a.encrypted_data_key,a.type,a.c_desc FROM config_info a "
            + "LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id LIKE ? ESCAPE '\\' "
            + " AND a.data_id LIKE ? ESCAPE '\\'  AND a.group_id LIKE ? ESCAPE '\\'  AND a.content LIKE ? ESCAPE '\\'  AND "
            + " ( b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\' "
            + " OR b.tag_name LIKE ? ESCAPE '\\'  OR b.tag_name LIKE ? ESCAPE '\\'  )"
            + "  OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
        assertEquals(expectedSql, mapperResult.getSql());
        list = CollectionUtils.list(tenantId, "test_data", "test_group", "test_content");
        list.addAll(Arrays.asList(tagArr));
        assertArrayEquals(mapperResult.getParamList().toArray(), list.toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoTagsRelationMapperByDerby.getTableName();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoTagsRelationMapperByDerby.getDataSource();
        assertEquals(DataSourceConstant.DERBY, dataSource);
    }
}
