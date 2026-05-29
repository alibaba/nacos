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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.dialect.OracleDatabaseDialect;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleMapperCoverageTest {
    
    private final int startRow = 3;
    
    private final int pageSize = 7;
    
    private final String tenantId = "tenantId";
    
    private final String groupId = "groupId";
    
    private final String dataId = "dataId";
    
    private final String appName = "appName";
    
    private final String content = "content";
    
    private final String namespaceId = "namespaceId";
    
    private final Timestamp startTime = new Timestamp(1000L);
    
    private final Timestamp endTime = new Timestamp(2000L);
    
    private MapperContext context;
    
    @BeforeEach
    void setUp() {
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
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
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, 8L);
        context.putWhereParameter(FieldConstant.USAGE, 99);
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, "formal");
        context.putWhereParameter(FieldConstant.NID, 100L);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, 3000L);
    }
    
    @Test
    void testOracleDatabaseDialect() {
        OracleDatabaseDialect dialect = new OracleDatabaseDialect();
        assertEquals(DatabaseTypeConstant.ORACLE, dialect.getType());
        assertEquals("SYSDATE", dialect.getFunction("NOW()"));
    }
    
    @Test
    void testConfigInfoOptionalBranches() {
        ConfigInfoMapperByOracle mapper = new ConfigInfoMapperByOracle();
        assertResult(mapper.findChangeConfigFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified "
                + "FROM config_info WHERE 1=1 AND data_id LIKE ? "
                + "AND group_id LIKE ? AND tenant_id = ? AND app_name = ? "
                + "AND gmt_modified >=? AND gmt_modified <=? AND id > 8 "
                + "ORDER BY id ASC OFFSET 0 ROWS FETCH NEXT 7 ROWS ONLY",
            dataId, groupId, tenantId, appName, startTime, endTime);
        assertResult(mapper.findConfigInfoBaseLikeFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,content FROM config_info "
                + "WHERE 1=1 AND tenant_id='public' AND data_id LIKE ? "
                + "AND group_id LIKE ? AND content LIKE ? ORDER BY id "
                + "OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY",
            dataId, groupId, content);
    }
    
    @Test
    void testConfigTagsRelationTypeFilter() {
        ConfigTagsRelationMapperByOracle mapper = new ConfigTagsRelationMapperByOracle();
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tag"});
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "properties"});
        assertResult(mapper.findConfigInfoLike4PageFetchRows(context),
            "WITH tag_agg AS ( SELECT id, LISTAGG(DISTINCT tag_name, ',') "
                + "WITHIN GROUP (ORDER BY tag_name) AS config_tags "
                + "FROM config_tags_relation GROUP BY id ) "
                + "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name, "
                + "a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc, "
                + "t.config_tags FROM config_info a JOIN "
                + "(SELECT DISTINCT a.id FROM config_info a "
                + "LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE a.tenant_id LIKE ? AND a.data_id LIKE ? "
                + "AND a.group_id LIKE ? AND a.app_name = ? "
                + "AND a.content LIKE ? AND ( b.tag_name LIKE ? ) "
                + "AND a.type IN (?, ?) ORDER BY a.id OFFSET 3 ROWS "
                + "FETCH NEXT 7 ROWS ONLY) x ON a.id = x.id "
                + "LEFT JOIN tag_agg t ON a.id = t.id",
            tenantId, dataId, groupId, appName, content, "tag", "yaml", "properties");
    }
    
    @Test
    void testCapacitySelectBranches() {
        TenantCapacityMapperByOracle tenantCapacityMapper = new TenantCapacityMapperByOracle();
        assertEquals(DataSourceConstant.ORACLE, tenantCapacityMapper.getDataSource());
        assertResult(tenantCapacityMapper.select(context),
            "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                + "tenant_id FROM tenant_capacity WHERE tenant_id = ?",
            tenantId);
        
        GroupCapacityMapperByOracle groupCapacityMapper = new GroupCapacityMapperByOracle();
        assertEquals(DataSourceConstant.ORACLE, groupCapacityMapper.getDataSource());
        assertResult(groupCapacityMapper.select(context),
            "SELECT id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                + "group_id FROM group_capacity WHERE group_id = ?",
            groupId);
    }
    
    @Test
    void testHistoryGrayBranch() {
        HistoryConfigInfoMapperByOracle mapper = new HistoryConfigInfoMapperByOracle();
        context.putContextParameter(FieldConstant.GRAY_NAME, "gray");
        context.putWhereParameter(FieldConstant.GRAY_NAME, "gray");
        assertResult(mapper.getNextHistoryInfo(context),
            "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,"
                + "src_user,src_ip,op_type,publish_type,gray_name,ext_info,"
                + "gmt_create,gmt_modified,encrypted_data_key FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                + "AND publish_type = ? AND gray_name = ? AND nid > ? "
                + "ORDER BY nid FETCH FIRST 1 ROWS ONLY",
            dataId, groupId, tenantId, "formal", "gray", 100L);
    }
    
    @Test
    void testAiResourceMapper() {
        AiResourceMapperByOracle mapper = new AiResourceMapperByOracle();
        assertEquals(DataSourceConstant.ORACLE, mapper.getDataSource());
        context.putWhereParameter(FieldConstant.BIZ_TAGS, "tag");
        context.putWhereParameter(FieldConstant.SCOPE, "public");
        context.putWhereParameter(FieldConstant.OWNER, "owner");
        context.putWhereParameter(FieldConstant.ORDER_BY, FieldConstant.ORDER_BY_DOWNLOAD_COUNT);
        assertResult(mapper.findAiResourceFetchRows(context),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,"
                + "biz_tags,ext,c_from,version_info,meta_version,scope,owner,"
                + "download_count FROM ai_resource WHERE namespace_id = ? "
                + "AND name LIKE ? AND biz_tags LIKE ? AND type = ? "
                + "AND scope = ? AND owner = ? ORDER BY download_count DESC "
                + "OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY",
            namespaceId, "nacos", "tag", "mcp", "public", "owner");
        
        MapperContext alwaysEmptyContext = new MapperContext(startRow, pageSize);
        alwaysEmptyContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        alwaysEmptyContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_ALWAYS_EMPTY, true);
        assertResult(mapper.findAiResourceFetchRows(alwaysEmptyContext),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,"
                + "biz_tags,ext,c_from,version_info,meta_version,scope,owner,"
                + "download_count FROM ai_resource WHERE namespace_id = ? "
                + "AND 1 = ? ORDER BY gmt_modified DESC OFFSET 3 ROWS "
                + "FETCH NEXT 7 ROWS ONLY",
            namespaceId, 0);
        
        MapperContext orContext = new MapperContext(startRow, pageSize);
        orContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        Map<Object, Object> orGroup = new LinkedHashMap<>();
        orGroup.put(FieldConstant.STATUS, "stable");
        orGroup.put(FieldConstant.TYPE, Arrays.asList("mcp", "a2a"));
        orContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, orGroup);
        assertResult(mapper.findAiResourceFetchRows(orContext),
            "SELECT id,gmt_create,gmt_modified,name,type,c_desc,status,namespace_id,"
                + "biz_tags,ext,c_from,version_info,meta_version,scope,owner,"
                + "download_count FROM ai_resource WHERE namespace_id = ? "
                + "AND ( status = ? OR type IN (?, ?) ) "
                + "ORDER BY gmt_modified DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY",
            namespaceId, "stable", "mcp", "a2a");
    }
    
    @Test
    void testAiResourceVersionMapper() {
        AiResourceVersionMapperByOracle mapper = new AiResourceVersionMapperByOracle();
        assertEquals(DataSourceConstant.ORACLE, mapper.getDataSource());
        assertResult(mapper.findAiResourceVersionFetchRows(context),
            "SELECT id,gmt_create,gmt_modified,type,author,name,c_desc,status,version,"
                + "namespace_id,storage,publish_pipeline_info,download_count "
                + "FROM ai_resource_version WHERE namespace_id = ? AND name = ? "
                + "AND type = ? AND status = ? AND version = ? "
                + "ORDER BY gmt_modified DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY",
            namespaceId, "nacos", "mcp", "stable", "1.0.0");
        
        MapperContext minimalContext = new MapperContext(startRow, pageSize);
        minimalContext.putWhereParameter(FieldConstant.NAMESPACE_ID, namespaceId);
        minimalContext.putWhereParameter(FieldConstant.NAME, "nacos");
        minimalContext.putWhereParameter(FieldConstant.TYPE, "");
        minimalContext.putWhereParameter(FieldConstant.STATUS, "");
        minimalContext.putWhereParameter(FieldConstant.VERSION, "");
        assertResult(mapper.findAiResourceVersionFetchRows(minimalContext),
            "SELECT id,gmt_create,gmt_modified,type,author,name,c_desc,status,version,"
                + "namespace_id,storage,publish_pipeline_info,download_count "
                + "FROM ai_resource_version WHERE namespace_id = ? AND name = ? "
                + "ORDER BY gmt_modified DESC OFFSET 3 ROWS FETCH NEXT 7 ROWS ONLY",
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
