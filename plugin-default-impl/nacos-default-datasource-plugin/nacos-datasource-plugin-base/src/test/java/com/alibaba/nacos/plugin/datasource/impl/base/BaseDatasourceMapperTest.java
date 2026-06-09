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

package com.alibaba.nacos.plugin.datasource.impl.base;

import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.impl.dialect.AbstractDatabaseDialect;
import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseDatasourceMapperTest {
    
    private static final String DATA_SOURCE = "base-test";
    
    private static final String FUNCTION_PREFIX = "function:";
    
    private final int startRow = 2;
    
    private final int pageSize = 5;
    
    private final String appName = "appName";
    
    private final String dataId = "dataId";
    
    private final String groupId = "groupId";
    
    private final String tenantId = "tenantId";
    
    private final String content = "content";
    
    private final String id = "123";
    
    private final Timestamp startTime = new Timestamp(1000L);
    
    private final Timestamp endTime = new Timestamp(2000L);
    
    private TestConfigInfoMapper configInfoMapper;
    
    private MapperContext context;
    
    @BeforeAll
    static void registerDialect() throws Exception {
        Field field = DatabaseDialectManager.class.getDeclaredField("SUPPORT_DIALECT_MAP");
        field.setAccessible(true);
        Map<String, DatabaseDialect> dialects = (Map<String, DatabaseDialect>) field.get(null);
        dialects.put(DATA_SOURCE, new TestDatabaseDialect());
    }
    
    @BeforeEach
    void setUp() {
        configInfoMapper = new TestConfigInfoMapper();
        context = newContext();
    }
    
    @Test
    void testConfigInfoMapperBasicDelegates() {
        assertEquals(TableConstant.CONFIG_INFO, configInfoMapper.getTableName());
        assertEquals("SELECT  LIMIT 1 , 3",
            configInfoMapper.getLimitPageSqlWithOffset("SELECT", 1, 3));
        assertEquals("SELECT LIMIT ?,? ", configInfoMapper.getLimitPageSqlWithMark("SELECT"));
        assertEquals(FUNCTION_PREFIX + "NOW()", configInfoMapper.getFunction("NOW()"));
    }
    
    @Test
    void testConfigInfoMapperSimpleFetchRows() {
        assertResult(configInfoMapper.findConfigInfoByAppFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info "
                + "WHERE tenant_id LIKE ? AND app_name= ?  LIMIT 2 , 5",
            tenantId, appName);
        assertResult(configInfoMapper.getTenantIdList(context),
            "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id "
                + " LIMIT 2 , 5");
        assertResult(configInfoMapper.getGroupIdList(context),
            "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id "
                + " LIMIT 2 , 5");
        assertResult(configInfoMapper.findAllConfigKey(context),
            " SELECT data_id,group_id,app_name  FROM (  SELECT id FROM config_info "
                + "WHERE tenant_id LIKE ? ORDER BY id   LIMIT 2 , 5 ) g, config_info t "
                + "WHERE g.id = t.id  ",
            tenantId);
        assertResult(configInfoMapper.findAllConfigInfoBaseFetchRows(context),
            " SELECT t.id,data_id,group_id,content,md5 FROM (  SELECT id FROM config_info "
                + "ORDER BY id  LIMIT ?,?   )  g, config_info t  WHERE g.id = t.id ",
            startRow, pageSize);
        assertResult(configInfoMapper.listGroupKeyMd5ByPageFetchRows(context),
            " SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,"
                + "encrypted_data_key FROM (  SELECT id FROM config_info ORDER BY id  "
                + " LIMIT 2 , 5 ) g, config_info t WHERE g.id = t.id");
        assertResult(configInfoMapper.findConfigInfoBaseByGroupFetchRows(context),
            "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? "
                + "AND tenant_id=?   LIMIT 2 , 5",
            groupId, tenantId);
        assertResult(configInfoMapper.findAllConfigInfoFetchRows(context),
            " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM ( "
                + "SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id  "
                + "LIMIT ?,?  ) g, config_info t  WHERE g.id = t.id ",
            tenantId, startRow, pageSize);
    }
    
    @Test
    void testConfigInfoMapperFindAllConfigInfoFragment() {
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        assertResult(configInfoMapper.findAllConfigInfoFragment(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,"
                + "encrypted_data_key FROM config_info WHERE id > ? ORDER BY id ASC "
                + "  LIMIT 2 , 5",
            id);
        
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        assertResult(configInfoMapper.findAllConfigInfoFragment(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type,"
                + "encrypted_data_key FROM config_info WHERE id > ? ORDER BY id ASC "
                + "  LIMIT 2 , 5",
            id);
    }
    
    @Test
    void testConfigInfoMapperFindChangeConfigFetchRows() {
        assertResult(configInfoMapper.findChangeConfigFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified "
                + "FROM config_info WHERE  1=1  AND data_id LIKE ?  AND group_id LIKE ? "
                + " AND tenant_id = ?  AND app_name = ?  AND gmt_modified >=? "
                + " AND gmt_modified <=?  AND id > 7 ORDER BY id ASC  LIMIT 0 , 5",
            dataId, groupId, tenantId, appName, startTime, endTime);
        
        MapperContext emptyContext = new MapperContext(startRow, pageSize);
        emptyContext.putWhereParameter(FieldConstant.TENANT_ID, "");
        emptyContext.putWhereParameter(FieldConstant.DATA_ID, "");
        emptyContext.putWhereParameter(FieldConstant.GROUP_ID, "");
        emptyContext.putWhereParameter(FieldConstant.APP_NAME, "");
        emptyContext.putWhereParameter(FieldConstant.START_TIME, null);
        emptyContext.putWhereParameter(FieldConstant.END_TIME, null);
        emptyContext.putWhereParameter(FieldConstant.LAST_MAX_ID, 8L);
        assertResult(configInfoMapper.findChangeConfigFetchRows(emptyContext),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified "
                + "FROM config_info WHERE  1=1  AND id > 8 ORDER BY id ASC  LIMIT 0 , 5");
    }
    
    @Test
    void testConfigInfoMapperFindBaseLikeFetchRows() {
        assertResult(configInfoMapper.findConfigInfoBaseLikeFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 "
                + "AND tenant_id=''  AND data_id LIKE ?  AND group_id LIKE ? "
                + " AND content LIKE ?   LIMIT 2 , 5",
            dataId, groupId, content);
        
        MapperContext emptyContext = new MapperContext(startRow, pageSize);
        emptyContext.putWhereParameter(FieldConstant.DATA_ID, "");
        emptyContext.putWhereParameter(FieldConstant.GROUP_ID, "");
        emptyContext.putWhereParameter(FieldConstant.CONTENT, "");
        assertResult(configInfoMapper.findConfigInfoBaseLikeFetchRows(emptyContext),
            "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 "
                + "AND tenant_id=''   LIMIT 2 , 5");
    }
    
    @Test
    void testConfigInfoMapperFindConfigInfo4PageFetchRows() {
        assertResult(configInfoMapper.findConfigInfo4PageFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,type,"
                + "encrypted_data_key,c_desc FROM config_info WHERE  tenant_id=? "
                + " AND data_id=?  AND group_id=?  AND app_name=?  AND content LIKE ? "
                + "  LIMIT 2 , 5",
            tenantId, dataId, groupId, appName, content);
        
        MapperContext requiredOnlyContext = new MapperContext(startRow, pageSize);
        requiredOnlyContext.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        requiredOnlyContext.putWhereParameter(FieldConstant.DATA_ID, "");
        requiredOnlyContext.putWhereParameter(FieldConstant.GROUP_ID, "");
        requiredOnlyContext.putWhereParameter(FieldConstant.APP_NAME, "");
        requiredOnlyContext.putWhereParameter(FieldConstant.CONTENT, "");
        assertResult(configInfoMapper.findConfigInfo4PageFetchRows(requiredOnlyContext),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,type,"
                + "encrypted_data_key,c_desc FROM config_info WHERE  tenant_id=? "
                + "  LIMIT 2 , 5",
            tenantId);
    }
    
    @Test
    void testConfigInfoMapperFindLike4PageFetchRows() {
        assertResult(configInfoMapper.findConfigInfoLike4PageFetchRows(context),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,encrypted_data_key,"
                + "type,c_desc FROM config_info WHERE  tenant_id LIKE ? "
                + " AND data_id LIKE ?  AND group_id LIKE ?  AND app_name = ? "
                + " AND content LIKE ?   LIMIT 2 , 5",
            tenantId, dataId, groupId, appName, content);
        
        MapperContext tenantOnlyContext = new MapperContext(startRow, pageSize);
        tenantOnlyContext.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        tenantOnlyContext.putWhereParameter(FieldConstant.DATA_ID, "");
        tenantOnlyContext.putWhereParameter(FieldConstant.GROUP_ID, "");
        tenantOnlyContext.putWhereParameter(FieldConstant.APP_NAME, "");
        tenantOnlyContext.putWhereParameter(FieldConstant.CONTENT, "");
        assertResult(configInfoMapper.findConfigInfoLike4PageFetchRows(tenantOnlyContext),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,encrypted_data_key,"
                + "type,c_desc FROM config_info WHERE  tenant_id LIKE ?   LIMIT 2 , 5",
            tenantId);
    }
    
    @Test
    void testConfigTagsRelationMapper() {
        TestConfigTagsRelationMapper mapper = new TestConfigTagsRelationMapper();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, mapper.getTableName());
        assertEquals("SELECT  LIMIT 1 , 3", mapper.getLimitPageSqlWithOffset("SELECT", 1, 3));
        assertEquals(FUNCTION_PREFIX + "NOW()", mapper.getFunction("NOW()"));
        
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tag1", "tag2"});
        assertResult(mapper.findConfigInfo4PageFetchRows(context),
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM "
                + "config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE  a.tenant_id=?  AND a.data_id=?  AND a.group_id=? "
                + " AND a.app_name=?  AND a.content LIKE ?  AND b.tag_name IN (?, ?) "
                + "  LIMIT 2 , 5",
            tenantId, dataId, groupId, appName, content, "tag1", "tag2");
        assertResult(mapper.findConfigInfoLike4PageFetchRows(context),
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + " WHERE  a.tenant_id LIKE ?  AND a.data_id LIKE ? "
                + " AND a.group_id LIKE ?  AND a.app_name = ?  AND a.content LIKE ? "
                + " AND b.tag_name IN (?, ?)   LIMIT 2 , 5",
            tenantId, dataId, groupId, appName, content, "tag1", "tag2");
        
        MapperContext minimalContext = new MapperContext(startRow, pageSize);
        minimalContext.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        minimalContext.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tag"});
        assertResult(mapper.findConfigInfo4PageFetchRows(minimalContext),
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM "
                + "config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "WHERE  a.tenant_id=?  AND b.tag_name IN (?)   LIMIT 2 , 5",
            tenantId, "tag");
        assertResult(mapper.findConfigInfoLike4PageFetchRows(minimalContext),
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content "
                + "FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id "
                + " WHERE  a.tenant_id LIKE ?  AND b.tag_name IN (?)   LIMIT 2 , 5",
            tenantId, "tag");
    }
    
    @Test
    void testOtherBaseMappers() {
        TestTenantCapacityMapper tenantCapacityMapper = new TestTenantCapacityMapper();
        assertResult(tenantCapacityMapper.getCapacityList4CorrectUsage(context),
            "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ? ", id, pageSize);
        assertEquals(FUNCTION_PREFIX + "NOW()", tenantCapacityMapper.getFunction("NOW()"));
        
        TestGroupCapacityMapper groupCapacityMapper = new TestGroupCapacityMapper();
        assertResult(groupCapacityMapper.selectGroupInfoBySize(context),
            "SELECT id, group_id FROM group_capacity WHERE id > ? LIMIT ? ", id, pageSize);
        assertEquals(FUNCTION_PREFIX + "NOW()", groupCapacityMapper.getFunction("NOW()"));
        
        TestTenantInfoMapper tenantInfoMapper = new TestTenantInfoMapper();
        assertEquals(FUNCTION_PREFIX + "NOW()", tenantInfoMapper.getFunction("NOW()"));
    }
    
    private MapperContext newContext() {
        MapperContext result = new MapperContext(startRow, pageSize);
        result.putWhereParameter(FieldConstant.APP_NAME, appName);
        result.putWhereParameter(FieldConstant.DATA_ID, dataId);
        result.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        result.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        result.putWhereParameter(FieldConstant.CONTENT, content);
        result.putWhereParameter(FieldConstant.ID, id);
        result.putWhereParameter(FieldConstant.START_TIME, startTime);
        result.putWhereParameter(FieldConstant.END_TIME, endTime);
        result.putWhereParameter(FieldConstant.LAST_MAX_ID, 7L);
        result.putWhereParameter(FieldConstant.LIMIT_SIZE, pageSize);
        return result;
    }
    
    private static void assertResult(MapperResult result, String sql, Object... parameters) {
        assertEquals(normalizeSql(sql), normalizeSql(result.getSql()));
        assertArrayEquals(parameters, result.getParamList().toArray());
    }
    
    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
    
    private static class TestDatabaseDialect extends AbstractDatabaseDialect {
        
        @Override
        public String getType() {
            return DATA_SOURCE;
        }
        
        @Override
        public String getFunction(String functionName) {
            return FUNCTION_PREFIX + functionName;
        }
    }
    
    private static class TestConfigInfoMapper extends BaseConfigInfoMapper {
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE;
        }
    }
    
    private static class TestConfigTagsRelationMapper extends BaseConfigTagsRelationMapper {
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE;
        }
    }
    
    private static class TestTenantCapacityMapper extends BaseTenantCapacityMapper {
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE;
        }
        
        @Override
        public MapperResult select(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsageWithDefaultQuotaLimit(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsageWithQuotaLimit(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsage(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult decrementUsage(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult correctUsage(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult insertTenantCapacity(MapperContext context) {
            return null;
        }
    }
    
    private static class TestGroupCapacityMapper extends BaseGroupCapacityMapper {
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE;
        }
        
        @Override
        public MapperResult select(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult insertIntoSelect(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult insertIntoSelectByWhere(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsageByWhereQuotaEqualZero(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsageByWhereQuotaNotEqualZero(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult incrementUsageByWhere(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult decrementUsageByWhere(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult updateUsage(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult updateUsageByWhere(MapperContext context) {
            return null;
        }
    }
    
    private static class TestTenantInfoMapper extends BaseTenantInfoMapper {
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE;
        }
    }
}
