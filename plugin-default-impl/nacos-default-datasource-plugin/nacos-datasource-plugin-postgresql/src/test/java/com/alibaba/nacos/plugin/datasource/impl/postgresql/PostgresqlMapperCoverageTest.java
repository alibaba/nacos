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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.dialect.PostgresqlDatabaseDialect;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresqlMapperCoverageTest {
    
    private final int startRow = 3;
    
    private final int pageSize = 7;
    
    private final String tenantId = "tenantId";
    
    private final String groupId = "groupId";
    
    private final String namespaceId = "namespaceId";
    
    private final long modifiedTime = 1000L;
    
    private MapperContext context;
    
    @BeforeEach
    void setUp() {
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        context.putWhereParameter(FieldConstant.NAME, "nacos");
        context.putWhereParameter(FieldConstant.TYPE, "mcp");
        context.putWhereParameter(FieldConstant.STATUS, "stable");
        context.putWhereParameter(FieldConstant.VERSION, "1.0.0");
        context.putWhereParameter(FieldConstant.ID, 12L);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, 20);
        context.putWhereParameter(FieldConstant.START_TIME, modifiedTime);
        context.putWhereParameter(FieldConstant.DATA_ID, "dataId");
        context.putUpdateParameter(FieldConstant.GROUP_ID, groupId);
        context.putUpdateParameter(FieldConstant.TENANT_ID, tenantId);
        context.putUpdateParameter(FieldConstant.QUOTA, 10);
        context.putUpdateParameter(FieldConstant.MAX_SIZE, 100);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, 5);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, 200);
        context.putUpdateParameter(FieldConstant.GMT_CREATE, 100L);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modifiedTime);
        context.putWhereParameter(FieldConstant.USAGE, 99);
    }
    
    @Test
    void testPostgresqlDatabaseDialect() {
        PostgresqlDatabaseDialect dialect = new PostgresqlDatabaseDialect();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, dialect.getType());
        assertEquals("NOW()", dialect.getFunction("NOW()"));
        assertEquals("SELECT OFFSET ? LIMIT ? ", dialect.getLimitPageSqlWithMark("SELECT"));
        assertEquals("SELECT  OFFSET 14 LIMIT 7", dialect.getLimitPageSql("SELECT", 3, 7));
        assertEquals("SELECT  OFFSET 3 LIMIT 7",
            dialect.getLimitPageSqlWithOffset("SELECT", 3, 7));
    }
    
    @Test
    void testTenantInfoMapperInsertAndUpdate() {
        TenantInfoMapperByPostgresql mapper = new TenantInfoMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertEquals(TableConstant.TENANT_INFO, mapper.getTableName());
        assertEquals(
            "INSERT INTO tenant_info(gmt_create, tenant_id, gmt_modified) "
                + "VALUES(TO_TIMESTAMP(? / 1000.0),?,NOW())",
            mapper.insert(Arrays.asList("gmt_create", "tenant_id", "gmt_modified@NOW()")));
        assertEquals(
            "UPDATE tenant_info SET gmt_modified = TO_TIMESTAMP(? / 1000.0),"
                + "tenant_id = ?,gmt_create = NOW() WHERE tenant_id = ?",
            mapper.update(Arrays.asList("gmt_modified", "tenant_id", "gmt_create@NOW()"),
                Collections.singletonList("tenant_id")));
        assertEquals("UPDATE tenant_info SET tenant_id = ?",
            mapper.update(Collections.singletonList("tenant_id"), Collections.emptyList()));
    }
    
    @Test
    void testGroupCapacityMapper() {
        GroupCapacityMapperByPostgresql mapper = new GroupCapacityMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertResult(mapper.select(context),
            "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, group_id "
                + "FROM group_capacity WHERE group_id = ?",
            groupId);
        assertResult(mapper.insertIntoSelect(context),
            "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count,"
                + " max_aggr_size, max_history_count, gmt_create, gmt_modified)"
                + " SELECT ?, ?, count(*), ?, ?, ?, 0, ?, ? FROM config_info",
            groupId, 10, 100, 5, 200, 100L, modifiedTime);
        assertResult(mapper.insertIntoSelectByWhere(context),
            "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count,"
                + " max_aggr_size, max_history_count, gmt_create, gmt_modified)"
                + " SELECT ?, ?, count(*), ?, ?, ?, 0, ?, ? FROM config_info "
                + "WHERE group_id=? AND tenant_id = '" + NamespaceUtil
                    .getNamespaceDefaultId()
                + "'",
            groupId, 10, 100, 5, 200, 100L, modifiedTime, groupId);
        assertResult(mapper.incrementUsageByWhereQuotaEqualZero(context),
            "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? "
                + "WHERE group_id = ? AND usage < ? AND quota = 0",
            modifiedTime, groupId, 99);
        assertResult(mapper.incrementUsageByWhereQuotaNotEqualZero(context),
            "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? "
                + "WHERE group_id = ? AND usage < quota AND quota != 0",
            modifiedTime, groupId);
        assertResult(mapper.incrementUsageByWhere(context),
            "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?",
            modifiedTime, groupId);
        assertResult(mapper.decrementUsageByWhere(context),
            "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? "
                + "WHERE group_id = ? AND usage > 0",
            modifiedTime, groupId);
        assertResult(mapper.updateUsage(context),
            "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), "
                + "gmt_modified = ? WHERE group_id = ?",
            modifiedTime, groupId);
        assertResult(mapper.updateUsageByWhere(context),
            "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info "
                + "WHERE group_id=? AND tenant_id = '" + NamespaceUtil
                    .getNamespaceDefaultId()
                + "'), gmt_modified = ? WHERE group_id= ?",
            groupId, modifiedTime, groupId);
    }
    
    @Test
    void testTenantCapacityMapper() {
        TenantCapacityMapperByPostgresql mapper = new TenantCapacityMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertResult(mapper.select(context),
            "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, tenant_id "
                + "FROM tenant_capacity WHERE tenant_id = ?",
            tenantId);
        assertResult(mapper.incrementUsageWithDefaultQuotaLimit(context),
            "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? "
                + "WHERE tenant_id = ? AND usage < ? AND quota = 0",
            modifiedTime, tenantId, 99);
        assertResult(mapper.incrementUsageWithQuotaLimit(context),
            "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? "
                + "WHERE tenant_id = ? AND usage < quota AND quota != 0",
            modifiedTime, tenantId);
        assertResult(mapper.incrementUsage(context),
            "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? "
                + "WHERE tenant_id = ?",
            modifiedTime, tenantId);
        assertResult(mapper.decrementUsage(context),
            "UPDATE tenant_capacity SET usage = usage - 1, gmt_modified = ? "
                + "WHERE tenant_id = ? AND usage > 0",
            modifiedTime, tenantId);
        assertResult(mapper.correctUsage(context),
            "UPDATE tenant_capacity SET usage = (SELECT count(*) FROM config_info "
                + "WHERE tenant_id = ?), gmt_modified = ? WHERE tenant_id = ?",
            tenantId, modifiedTime, tenantId);
        assertResult(mapper.insertTenantCapacity(context),
            "INSERT INTO tenant_capacity (tenant_id, quota, usage, max_size, max_aggr_count, "
                + "max_aggr_size, max_history_count, gmt_create, gmt_modified)"
                + " SELECT ?, ?, count(*), ?, ?, ?, 0, ?, ? FROM config_info WHERE tenant_id=?",
            tenantId, 10, 100, 5, 200, 100L, modifiedTime, tenantId);
    }
    
    @Test
    void testHistoryConfigInfoMapper() {
        HistoryConfigInfoMapperByPostgresql mapper = new HistoryConfigInfoMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertEquals(TableConstant.HIS_CONFIG_INFO, mapper.getTableName());
        assertResult(mapper.removeConfigHistory(context),
            "WITH temp_table as (SELECT id FROM his_config_info WHERE gmt_modified < ? LIMIT ? ) "
                + "DELETE FROM his_config_info WHERE id in (SELECT id FROM temp_table) ",
            modifiedTime, 20);
        assertResult(mapper.pageFindConfigHistoryFetchRows(context),
            "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,ext_info,"
                + "publish_type,gray_name,gmt_create,gmt_modified FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC "
                + "LIMIT 7 OFFSET 3",
            "dataId", groupId, tenantId);
    }
    
    @Test
    void testSimplePostgresqlMappers() {
        ConfigInfoMapperByPostgresql configInfoMapper = new ConfigInfoMapperByPostgresql();
        assertEquals("NOW()", configInfoMapper.getFunction("NOW()"));
        
        ConfigInfoGrayMapperByPostgresql grayMapper = new ConfigInfoGrayMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, grayMapper.getDataSource());
        assertEquals("NOW()", grayMapper.getFunction("NOW()"));
        assertEquals(TableConstant.CONFIG_INFO_GRAY, grayMapper.getTableName());
        assertResult(grayMapper.findAllConfigInfoGrayForDumpAllFetchRows(context),
            " SELECT id,data_id,group_id,tenant_id,gray_name,gray_rule,app_name,content,"
                + "md5,gmt_modified  FROM  config_info_gray  ORDER BY id LIMIT 7 OFFSET 3");
        
    }
    
    @Test
    void testAiResourceMapper() {
        AiResourceMapperByPostgresql mapper = new AiResourceMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertEquals(TableConstant.AI_RESOURCE, mapper.getTableName());
        context.putWhereParameter(FieldConstant.BIZ_TAGS, "tag");
        context.putWhereParameter(FieldConstant.SCOPE, "public");
        context.putWhereParameter(FieldConstant.OWNER, "owner");
        context.putWhereParameter(FieldConstant.ORDER_BY, FieldConstant.ORDER_BY_DOWNLOAD_COUNT);
        assertResult(mapper.findAiResourceFetchRows(context),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,biz_tags,"
                + "ext,c_from,version_info,meta_version,scope,owner,download_count "
                + "FROM ai_resource WHERE namespace_id = ? AND name LIKE ? "
                + "AND biz_tags LIKE ? AND type = ? AND scope = ? AND owner = ? "
                + "ORDER BY download_count DESC LIMIT 7 OFFSET 3",
            namespaceId, "nacos", "tag", "mcp", "public", "owner");
        
        MapperContext alwaysEmptyContext = new MapperContext(startRow, pageSize);
        alwaysEmptyContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        alwaysEmptyContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_ALWAYS_EMPTY, true);
        assertResult(mapper.findAiResourceFetchRows(alwaysEmptyContext),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,biz_tags,"
                + "ext,c_from,version_info,meta_version,scope,owner,download_count "
                + "FROM ai_resource WHERE namespace_id = ? AND 1 = ? "
                + "ORDER BY gmt_modified DESC LIMIT 7 OFFSET 3",
            namespaceId, 0);
        
        MapperContext orContext = new MapperContext(startRow, pageSize);
        orContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        Map<Object, Object> orGroup = new LinkedHashMap<>();
        orGroup.put(FieldConstant.STATUS, "stable");
        orGroup.put(FieldConstant.TYPE, Arrays.asList("mcp", "a2a"));
        orContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, orGroup);
        assertResult(mapper.findAiResourceFetchRows(orContext),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,biz_tags,"
                + "ext,c_from,version_info,meta_version,scope,owner,download_count "
                + "FROM ai_resource WHERE namespace_id = ? AND ( status = ? OR type IN (?, ?) ) "
                + "ORDER BY gmt_modified DESC LIMIT 7 OFFSET 3",
            namespaceId, "stable", "mcp", "a2a");
    }
    
    @Test
    void testAiResourceVersionMapper() {
        AiResourceVersionMapperByPostgresql mapper = new AiResourceVersionMapperByPostgresql();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, mapper.getDataSource());
        assertEquals("NOW()", mapper.getFunction("NOW()"));
        assertEquals(TableConstant.AI_RESOURCE_VERSION, mapper.getTableName());
        assertResult(mapper.findAiResourceVersionFetchRows(context),
            "SELECT id,gmt_create,gmt_modified,type,author,name,c_desc,status,version,namespace_id,"
                + "storage,publish_pipeline_info,download_count FROM ai_resource_version "
                + "WHERE namespace_id = ? AND name = ? AND type = ? AND status = ? "
                + "AND version = ? ORDER BY gmt_modified DESC LIMIT 7 OFFSET 3",
            namespaceId, "nacos", "mcp", "stable", "1.0.0");
        
        MapperContext minimalContext = new MapperContext(startRow, pageSize);
        minimalContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        minimalContext.putWhereParameter(FieldConstant.NAME, "nacos");
        minimalContext.putWhereParameter(FieldConstant.TYPE, "");
        minimalContext.putWhereParameter(FieldConstant.STATUS, "");
        minimalContext.putWhereParameter(FieldConstant.VERSION, "");
        assertResult(mapper.findAiResourceVersionFetchRows(minimalContext),
            "SELECT id,gmt_create,gmt_modified,type,author,name,c_desc,status,version,namespace_id,"
                + "storage,publish_pipeline_info,download_count FROM ai_resource_version "
                + "WHERE namespace_id = ? AND name = ? ORDER BY gmt_modified DESC LIMIT 7 OFFSET 3",
            namespaceId, "nacos");
    }
    
    private static void assertResult(MapperResult result, String sql, Object... parameters) {
        assertEquals(normalizeSql(sql), normalizeSql(result.getSql()));
        assertArrayEquals(parameters, result.getParamList().toArray());
    }
    
    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
