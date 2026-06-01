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

import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInfoMapperByOracleTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String appName = "appName";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String dataId = "dataId";
    
    String id = "123";
    
    long lastMaxId = 1234;
    
    List<Long> ids = Lists.newArrayList(1L, 2L, 3L, 5L, 144L);
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigInfoMapperByOracle configInfoMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoMapperByOracle = new ConfigInfoMapperByOracle();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.ID, id);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.IDS, ids);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
    }
    
    @Test
    void testFindConfigMaxId() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigMaxId(null);
        assertEquals("SELECT MAX(id) FROM config_info", mapperResult.getSql());
    }
    
    @Test
    void testFindAllDataIdAndGroup() {
        MapperResult mapperResult = configInfoMapperByOracle.findAllDataIdAndGroup(null);
        assertEquals("SELECT DISTINCT data_id, group_id FROM config_info", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoByAppCountRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigInfoByAppCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoByAppFetchRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigInfoByAppFetchRows(context);
        assertTrue(mapperResult.getSql()
            .contains("SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info"));
        assertTrue(mapperResult.getSql().contains(
            "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testConfigInfoLikeTenantCount() {
        MapperResult mapperResult = configInfoMapperByOracle.configInfoLikeTenantCount(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTenantIdList() {
        MapperResult mapperResult = configInfoMapperByOracle.getTenantIdList(context);
        assertTrue(mapperResult.getSql().contains("SELECT tenant_id FROM config_info"));
        assertTrue(mapperResult.getSql().contains("GROUP BY tenant_id ORDER BY tenant_id"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
    }
    
    @Test
    void testGetGroupIdList() {
        MapperResult mapperResult = configInfoMapperByOracle.getGroupIdList(context);
        assertTrue(mapperResult.getSql().contains("SELECT group_id FROM config_info"));
        assertTrue(mapperResult.getSql().contains("GROUP BY group_id ORDER BY group_id"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
    }
    
    @Test
    void testFindAllConfigKey() {
        MapperResult mapperResult = configInfoMapperByOracle.findAllConfigKey(context);
        assertTrue(mapperResult.getSql().contains("SELECT data_id,group_id,app_name"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoBaseFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.findAllConfigInfoBaseFetchRows(context);
        assertTrue(mapperResult.getSql().contains("SELECT t.id,data_id,group_id,content,md5"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //with content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        
        MapperResult mapperResult = configInfoMapperByOracle.findAllConfigInfoFragment(context);
        assertTrue(mapperResult.getSql().contains(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {id}, mapperResult.getParamList().toArray());
        
        //without content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        MapperResult mapperResult2 = configInfoMapperByOracle.findAllConfigInfoFragment(context);
        assertTrue(mapperResult2.getSql().contains(
            "SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type,encrypted_data_key"));
        assertTrue(mapperResult2.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {id}, mapperResult2.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfig() {
        MapperResult mapperResult = configInfoMapperByOracle.findChangeConfig(context);
        assertEquals(
            "SELECT id, data_id, group_id, tenant_id, app_name,md5, gmt_modified, encrypted_data_key FROM config_info WHERE "
                + "gmt_modified >= ? and id > ? order by id fetch first ? rows only",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime, lastMaxId, pageSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigCountRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findChangeConfigCountRows(context);
        assertEquals(
            "SELECT count(*) FROM config_info WHERE  1=1  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=? ",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {appName, startTime, endTime},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigFetchRows() {
        Object lastMaxId = 100;
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        MapperResult mapperResult = configInfoMapperByOracle.findChangeConfigFetchRows(context);
        assertTrue(mapperResult.getSql().contains(
            "SELECT id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM config_info"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + 0 + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId, appName, startTime, endTime},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testListGroupKeyMd5ByPageFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.listGroupKeyMd5ByPageFetchRows(context);
        assertTrue(mapperResult.getSql().contains(
            "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key"));
        assertTrue(mapperResult.getSql()
            .contains("OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigInfo4Export() {
        MapperResult mapperResult = configInfoMapperByOracle.findAllConfigInfo4Export(context);
        assertTrue(mapperResult.getSql().contains(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified"));
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
        
        context.putWhereParameter(FieldConstant.IDS, null);
        mapperResult = configInfoMapperByOracle.findAllConfigInfo4Export(context);
        assertTrue(mapperResult.getSql().contains(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified"));
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseLikeCountRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.findConfigInfoBaseLikeCountRows(context);
        assertTrue(mapperResult.getSql().contains("SELECT count(*) FROM config_info"));
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoBaseLikeFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.findConfigInfoBaseLikeFetchRows(context);
        assertTrue(mapperResult.getSql()
            .contains("SELECT id,data_id,group_id,tenant_id,content FROM config_info"));
        assertTrue(mapperResult.getSql().contains(
            "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigInfo4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  tenant_id=?  AND app_name=? ",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigInfo4PageFetchRows(context);
        // Verify the optimized SQL structure with Oracle-specific syntax
        String sql = mapperResult.getSql();
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("LISTAGG"));
        assertTrue(sql.contains(
            "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseByGroupFetchRows() {
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        MapperResult mapperResult =
            configInfoMapperByOracle.findConfigInfoBaseByGroupFetchRows(context);
        assertTrue(
            mapperResult.getSql().contains("SELECT id,data_id,group_id,content FROM config_info"));
        assertTrue(mapperResult.getSql().contains("WHERE group_id=? AND tenant_id=?"));
        assertTrue(mapperResult.getSql().contains(
            "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.findConfigInfoLike4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?  AND app_name = ? ",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByOracle.findConfigInfoLike4PageFetchRows(context);
        // Verify the optimized SQL structure with Oracle-specific syntax
        String sql = mapperResult.getSql();
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("LISTAGG"));
        assertTrue(sql.contains(
            "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoFetchRows() {
        MapperResult mapperResult = configInfoMapperByOracle.findAllConfigInfoFetchRows(context);
        assertTrue(mapperResult.getSql()
            .contains("SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5"));
        assertTrue(mapperResult.getSql().contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertArrayEquals(new Object[] {tenantId, startRow, pageSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfosByIds() {
        MapperResult mapperResult = configInfoMapperByOracle.findConfigInfosByIds(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ",
            mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomic() {
        MapperResult mapperResult = configInfoMapperByOracle.removeConfigInfoByIdsAtomic(context);
        assertEquals("DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoMapperByOracle.getTableName();
        assertEquals(TableConstant.CONFIG_INFO, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
    
    @Test
    void testFindConfigInfo4PageFetchRowsWithDescAndTags() {
        ConfigInfoMapperByOracle mapper = new ConfigInfoMapperByOracle();
        MapperContext context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, "test.properties");
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, "key=value");
        
        MapperResult mapperResult = mapper.findConfigInfo4PageFetchRows(context);
        String sql = mapperResult.getSql();
        List<Object> paramList = mapperResult.getParamList();
        
        // Verify the optimized SQL structure with Oracle-specific syntax
        assertTrue(sql.contains("c_desc"));
        assertTrue(sql.contains("LISTAGG"));
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("OFFSET"));
        assertTrue(sql.contains("FETCH NEXT"));
        
        // Verify parameters
        assertEquals(5, paramList.size());
        assertEquals(tenantId, paramList.get(0));
        assertEquals("test.properties", paramList.get(1));
        assertEquals(groupId, paramList.get(2));
        assertEquals(appName, paramList.get(3));
        assertEquals("key=value", paramList.get(4));
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRowsWithDescAndTags() {
        ConfigInfoMapperByOracle mapper = new ConfigInfoMapperByOracle();
        MapperContext context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, "test");
        context.putWhereParameter(FieldConstant.GROUP_ID, "DEFAULT");
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.CONTENT, "key");
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"properties", "yaml"});
        
        MapperResult mapperResult = mapper.findConfigInfoLike4PageFetchRows(context);
        String sql = mapperResult.getSql();
        List<Object> paramList = mapperResult.getParamList();
        
        // Verify the optimized SQL structure with Oracle-specific syntax
        assertTrue(sql.contains("c_desc"));
        assertTrue(sql.contains("LISTAGG"));
        assertTrue(sql.contains("WITH tag_agg AS"));
        assertTrue(sql.contains("LIKE"));
        assertTrue(sql.contains("OFFSET"));
        assertTrue(sql.contains("FETCH NEXT"));
        
        // Verify parameters
        assertEquals(7, paramList.size());
        assertEquals(tenantId, paramList.get(0));
        assertEquals("test", paramList.get(1));
        assertEquals("DEFAULT", paramList.get(2));
        assertEquals(appName, paramList.get(3));
        assertEquals("key", paramList.get(4));
        assertEquals("properties", paramList.get(5));
        assertEquals("yaml", paramList.get(6));
    }
}
