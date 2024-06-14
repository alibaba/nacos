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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.common.utils.NamespaceUtil;
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

class ConfigInfoMapperByMySqlTest {
    
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
    
    private ConfigInfoMapperByMySql configInfoMapperByMySql;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoMapperByMySql = new ConfigInfoMapperByMySql();
        
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
        MapperResult mapperResult = configInfoMapperByMySql.findConfigMaxId(null);
        assertEquals("SELECT MAX(id) FROM config_info", mapperResult.getSql());
    }
    
    @Test
    void testFindAllDataIdAndGroup() {
        MapperResult mapperResult = configInfoMapperByMySql.findAllDataIdAndGroup(null);
        assertEquals("SELECT DISTINCT data_id, group_id FROM config_info", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoByAppCountRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoByAppCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoByAppFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoByAppFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND app_name= ? LIMIT "
                        + startRow + "," + pageSize);
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testConfigInfoLikeTenantCount() {
        MapperResult mapperResult = configInfoMapperByMySql.configInfoLikeTenantCount(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTenantIdList() {
        MapperResult mapperResult = configInfoMapperByMySql.getTenantIdList(context);
        assertEquals(mapperResult.getSql(), "SELECT tenant_id FROM config_info WHERE tenant_id != '" + NamespaceUtil.getNamespaceDefaultId()
                + "' GROUP BY tenant_id LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testGetGroupIdList() {
        MapperResult mapperResult = configInfoMapperByMySql.getGroupIdList(context);
        assertEquals(mapperResult.getSql(),
                "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigKey() {
        MapperResult mapperResult = configInfoMapperByMySql.findAllConfigKey(context);
        assertEquals(mapperResult.getSql(),
                " SELECT data_id,group_id,app_name  FROM ( " + " SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT "
                        + context.getStartRow() + "," + context.getPageSize() + " )" + " g, config_info t WHERE g.id = t.id  ");
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoBaseFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findAllConfigInfoBaseFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT t.id,data_id,group_id,content,md5 FROM ( SELECT id FROM config_info ORDER BY id LIMIT " + context.getStartRow()
                        + "," + context.getPageSize() + " ) g, config_info t  WHERE g.id = t.id ");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //with content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        
        MapperResult mapperResult = configInfoMapperByMySql.findAllConfigInfoFragment(context);
        assertEquals("SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC LIMIT " + startRow + "," + pageSize, mapperResult.getSql());
        assertArrayEquals(new Object[] {id}, mapperResult.getParamList().toArray());
        
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        MapperResult mapperResult2 = configInfoMapperByMySql.findAllConfigInfoFragment(context);
        assertEquals("SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC LIMIT " + startRow + "," + pageSize, mapperResult2.getSql());
        assertArrayEquals(new Object[] {id}, mapperResult2.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfig() {
        MapperResult mapperResult = configInfoMapperByMySql.findChangeConfig(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id, data_id, group_id, tenant_id, app_name,md5, gmt_modified, encrypted_data_key FROM config_info"
                        + " WHERE gmt_modified >= ? and id > ? order by id  limit ? ");
        assertArrayEquals(new Object[] {startTime, lastMaxId, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigCountRows() {
        
        MapperResult mapperResult = configInfoMapperByMySql.findChangeConfigCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  1=1  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=? ",
                mapperResult.getSql());
        assertArrayEquals(new Object[] {appName, startTime, endTime}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigFetchRows() {
        Object lastMaxId = 100;
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        MapperResult mapperResult = configInfoMapperByMySql.findChangeConfigFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM config_info "
                + "WHERE  1=1  AND tenant_id = ?  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=?  AND id > " + lastMaxId
                + " ORDER BY id ASC LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(new Object[] {tenantId, appName, startTime, endTime}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testListGroupKeyMd5ByPageFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.listGroupKeyMd5ByPageFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
                + "( SELECT id FROM config_info ORDER BY id LIMIT 0,5 ) g, config_info t WHERE g.id = t.id");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
        
    }
    
    @Test
    void testFindAllConfigInfo4Export() {
        MapperResult mapperResult = configInfoMapperByMySql.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  id IN (?, ?, ?, ?, ?) ");
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
        
        context.putWhereParameter(FieldConstant.IDS, null);
        mapperResult = configInfoMapperByMySql.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ?  AND app_name= ? ");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseLikeCountRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoBaseLikeCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='' ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoBaseLikeFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoBaseLikeFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id=''  LIMIT " + startRow + ","
                        + pageSize);
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfo4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  tenant_id=?  AND app_name=? ", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfo4PageFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key FROM config_info"
                + " WHERE  tenant_id=?  AND app_name=?  LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseByGroupFetchRows() {
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoBaseByGroupFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(new Object[] {groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoLike4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  tenant_id LIKE ?  AND app_name = ? ", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfoLike4PageFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info "
                + "WHERE  tenant_id LIKE ?  AND app_name = ?  LIMIT " + startRow + "," + pageSize);
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoFetchRows() {
        MapperResult mapperResult = configInfoMapperByMySql.findAllConfigInfoFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM (  SELECT id FROM config_info "
                        + "WHERE tenant_id LIKE ? ORDER BY id LIMIT ?,? ) g, config_info t  WHERE g.id = t.id ");
        assertArrayEquals(new Object[] {tenantId, startRow, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfosByIds() {
        MapperResult mapperResult = configInfoMapperByMySql.findConfigInfosByIds(context);
        assertEquals("SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ",
                mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomic() {
        MapperResult mapperResult = configInfoMapperByMySql.removeConfigInfoByIdsAtomic(context);
        assertEquals("DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testGetTableName() {
        String sql = configInfoMapperByMySql.getTableName();
        assertEquals(TableConstant.CONFIG_INFO, sql);
    }
    
    @Test
    void testGetDataSource() {
        String sql = configInfoMapperByMySql.getDataSource();
        assertEquals(DataSourceConstant.MYSQL, sql);
    }
    
    @Test
    void testUpdateConfigInfoAtomicCas() {
        String newContent = "new Content";
        String newMD5 = "newMD5";
        String srcIp = "1.1.1.1";
        Object srcUser = "nacos";
        Object time = new Timestamp(System.currentTimeMillis());
        Object appNameTmp = "newAppName";
        Object desc = "description";
        Object use = "use";
        Object effect = "effect";
        Object type = "type";
        Object schema = "schema";
        String encryptedDataKey = "ey456789";
        context.putUpdateParameter(FieldConstant.CONTENT, newContent);
        context.putUpdateParameter(FieldConstant.MD5, newMD5);
        context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
        context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, time);
        context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
        context.putUpdateParameter(FieldConstant.C_DESC, desc);
        context.putUpdateParameter(FieldConstant.C_USE, use);
        context.putUpdateParameter(FieldConstant.EFFECT, effect);
        context.putUpdateParameter(FieldConstant.TYPE, type);
        context.putUpdateParameter(FieldConstant.C_SCHEMA, schema);
        context.putUpdateParameter(FieldConstant.ENCRYPTED_DATA_KEY, encryptedDataKey);
        Object dataId = "dataId";
        Object group = "group";
        Object md5 = "md5";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.MD5, md5);
        
        MapperResult mapperResult = configInfoMapperByMySql.updateConfigInfoAtomicCas(context);
        assertEquals(mapperResult.getSql(),
                "UPDATE config_info SET " + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,"
                        + "c_use=?,effect=?,type=?,c_schema=?,encrypted_data_key=? "
                        + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')");
        assertArrayEquals(
                new Object[] {newContent, newMD5, srcIp, srcUser, time, appNameTmp, desc, use, effect, type, schema, encryptedDataKey,
                        dataId, group, tenantId, md5}, mapperResult.getParamList().toArray());
    }
}
