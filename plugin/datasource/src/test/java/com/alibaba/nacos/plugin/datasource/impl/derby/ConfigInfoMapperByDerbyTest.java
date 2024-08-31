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

package com.alibaba.nacos.plugin.datasource.impl.derby;

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

class ConfigInfoMapperByDerbyTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    long lastMaxId = 123;
    
    String appName = "appName";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String id = "123";
    
    List<Long> ids = Lists.newArrayList(1L, 2L, 3L, 5L, 144L);
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigInfoMapperByDerby configInfoMapperByDerby;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoMapperByDerby = new ConfigInfoMapperByDerby();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.ID, id);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.IDS, ids);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
    }
    
    @Test
    void testFindConfigMaxId() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigMaxId(null);
        assertEquals("SELECT MAX(id) FROM config_info", mapperResult.getSql());
    }
    
    @Test
    void testFindAllDataIdAndGroup() {
        MapperResult mapperResult = configInfoMapperByDerby.findAllDataIdAndGroup(null);
        assertEquals("SELECT DISTINCT data_id, group_id FROM config_info", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoByAppCountRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoByAppCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoByAppFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoByAppFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE"
                + " ? AND app_name = ? OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testConfigInfoLikeTenantCount() {
        MapperResult mapperResult = configInfoMapperByDerby.configInfoLikeTenantCount(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTenantIdList() {
        MapperResult mapperResult = configInfoMapperByDerby.getTenantIdList(context);
        assertEquals(mapperResult.getSql(),
                "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id OFFSET " + startRow + " ROWS FETCH NEXT "
                        + pageSize + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testGetGroupIdList() {
        MapperResult mapperResult = configInfoMapperByDerby.getGroupIdList(context);
        assertEquals(mapperResult.getSql(),
                "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize
                        + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigKey() {
        MapperResult mapperResult = configInfoMapperByDerby.findAllConfigKey(context);
        assertEquals(mapperResult.getSql(),
                " SELECT data_id,group_id,app_name FROM  ( SELECT id FROM config_info WHERE tenant_id LIKE" + " ? ORDER BY id OFFSET "
                        + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY ) g, config_info t  WHERE g.id = t.id ");
        assertArrayEquals(new Object[] {tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoBaseFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findAllConfigInfoBaseFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT t.id,data_id,group_id,content,md5  FROM ( SELECT id FROM config_info ORDER BY " + "id OFFSET " + startRow
                        + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY )   g, config_info t WHERE g.id = t.id ");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //with content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        MapperResult mapperResult = configInfoMapperByDerby.findAllConfigInfoFragment(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type FROM config_info "
                + "WHERE id > ? ORDER BY id ASC OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {id}, mapperResult.getParamList().toArray());
        //with out content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        
        MapperResult mapperResult2 = configInfoMapperByDerby.findAllConfigInfoFragment(context);
        assertEquals(mapperResult2.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type FROM config_info "
                + "WHERE id > ? ORDER BY id ASC OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {id}, mapperResult2.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfig() {
        MapperResult mapperResult = configInfoMapperByDerby.findChangeConfig(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id, data_id, group_id, tenant_id, app_name, content, gmt_modified, encrypted_data_key FROM config_info "
                        + "WHERE gmt_modified >= ? and id > ? order by id OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
        assertArrayEquals(new Object[] {startTime, lastMaxId, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigCountRows() {
        
        MapperResult mapperResult = configInfoMapperByDerby.findChangeConfigCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  1=1  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=? ",
                mapperResult.getSql());
        assertArrayEquals(new Object[] {appName, startTime, endTime}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindChangeConfigFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findChangeConfigFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info "
                + "WHERE  1=1  AND app_name = ?  AND gmt_modified >=?  AND gmt_modified <=?  OFFSET " + startRow + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {appName, startTime, endTime}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testListGroupKeyMd5ByPageFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.listGroupKeyMd5ByPageFetchRows(context);
        assertEquals(mapperResult.getSql(),
                " SELECT t.id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM ( SELECT id FROM config_info "
                        + "ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize
                        + " ROWS ONLY ) g, config_info t WHERE g.id = t.id");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
        
    }
    
    @Test
    void testFindAllConfigInfo4Export() {
        MapperResult mapperResult = configInfoMapperByDerby.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  id IN (?, ?, ?, ?, ?) ");
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
        
        context.putWhereParameter(FieldConstant.IDS, null);
        mapperResult = configInfoMapperByDerby.findAllConfigInfo4Export(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ?  AND app_name= ? ");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
        
    }
    
    @Test
    void testFindConfigInfoBaseLikeCountRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoBaseLikeCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='' ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfoBaseLikeFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoBaseLikeFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id=''  " + "OFFSET " + startRow
                        + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testFindConfigInfo4PageCountRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfo4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE  tenant_id=?  AND app_name=? ", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfo4PageFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type FROM config_info WHERE  tenant_id=?  AND app_name=? "
                        + " OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseByGroupFetchRows() {
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoBaseByGroupFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? " + "OFFSET " + startRow
                        + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoLike4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info WHERE tenant_id LIKE ?  AND app_name = ? ", mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfoLike4PageFetchRows(context);
        assertEquals(mapperResult.getSql(), "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key,type FROM config_info "
                + "WHERE tenant_id LIKE ?  AND app_name = ?  OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {tenantId, appName}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoFetchRows() {
        MapperResult mapperResult = configInfoMapperByDerby.findAllConfigInfoFetchRows(context);
        assertEquals(mapperResult.getSql(),
                " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM ( SELECT id FROM config_info  "
                        + "WHERE tenant_id LIKE ? ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ) g, config_info t  WHERE g.id = t.id ");
        assertArrayEquals(new Object[] {tenantId, startRow, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfosByIds() {
        MapperResult mapperResult = configInfoMapperByDerby.findConfigInfosByIds(context);
        assertEquals("SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ",
                mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testRemoveConfigInfoByIdsAtomic() {
        MapperResult mapperResult = configInfoMapperByDerby.removeConfigInfoByIdsAtomic(context);
        assertEquals("DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ", mapperResult.getSql());
        assertArrayEquals(mapperResult.getParamList().toArray(), ids.toArray());
    }
    
    @Test
    void testGetTableName() {
        String sql = configInfoMapperByDerby.getTableName();
        assertEquals(TableConstant.CONFIG_INFO, sql);
    }
    
    @Test
    void testGetDataSource() {
        String sql = configInfoMapperByDerby.getDataSource();
        assertEquals(DataSourceConstant.DERBY, sql);
    }
    
    @Test
    void testUpdateConfigInfoAtomicCas() {
        String newContent = "new Content";
        String newMD5 = "newMD5";
        String srcIp = "1.1.1.1";
        Object srcUser = "nacos";
        Object appNameTmp = "newAppName";
        Object desc = "description";
        Object use = "use";
        Object effect = "effect";
        Object type = "type";
        Object schema = "schema";
        String encrypedDataKey = "key5678";
        context.putUpdateParameter(FieldConstant.CONTENT, newContent);
        context.putUpdateParameter(FieldConstant.MD5, newMD5);
        context.putUpdateParameter(FieldConstant.SRC_IP, srcIp);
        context.putUpdateParameter(FieldConstant.SRC_USER, srcUser);
        context.putUpdateParameter(FieldConstant.APP_NAME, appNameTmp);
        context.putUpdateParameter(FieldConstant.C_DESC, desc);
        context.putUpdateParameter(FieldConstant.C_USE, use);
        context.putUpdateParameter(FieldConstant.EFFECT, effect);
        context.putUpdateParameter(FieldConstant.TYPE, type);
        context.putUpdateParameter(FieldConstant.C_SCHEMA, schema);
        context.putUpdateParameter(FieldConstant.ENCRYPTED_DATA_KEY, encrypedDataKey);
        Object dataId = "dataId00";
        Object group = "group";
        Object md5 = "md5";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, group);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.MD5, md5);
        
        MapperResult mapperResult = configInfoMapperByDerby.updateConfigInfoAtomicCas(context);
        assertEquals(mapperResult.getSql(),
                "UPDATE config_info SET " + "content=?, md5=?, src_ip=?, src_user=?, gmt_modified=CURRENT_TIMESTAMP,"
                        + " app_name=?, c_desc=?, c_use=?, effect=?, type=?, c_schema=?, encrypted_data_key=? "
                        + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')");
        assertArrayEquals(
                new Object[]{newContent, newMD5, srcIp, srcUser, appNameTmp, desc, use, effect, type, schema, encrypedDataKey,
                        dataId, group, tenantId, md5}, mapperResult.getParamList().toArray());
    }
}
