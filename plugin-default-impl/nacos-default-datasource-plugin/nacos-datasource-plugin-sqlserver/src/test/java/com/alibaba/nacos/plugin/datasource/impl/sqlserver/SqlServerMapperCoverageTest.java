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

package com.alibaba.nacos.plugin.datasource.impl.sqlserver;

import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.dialect.SqlServerDatabaseDialect;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerMapperCoverageTest {
    
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
    }
    
    @Test
    void testSqlServerDatabaseDialect() {
        SqlServerDatabaseDialect dialect = new SqlServerDatabaseDialect();
        assertEquals(DatabaseTypeConstant.SQLSERVER, dialect.getType());
        assertEquals("GETDATE()", dialect.getFunction("NOW()"));
    }
    
    @Test
    void testConfigInfoMapper() {
        ConfigInfoMapperBySqlServer mapper = new ConfigInfoMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        assertResult(mapper.findChangeConfigFetchRows(context),
            tenantId, appName, startTime, context.getWhereParameter(FieldConstant.END_TIME));
        assertSqlContains(mapper.findChangeConfigFetchRows(context),
            "AND tenant_id = ?", "AND app_name = ?", "ORDER BY id ASC");
    }
    
    @Test
    void testGroupCapacityMapper() {
        GroupCapacityMapperBySqlServer mapper = new GroupCapacityMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        assertResult(mapper.select(context), groupId);
    }
    
    @Test
    void testTenantCapacityMapper() {
        TenantCapacityMapperBySqlServer mapper = new TenantCapacityMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        assertResult(mapper.select(context), tenantId);
    }
    
    @Test
    void testTenantInfoMapper() {
        TenantInfoMapperBySqlServer mapper = new TenantInfoMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
    }
    
    @Test
    void testConfigTagsRelationMapper() {
        ConfigTagsRelationMapperBySqlServer mapper = new ConfigTagsRelationMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
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
    void testConfigInfoGrayMapper() {
        ConfigInfoGrayMapperBySqlServer mapper = new ConfigInfoGrayMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        assertResult(mapper.findAllConfigInfoGrayForDumpAllFetchRows(context));
        assertResult(mapper.findChangeConfig(context), startTime, 8L, pageSize);
    }
    
    @Test
    void testHistoryConfigInfoMapper() {
        HistoryConfigInfoMapperBySqlServer mapper = new HistoryConfigInfoMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        assertResult(mapper.removeConfigHistory(context), startTime, 20);
        // 验证修复后的 SQL 包含所有必要字段
        MapperResult result = mapper.pageFindConfigHistoryFetchRows(context);
        assertResult(result, dataId, groupId, tenantId);
        // 验证 SELECT 列表包含 publish_type 字段
        assertSqlContains(result, "publish_type", "ext_info", "gray_name");
        // 验证正确的分页语法
        assertSqlContains(result, "ORDER BY nid DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY");
        
        assertResult(mapper.findDeletedConfig(context), "formal", startTime, 8L, pageSize);
    }
    
    @Test
    void testAiResourceMapper() {
        AiResourceMapperBySqlServer mapper = new AiResourceMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
        
        // 测试 appendSingleAndCondition 方法
        mapper.appendSingleAndCondition(null, null, "value", true);
        mapper.appendSingleAndCondition(null, "type", Collections.emptyList(), false);
        mapper.appendSingleAndCondition(null, "type", Arrays.asList("mcp", "a2a"), false);
        mapper.appendSingleAndCondition(null, "name", "nacos", true);
        mapper.appendSingleAndCondition(null, "scope", "public", false);
        
        // 设置额外参数
        context.putWhereParameter(FieldConstant.BIZ_TAGS, "tag");
        context.putWhereParameter(FieldConstant.SCOPE, "public");
        context.putWhereParameter(FieldConstant.OWNER, "owner");
        context.putWhereParameter(FieldConstant.ORDER_BY, FieldConstant.ORDER_BY_DOWNLOAD_COUNT);
        
        // 验证修复后的 SQL 包含所有必要字段
        MapperResult result = mapper.findAiResourceFetchRows(context);
        assertResult(result, namespaceId, "nacos", "tag", "mcp", "public", "owner");
        
        // 验证 SELECT 列表包含 c_from 字段
        assertSqlContains(result, "c_from");
        
        // 验证没有双重 ORDER BY（只有一个 ORDER BY）
        String sql = result.getSql();
        long orderByCount = sql.toUpperCase().chars().filter(ch -> ch == 'O').count();
        // 更准确的方式：统计 ORDER BY 出现次数
        String upperSql = sql.toUpperCase();
        int orderByIndex1 = upperSql.indexOf("ORDER BY");
        int orderByIndex2 = upperSql.indexOf("ORDER BY", orderByIndex1 + 1);
        // 应该只有一个 ORDER BY
        assertTrue(orderByIndex2 == -1, "SQL 不应包含多个 ORDER BY: " + sql);
        
        // 验证正确的分页语法
        assertSqlContains(result, "ORDER BY download_count DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY");
        
        // 测试 OR 条件组
        MapperContext orContext = new MapperContext(startRow, pageSize);
        orContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        Map<Object, Object> orGroup = new LinkedHashMap<>();
        orGroup.put(FieldConstant.STATUS, "stable");
        orGroup.put(FieldConstant.TYPE, Arrays.asList("mcp", "a2a"));
        orContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, orGroup);
        assertResult(mapper.findAiResourceFetchRows(orContext), namespaceId, "stable", "mcp", "a2a");
    }
    
    @Test
    void testAiResourceVersionMapper() {
        AiResourceVersionMapperBySqlServer mapper = new AiResourceVersionMapperBySqlServer();
        assertEquals(DatabaseTypeConstant.SQLSERVER, mapper.getDataSource());
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
            assertTrue(sql.contains(normalizeSql(each)), 
                "SQL 不包含预期片段 '" + each + "': " + result.getSql());
        }
    }
    
    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
