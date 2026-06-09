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

import java.sql.Timestamp;
import java.util.List;

import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInfoMapperBySqlServerTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String appName = "appName";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String id = "123";
    
    long lastMaxId = 1234;
    
    List<Long> ids = Lists.newArrayList(1L, 2L, 3L, 5L, 144L);
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigInfoMapperBySqlServer configInfoMapperBySqlServer;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoMapperBySqlServer = new ConfigInfoMapperBySqlServer();
        
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
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigMaxId(null);
        assertEquals("SELECT MAX(id) FROM config_info", mapperResult.getSql());
    }
    
    @Test
    void testFindAllDataIdAndGroup() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllDataIdAndGroup(null);
        assertEquals("SELECT DISTINCT data_id, group_id FROM config_info", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoByAppCountRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigInfoByAppCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoByAppFetchRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigInfoByAppFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info"
                + " WHERE tenant_id LIKE ? AND app_name= ? "
                + " ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testConfigInfoLikeTenantCount() {
        MapperResult mapperResult = configInfoMapperBySqlServer.configInfoLikeTenantCount(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTenantIdList() {
        MapperResult mapperResult = configInfoMapperBySqlServer.getTenantIdList(context);
        assertEquals(mapperResult.getSql(),
            "SELECT tenant_id FROM config_info WHERE tenant_id != '" + NamespaceUtil.getNamespaceDefaultId()
                + "' GROUP BY tenant_id " + " ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testGetGroupIdList() {
        MapperResult mapperResult = configInfoMapperBySqlServer.getGroupIdList(context);
        assertEquals(mapperResult.getSql(),
            "SELECT group_id FROM config_info WHERE tenant_id ='" + NamespaceUtil.getNamespaceDefaultId()
                + "' GROUP BY group_id " + " ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigKey() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllConfigKey(context);
        assertEquals(mapperResult.getSql(),
            " SELECT data_id,group_id,app_name  FROM ( "
                + " SELECT id FROM config_info WHERE tenant_id LIKE ? " + " ORDER BY id OFFSET " + context.getStartRow()
                + " ROWS FETCH NEXT " + context.getPageSize() + " ROWS ONLY)" + " g, config_info t WHERE g.id = t.id  ");
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoBaseFetchRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllConfigInfoBaseFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT t.id,data_id,group_id,content,md5"
                + " FROM ( SELECT id FROM config_info ORDER BY id  OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ) "
                + " g, config_info t  WHERE g.id = t.id ");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //with content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllConfigInfoFragment(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC OFFSET " + startRow + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {id}, mapperResult.getParamList().toArray());
        
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        MapperResult mapperResult2 = configInfoMapperBySqlServer.findAllConfigInfoFragment(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC OFFSET " + startRow + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY",
            mapperResult2.getSql());
        assertArrayEquals(new Object[] {id}, mapperResult2.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfig() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findChangeConfig(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id, data_id, group_id, tenant_id, app_name,md5, gmt_modified, encrypted_data_key FROM config_info"
                + " WHERE gmt_modified >= ? and id > ? order by id  OFFSET " + 0 + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY ");
        assertArrayEquals(new Object[] {startTime, lastMaxId},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigCountRows() {
        
        MapperResult mapperResult = configInfoMapperBySqlServer.findChangeConfigCountRows(context);
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
        MapperResult mapperResult = configInfoMapperBySqlServer.findChangeConfigFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM config_info "
                + "WHERE  1=1  AND tenant_id = ?  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=?  AND id > "
                + lastMaxId
                + " ORDER BY id ASC OFFSET " + 0 + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {tenantId, appName, startTime, endTime},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testListGroupKeyMd5ByPageFetchRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.listGroupKeyMd5ByPageFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
                + "( SELECT id FROM config_info ORDER BY id OFFSET " + context.getStartRow() + " ROWS FETCH NEXT "
                + context.getPageSize() + " ROWS ONLY ) g, config_info t WHERE g.id = t.id");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
        
    }
    
    @Test
    void testFindAllConfigInfo4Export() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  id IN (?, ?, ?, ?, ?) ");
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
        
        context.putWhereParameter(FieldConstant.IDS, null);
        mapperResult = configInfoMapperBySqlServer.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ?  AND app_name= ? ");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseLikeCountRows() {
        MapperResult mapperResult =
            configInfoMapperBySqlServer.findConfigInfoBaseLikeCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='public' ",
            mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoBaseLikeFetchRows() {
        MapperResult mapperResult =
            configInfoMapperBySqlServer.findConfigInfoBaseLikeFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id='public'  ORDER BY id ASC"
                + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigInfo4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  tenant_id=?  AND app_name=? ",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigInfo4PageFetchRows(context);
        // 验证新的优化后的 SQL 结构：先分页再 JOIN，使用 SQL Server 语法
        String expectedInnerSql =
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,type,encrypted_data_key,c_desc FROM config_info "
                + "WHERE tenant_id=? AND app_name=? ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        String expectedSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc,"
                + "STRING_AGG(b.tag_name, ',') WITHIN GROUP (ORDER BY b.tag_name) as config_tags "
                + "FROM (" + expectedInnerSql + ") a "
                + "LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "GROUP BY a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc";
        assertEquals(expectedSql, mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseByGroupFetchRows() {
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        MapperResult mapperResult =
            configInfoMapperBySqlServer.findConfigInfoBaseByGroupFetchRows(context);
        String expectedSql =
            "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? "
                + "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        assertEquals(mapperResult.getSql(), expectedSql);
        assertArrayEquals(new Object[] {groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configInfoMapperBySqlServer.findConfigInfoLike4PageFetchRows(context);
        // 验证新的优化后的 SQL 结构：先分页再 JOIN，使用 SQL Server 语法
        String expectedInnerSql =
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,encrypted_data_key,type,c_desc,gmt_modified"
                + " FROM config_info WHERE tenant_id LIKE ? AND app_name = ? ORDER BY id OFFSET "
                + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        String expectedSql =
            "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,"
                + "a.encrypted_data_key,a.type,a.c_desc,a.gmt_modified,"
                + "STRING_AGG(b.tag_name, ',') WITHIN GROUP (ORDER BY b.tag_name) as config_tags "
                + "FROM (" + expectedInnerSql + ") a "
                + "LEFT JOIN config_tags_relation b ON a.id=b.id "
                + "GROUP BY a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content,a.md5,"
                + "a.encrypted_data_key,a.type,a.c_desc,a.gmt_modified";
        assertEquals(expectedSql, mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoFetchRows() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findAllConfigInfoFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
                + " FROM ( SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY )"
                + " g, config_info t WHERE g.id = t.id ");
        assertArrayEquals(new Object[] {tenantId, startRow, pageSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfosByIds() {
        MapperResult mapperResult = configInfoMapperBySqlServer.findConfigInfosByIds(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ",
            mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomic() {
        MapperResult mapperResult = configInfoMapperBySqlServer.removeConfigInfoByIdsAtomic(context);
        assertEquals("DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testGetTableName() {
        String sql = configInfoMapperBySqlServer.getTableName();
        assertEquals(TableConstant.CONFIG_INFO, sql);
    }
    
    @Test
    void testGetDataSource() {
        String sql = configInfoMapperBySqlServer.getDataSource();
        assertEquals(DatabaseTypeConstant.SQLSERVER, sql);
    }
}
