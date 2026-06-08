/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.dialect.DerbyDatabaseDialect;
import com.alibaba.nacos.plugin.datasource.impl.enums.derby.TrustedDerbyFunctionEnum;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerbyMapperCoverageTest {
    
    private final int startRow = 3;
    
    private final int pageSize = 7;
    
    private final String tenantId = "tenantId";
    
    private final String groupId = "groupId";
    
    private final String dataId = "dataId";
    
    private final String appName = "appName";
    
    private final String content = "content";
    
    private final String namespaceId = "namespaceId";
    
    private final Timestamp startTime = new Timestamp(1000L);
    
    private MapperContext context;
    
    @BeforeEach
    void setUp() {
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TENANT, tenantId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, content);
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        context.putWhereParameter(FieldConstant.NAME, "nacos");
        context.putWhereParameter(FieldConstant.TYPE, "mcp");
        context.putWhereParameter(FieldConstant.STATUS, "stable");
        context.putWhereParameter(FieldConstant.VERSION, "1.0.0");
        context.putWhereParameter(FieldConstant.ID, 12L);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, 20);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, new Timestamp(2000L));
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, 8L);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.USAGE, 99);
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, "formal");
        context.putWhereParameter(FieldConstant.SRC_TENANT, "srcTenant");
        context.putWhereParameter(FieldConstant.TARGET_TENANT, "targetTenant");
        context.putWhereParameter(FieldConstant.SRC_USER, "srcUser");
        context.putWhereParameter(FieldConstant.TARGET_ID, 100L);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, 3000L);
    }
    
    @Test
    void testDerbyDatabaseDialectAndTrustedFunctionEnum() {
        DerbyDatabaseDialect dialect = new DerbyDatabaseDialect();
        assertEquals(DatabaseTypeConstant.DERBY, dialect.getType());
        assertEquals("CURRENT_TIMESTAMP", dialect.getFunction("NOW()"));
        assertEquals("CURRENT_TIMESTAMP",
            TrustedDerbyFunctionEnum.getFunctionByName("CURRENT_TIMESTAMP()"));
        assertThrows(IllegalArgumentException.class,
            () -> TrustedDerbyFunctionEnum.getFunctionByName("UNKNOWN"));
    }
    
    @Test
    void testConfigInfoOptionalBranches() {
        ConfigInfoMapperByDerby mapper = new ConfigInfoMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, mapper.getDataSource());
        assertResult(mapper.findChangeConfigFetchRows(context),
            dataId, groupId, tenantId, appName, startTime, context.getWhereParameter(
                FieldConstant.END_TIME));
        assertSqlContains(mapper.findChangeConfigFetchRows(context),
            "AND data_id LIKE ? ESCAPE '\\'", "AND group_id LIKE ? ESCAPE '\\'",
            "AND tenant_id = ?", "AND app_name = ?", "AND gmt_modified >=?",
            "AND gmt_modified <=?");
        assertResult(mapper.findConfigInfoBaseLikeFetchRows(context), dataId, groupId, tenantId);
        assertSqlContains(mapper.findConfigInfoBaseLikeFetchRows(context),
            "AND data_id LIKE ? ESCAPE '\\'", "AND group_id LIKE ? ESCAPE '\\'",
            "AND content LIKE ? ESCAPE '\\'");
        
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "properties"});
        assertResult(mapper.findConfigInfo4PageFetchRows(context),
            tenantId, dataId, groupId, appName, content);
        assertResult(mapper.findConfigInfoLike4PageCountRows(context),
            tenantId, dataId, groupId, appName, content, "yaml", "properties");
        assertResult(mapper.findConfigInfoLike4PageFetchRows(context),
            tenantId, dataId, groupId, appName, content, "yaml", "properties");
        assertSqlContains(mapper.findConfigInfoLike4PageFetchRows(context),
            "type IN (?, ?)", "ORDER BY id OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY");
        assertResult(mapper.findChangeConfig(context), startTime, 8L, pageSize);
    }
    
    @Test
    void testConfigTagsRelationOptionalBranches() {
        ConfigInfoTagsRelationMapperByDerby mapper = new ConfigInfoTagsRelationMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, mapper.getDataSource());
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tag"});
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "properties"});
        assertResult(mapper.findConfigInfo4PageFetchRows(context),
            tenantId, dataId, groupId, appName, content, "tag");
        assertResult(mapper.findConfigInfoLike4PageCountRows(context),
            tenantId, dataId, groupId, appName, content, "tag");
        assertResult(mapper.findConfigInfoLike4PageFetchRows(context),
            tenantId, dataId, groupId, appName, content, "tag", "yaml", "properties");
        assertSqlContains(mapper.findConfigInfoLike4PageFetchRows(context), "a.type IN (?, ?)");
    }
    
    @Test
    void testConfigInfoGrayAndHistoryMappers() {
        ConfigInfoGrayMapperByDerby grayMapper = new ConfigInfoGrayMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, grayMapper.getDataSource());
        assertResult(grayMapper.findAllConfigInfoGrayForDumpAllFetchRows(context));
        assertResult(grayMapper.findChangeConfig(context), startTime, 8L, pageSize);
        
        HistoryConfigInfoMapperByDerby historyMapper = new HistoryConfigInfoMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, historyMapper.getDataSource());
        assertResult(historyMapper.removeConfigHistory(context), startTime, 20);
        assertResult(historyMapper.pageFindConfigHistoryFetchRows(context), dataId, groupId,
            tenantId);
        assertResult(historyMapper.findDeletedConfig(context), "formal", startTime, 8L, pageSize);
    }
    
    @Test
    void testCapacitySelectBranches() {
        TenantCapacityMapperByDerby tenantCapacityMapper = new TenantCapacityMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, tenantCapacityMapper.getDataSource());
        assertResult(tenantCapacityMapper.select(context), tenantId);
        
        GroupCapacityMapperByDerby groupCapacityMapper = new GroupCapacityMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, groupCapacityMapper.getDataSource());
        assertResult(groupCapacityMapper.select(context), groupId);
    }
    
    @Test
    void testAiResourceMapper() {
        AiResourceMapperByDerby mapper = new AiResourceMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, mapper.getDataSource());
        mapper.appendSingleAndCondition(new WhereBuilder("SELECT * FROM t"), null, "value",
            true);
        mapper.appendSingleAndCondition(new WhereBuilder("SELECT * FROM t"), "type",
            Collections.emptyList(), false);
        mapper.appendSingleAndCondition(new WhereBuilder("SELECT * FROM t"), "type",
            Arrays.asList("mcp", "a2a"), false);
        mapper.appendSingleAndCondition(new WhereBuilder("SELECT * FROM t"), "name",
            "nacos", true);
        mapper.appendSingleAndCondition(new WhereBuilder("SELECT * FROM t"), "scope",
            "public", false);
        
        context.putWhereParameter(FieldConstant.BIZ_TAGS, "tag");
        context.putWhereParameter(FieldConstant.SCOPE, "public");
        context.putWhereParameter(FieldConstant.OWNER, "owner");
        context.putWhereParameter(FieldConstant.ORDER_BY, FieldConstant.ORDER_BY_DOWNLOAD_COUNT);
        assertResult(mapper.findAiResourceFetchRows(context),
            namespaceId, "nacos", "tag", "mcp", "public", "owner");
        assertSqlContains(mapper.findAiResourceFetchRows(context),
            "name LIKE ? ESCAPE '\\'", "biz_tags LIKE ? ESCAPE '\\'",
            "ORDER BY download_count DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY");
        
        MapperContext orContext = new MapperContext(startRow, pageSize);
        orContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        Map<Object, Object> orGroup = new LinkedHashMap<>();
        orGroup.put(FieldConstant.STATUS, "stable");
        orGroup.put(FieldConstant.TYPE, Arrays.asList("mcp", "a2a"));
        orContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, orGroup);
        assertResult(mapper.findAiResourceFetchRows(orContext),
            namespaceId, "stable", "mcp", "a2a");
    }
    
    @Test
    void testAiResourceVersionMapper() {
        AiResourceVersionMapperByDerby mapper = new AiResourceVersionMapperByDerby();
        assertEquals(DataSourceConstant.DERBY, mapper.getDataSource());
        assertResult(mapper.findAiResourceVersionFetchRows(context),
            namespaceId, "nacos", "mcp", "stable", "1.0.0");
        
        MapperContext minimalContext = new MapperContext(startRow, pageSize);
        minimalContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        minimalContext.putWhereParameter(FieldConstant.NAME, "nacos");
        minimalContext.putWhereParameter(FieldConstant.TYPE, "");
        minimalContext.putWhereParameter(FieldConstant.STATUS, "");
        minimalContext.putWhereParameter(FieldConstant.VERSION, "");
        assertResult(mapper.findAiResourceVersionFetchRows(minimalContext), namespaceId, "nacos");
    }
    
    private static void assertResult(MapperResult result, Object... parameters) {
        assertArrayEquals(parameters, result.getParamList().toArray());
    }
    
    private static void assertSqlContains(MapperResult result, String... snippets) {
        String sql = normalizeSql(result.getSql());
        for (String each : snippets) {
            assertTrue(sql.contains(normalizeSql(each)));
        }
    }
    
    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
