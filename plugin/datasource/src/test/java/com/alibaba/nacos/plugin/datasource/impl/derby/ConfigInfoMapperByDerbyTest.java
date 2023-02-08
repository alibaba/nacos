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

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

public class ConfigInfoMapperByDerbyTest {
    
    private ConfigInfoMapperByDerby configInfoMapperByDerby;
    
    @Before
    public void setUp() throws Exception {
        configInfoMapperByDerby = new ConfigInfoMapperByDerby();
    }
    
    @Test
    public void testFindConfigMaxId() {
        String sql = configInfoMapperByDerby.findConfigMaxId();
        Assert.assertEquals(sql, "SELECT MAX(id) FROM config_info");
    }
    
    @Test
    public void testFindAllDataIdAndGroup() {
        String sql = configInfoMapperByDerby.findAllDataIdAndGroup();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id FROM config_info");
    }
    
    @Test
    public void testFindConfigInfoByAppCountRows() {
        String sql = configInfoMapperByDerby.findConfigInfoByAppCountRows();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?");
    }
    
    @Test
    public void testFindConfigInfoByAppFetchRows() {
        String sql = configInfoMapperByDerby.findConfigInfoByAppFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE"
                        + " ? AND app_name = ? OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testConfigInfoLikeTenantCount() {
        String sql = configInfoMapperByDerby.configInfoLikeTenantCount();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?");
    }
    
    @Test
    public void testGetTenantIdList() {
        String sql = configInfoMapperByDerby.getTenantIdList(0, 5);
        Assert.assertEquals(sql,
                "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testGetGroupIdList() {
        String sql = configInfoMapperByDerby.getGroupIdList(0, 5);
        Assert.assertEquals(sql,
                "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindAllConfigKey() {
        String sql = configInfoMapperByDerby.findAllConfigKey(0, 5);
        Assert.assertEquals(sql,
                " SELECT data_id,group_id,app_name FROM  ( SELECT id FROM config_info WHERE tenant_id LIKE"
                        + " ? ORDER BY id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY ) g, config_info t  WHERE g.id = t.id ");
    }
    
    @Test
    public void testFindAllConfigInfoBaseFetchRows() {
        String sql = configInfoMapperByDerby.findAllConfigInfoBaseFetchRows(0, 5);
        Assert.assertEquals(sql, "SELECT t.id,data_id,group_id,content,md5  FROM ( SELECT id FROM config_info ORDER BY "
                + "id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY )   g, config_info t WHERE g.id = t.id ");
    }
    
    @Test
    public void testFindAllConfigInfoFragment() {
        String sql = configInfoMapperByDerby.findAllConfigInfoFragment(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type FROM config_info "
                        + "WHERE id > ? ORDER BY id ASC OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindChangeConfig() {
        String sql = configInfoMapperByDerby.findChangeConfig();
        Assert.assertEquals(sql,
                "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified, encrypted_data_key FROM config_info "
                        + "WHERE gmt_modified >= ? AND gmt_modified <= ?");
    }
    
    @Test
    public void testFindChangeConfigCountRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByDerby.findChangeConfigCountRows(new HashMap<>(), timestamp, timestamp);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=? ");
    }
    
    @Test
    public void testFindChangeConfigFetchRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByDerby.findChangeConfigFetchRows(new HashMap<>(), timestamp, timestamp, 0, 5,
                100);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info "
                        + "WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=?  OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testListGroupKeyMd5ByPageFetchRows() {
        String sql = configInfoMapperByDerby.listGroupKeyMd5ByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                " SELECT t.id,data_id,group_id,tenant_id,app_name,type,md5,gmt_modified FROM ( SELECT id FROM config_info "
                        + "ORDER BY id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY ) g, config_info t WHERE g.id = t.id");
    }
    
    @Test
    public void testFindAllConfigInfo4Export() {
        String sql = configInfoMapperByDerby.findAllConfigInfo4Export(new ArrayList<>(), new HashMap<>());
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ? ");
    }
    
    @Test
    public void testFindConfigInfoBaseLikeCountRows() {
        String sql = configInfoMapperByDerby.findConfigInfoBaseLikeCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='' ");
    }
    
    @Test
    public void testFindConfigInfoBaseLikeFetchRows() {
        String sql = configInfoMapperByDerby.findConfigInfoBaseLikeFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id=''  "
                        + "OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindConfigInfo4PageCountRows() {
        String sql = configInfoMapperByDerby.findConfigInfo4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id=? ");
    }
    
    @Test
    public void testFindConfigInfo4PageFetchRows() {
        String sql = configInfoMapperByDerby.findConfigInfo4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type FROM config_info WHERE  tenant_id=?  "
                        + "OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindConfigInfoBaseByGroupFetchRows() {
        String sql = configInfoMapperByDerby.findConfigInfoBaseByGroupFetchRows(0, 5);
        Assert.assertEquals(sql, "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? "
                + "OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindConfigInfoLike4PageCountRows() {
        String sql = configInfoMapperByDerby.findConfigInfoLike4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id LIKE ? ");
    }
    
    @Test
    public void testFindConfigInfoLike4PageFetchRows() {
        String sql = configInfoMapperByDerby.findConfigInfoLike4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info "
                        + "WHERE  tenant_id LIKE ?  OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY");
    }
    
    @Test
    public void testFindAllConfigInfoFetchRows() {
        String sql = configInfoMapperByDerby.findAllConfigInfoFetchRows(0, 5);
        Assert.assertEquals(sql,
                " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM ( SELECT id FROM config_info  "
                        + "WHERE tenant_id LIKE ? ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ) g, config_info t  WHERE g.id = t.id ");
    }
    
    @Test
    public void testFindConfigInfosByIds() {
        String sql = configInfoMapperByDerby.findConfigInfosByIds(5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }
    
    @Test
    public void testRemoveConfigInfoByIdsAtomic() {
        String sql = configInfoMapperByDerby.removeConfigInfoByIdsAtomic(5);
        Assert.assertEquals(sql, "DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }
    
    @Test
    public void testGetTableName() {
        String sql = configInfoMapperByDerby.getTableName();
        Assert.assertEquals(sql, TableConstant.CONFIG_INFO);
    }
    
    @Test
    public void testGetDataSource() {
        String sql = configInfoMapperByDerby.getDataSource();
        Assert.assertEquals(sql, DataSourceConstant.DERBY);
    }
    
    @Test
    public void testUpdateConfigInfoAtomicCas() {
        String sql = configInfoMapperByDerby.updateConfigInfoAtomicCas();
        Assert.assertEquals(sql, "UPDATE config_info SET "
                + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? "
                + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')");
    }
}