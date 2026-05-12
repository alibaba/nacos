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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ConfigInfoMapperByPostgresqlTest
 *
 * @author Ken
 */
class ConfigInfoMapperByPostgresqlTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String appName = "appName";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String dataId = "dataId";
    
    String id = "id";
    
    MapperContext context;
    
    private ConfigInfoMapperByPostgresql configInfoMapperByPostgresql;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoMapperByPostgresql = new ConfigInfoMapperByPostgresql();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.ID, id);
        
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        
    }
    
    @Test
    void testFindAllConfigInfoFragment() {
        //with content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "true");
        
        MapperResult needContentMapperResult =
            configInfoMapperByPostgresql.findAllConfigInfoFragment(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC   OFFSET " + startRow + " LIMIT "
                + pageSize,
            needContentMapperResult.getSql());
        assertArrayEquals(new Object[] {id}, needContentMapperResult.getParamList().toArray());
        
        //without content
        context.putContextParameter(ContextConstant.NEED_CONTENT, "false");
        MapperResult withoutContentMapperResult =
            configInfoMapperByPostgresql.findAllConfigInfoFragment(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,md5,gmt_modified,type,encrypted_data_key "
                + "FROM config_info WHERE id > ? ORDER BY id ASC   OFFSET " + startRow + " LIMIT "
                + pageSize,
            withoutContentMapperResult.getSql());
        assertArrayEquals(new Object[] {id}, withoutContentMapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByPostgresql.findConfigInfo4PageFetchRows(context);
        
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,type,encrypted_data_key,c_desc "
                + "FROM config_info WHERE  tenant_id=?  AND data_id=?  AND group_id=?  AND app_name=?   "
                + "OFFSET " + startRow + " LIMIT " + pageSize,
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, dataId, groupId, appName},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByPostgresql.findConfigInfoLike4PageFetchRows(context);
        assertEquals(
            "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,encrypted_data_key,type,c_desc "
                + "FROM config_info WHERE  tenant_id LIKE ?  AND data_id LIKE ?  AND group_id LIKE ?  AND app_name = ?   "
                + "OFFSET " + startRow + " LIMIT " + pageSize,
            mapperResult.getSql());
        assertArrayEquals(new Object[] {tenantId, dataId, groupId, appName},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoBaseLikeFetchRows() {
        MapperResult mapperResult =
            configInfoMapperByPostgresql.findConfigInfoBaseLikeFetchRows(context);
        assertEquals("SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE "
            + " 1=1 AND tenant_id=''  AND data_id LIKE ?  AND group_id LIKE ?   "
            + "OFFSET " + startRow + " LIMIT " + pageSize, mapperResult.getSql());
        assertArrayEquals(new Object[] {dataId, groupId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoMapperByPostgresql.getTableName();
        assertEquals(TableConstant.CONFIG_INFO, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoMapperByPostgresql.getDataSource();
        assertEquals(DatabaseTypeConstant.POSTGRESQL, dataSource);
    }
}
