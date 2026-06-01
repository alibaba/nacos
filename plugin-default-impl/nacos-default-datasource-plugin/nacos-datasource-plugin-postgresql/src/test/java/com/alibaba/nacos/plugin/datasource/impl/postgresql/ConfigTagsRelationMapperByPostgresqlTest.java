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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigTagsRelationMapperByPostgresqlTest
 *
 * @author Ken
 */
public class ConfigTagsRelationMapperByPostgresqlTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String appName = "appName";
    
    String content = "content";
    
    String[] tagArr = new String[] {"tagA", "tagB", "tagC"};
    
    String[] typeArr = new String[] {"text", "json", "xml", "yaml", "properties"};
    
    MapperContext context;
    
    private ConfigTagsRelationMapperByPostgresql configTagsRelationMapperByPostgresql;
    
    @BeforeEach
    void setUp() throws Exception {
        configTagsRelationMapperByPostgresql = new ConfigTagsRelationMapperByPostgresql();
        context = new MapperContext(startRow, pageSize);
        
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, content);
        
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
        context.putWhereParameter(FieldConstant.TYPE, typeArr);
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configTagsRelationMapperByPostgresql.findConfigInfoLike4PageFetchRows(context);
        String sql = mapperResult.getSql();
        // 验证是否存在标量子查询
        assertTrue(sql.contains(
            "(SELECT STRING_AGG(tag_name, ',') FROM config_tags_relation d WHERE d.id = c.id)"));
        // 验证是否存在EXISTS
        assertTrue(sql.contains(" EXISTS "));
        // 验证是否存在标签的子查询
        assertTrue(sql.contains("SELECT 1 FROM config_tags_relation"));
        
        assertEquals(
            "SELECT c.id,c.data_id,c.group_id,c.tenant_id,c.app_name,c.content,c.md5,c.encrypted_data_key,"
                + "c.type,c.c_desc,(SELECT STRING_AGG(tag_name, ',') FROM config_tags_relation d WHERE d.id = c.id) as config_tags "
                + "FROM (SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc "
                + "FROM config_info a  WHERE a.tenant_id LIKE ?  AND a.data_id LIKE ?  AND a.group_id LIKE ?  AND a.app_name = ?  "
                + "AND a.content LIKE ?  AND  EXISTS ( SELECT 1 FROM config_tags_relation b WHERE b.id = a.id  "
                + "AND  ( b.tag_name LIKE ?  OR b.tag_name LIKE ?  OR b.tag_name LIKE ?  )  )  AND a.type IN (?, ?, ?, ?, ?)  "
                + "OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY) c ",
            mapperResult.getSql());
        
        List<Object> expectedParams = new ArrayList<>();
        expectedParams.add(tenantId);
        expectedParams.add(dataId);
        expectedParams.add(groupId);
        expectedParams.add(appName);
        expectedParams.add(content);
        expectedParams.addAll(Arrays.asList(tagArr));
        expectedParams.addAll(Arrays.asList(typeArr));
        assertArrayEquals(expectedParams.toArray(), mapperResult.getParamList().toArray());
    }
}
