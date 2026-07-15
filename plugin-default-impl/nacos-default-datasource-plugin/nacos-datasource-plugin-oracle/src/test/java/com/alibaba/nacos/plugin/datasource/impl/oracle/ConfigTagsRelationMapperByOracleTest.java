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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTagsRelationMapperByOracleTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String appName = "appName";
    
    String content = "content";
    
    String[] tagArr = new String[] {"tag1", "tag2"};
    
    MapperContext context;
    
    private ConfigTagsRelationMapperByOracle configTagsRelationMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configTagsRelationMapperByOracle = new ConfigTagsRelationMapperByOracle();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, content);
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult =
            configTagsRelationMapperByOracle.findConfigInfo4PageFetchRows(context);
        String sql = mapperResult.getSql();
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("SELECT DISTINCT a.id"));
        assertTrue(sql.contains("FROM config_info a"));
        assertTrue(sql.contains("LEFT JOIN config_tags_relation b"));
        assertTrue(sql.contains(
            "ORDER BY a.id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertTrue(mapperResult.getParamList().contains(tenantId));
        assertTrue(mapperResult.getParamList().contains(dataId));
        assertTrue(mapperResult.getParamList().contains(groupId));
        assertTrue(mapperResult.getParamList().contains(appName));
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configTagsRelationMapperByOracle.findConfigInfoLike4PageFetchRows(context);
        String sql = mapperResult.getSql();
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("SELECT DISTINCT a.id"));
        assertTrue(sql.contains("FROM config_info a"));
        assertTrue(sql.contains("LEFT JOIN config_tags_relation b"));
        assertTrue(sql.contains("ORDER BY a.id"));
        assertTrue(mapperResult.getParamList().contains(tenantId));
    }
    
    @Test
    void testGetTableName() {
        String tableName = configTagsRelationMapperByOracle.getTableName();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configTagsRelationMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
