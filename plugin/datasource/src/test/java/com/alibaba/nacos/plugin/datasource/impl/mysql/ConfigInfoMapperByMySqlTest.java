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
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

public class ConfigInfoMapperByMySqlTest {
    
    private ConfigInfoMapperByMySql configInfoMapperByMySql;
    
    @Before
    public void setUp() throws Exception {
        configInfoMapperByMySql = new ConfigInfoMapperByMySql();
    }
    
    @Test
    public void testFindConfigMaxId() {
        String sql = configInfoMapperByMySql.findConfigMaxId();
        Assert.assertEquals(sql, "SELECT MAX(id) FROM config_info");
    }
    
    @Test
    public void testFindAllDataIdAndGroup() {
        String sql = configInfoMapperByMySql.findAllDataIdAndGroup();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id FROM config_info");
    }
    
    @Test
    public void testFindConfigInfoByAppCountRows() {
        String sql = configInfoMapperByMySql.findConfigInfoByAppCountRows();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?");
    }
    
    @Test
    public void testFindConfigInfoByAppFetchRows() {
        String sql = configInfoMapperByMySql.findConfigInfoByAppFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND app_name= ? LIMIT 0,5");
    }
    
    @Test
    public void testConfigInfoLikeTenantCount() {
        String sql = configInfoMapperByMySql.configInfoLikeTenantCount();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?");
    }
    
    @Test
    public void testGetTenantIdList() {
        String sql = configInfoMapperByMySql.getTenantIdList(0, 5);
        Assert.assertEquals(sql,
                "SELECT tenant_id FROM config_info WHERE tenant_id != '"
                        + NamespaceUtil.getNamespaceDefaultId() + "' GROUP BY tenant_id LIMIT 0,5");
    }
    
    @Test
    public void testGetGroupIdList() {
        String sql = configInfoMapperByMySql.getGroupIdList(0, 5);
        Assert.assertEquals(sql, "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT 0,5");
    }
    
    @Test
    public void testFindAllConfigKey() {
        String sql = configInfoMapperByMySql.findAllConfigKey(0, 5);
        Assert.assertEquals(sql, " SELECT data_id,group_id,app_name  FROM ( "
                + " SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT 0,5 )"
                + " g, config_info t WHERE g.id = t.id  ");
    }
    
    @Test
    public void testFindAllConfigInfoBaseFetchRows() {
        String sql = configInfoMapperByMySql.findAllConfigInfoBaseFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,content,md5 FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,?"
                        + "  )  g, config_info t  WHERE g.id = t.id ");
    }
    
    @Test
    public void testFindAllConfigInfoFragment() {
        String sql = configInfoMapperByMySql.findAllConfigInfoFragment(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key "
                        + "FROM config_info WHERE id > ? ORDER BY id ASC LIMIT 0,5");
    }
    
    @Test
    public void testFindChangeConfig() {
        String sql = configInfoMapperByMySql.findChangeConfig();
        Assert.assertEquals(sql,
                "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified, encrypted_data_key "
                        + "FROM config_info WHERE gmt_modified >= ? AND gmt_modified <= ?");
    }
    
    @Test
    public void testFindChangeConfigCountRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByMySql.findChangeConfigCountRows(new HashMap<>(), timestamp, timestamp);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=? ");
    }
    
    @Test
    public void testFindChangeConfigFetchRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByMySql.findChangeConfigFetchRows(new HashMap<>(), timestamp, timestamp, 0, 5,
                100);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info "
                        + "WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=?  AND id > 100 ORDER BY id ASC LIMIT 0,5");
    }
    
    @Test
    public void testListGroupKeyMd5ByPageFetchRows() {
        String sql = configInfoMapperByMySql.listGroupKeyMd5ByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
                        + "( SELECT id FROM config_info ORDER BY id LIMIT 0,5 ) g, config_info t WHERE g.id = t.id");
    }
    
    @Test
    public void testFindAllConfigInfo4Export() {
        String sql = configInfoMapperByMySql.findAllConfigInfo4Export(new ArrayList<>(), new HashMap<>());
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ? ");
    }
    
    @Test
    public void testFindConfigInfoBaseLikeCountRows() {
        String sql = configInfoMapperByMySql.findConfigInfoBaseLikeCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='' ");
    }
    
    @Test
    public void testFindConfigInfoBaseLikeFetchRows() {
        String sql = configInfoMapperByMySql.findConfigInfoBaseLikeFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id=''  LIMIT 0,5");
    }
    
    @Test
    public void testFindConfigInfo4PageCountRows() {
        String sql = configInfoMapperByMySql.findConfigInfo4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id=? ");
    }
    
    @Test
    public void testFindConfigInfo4PageFetchRows() {
        String sql = configInfoMapperByMySql.findConfigInfo4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key FROM config_info"
                        + " WHERE  tenant_id=?  LIMIT 0,5");
    }
    
    @Test
    public void testFindConfigInfoBaseByGroupFetchRows() {
        String sql = configInfoMapperByMySql.findConfigInfoBaseByGroupFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? LIMIT 0,5");
    }
    
    @Test
    public void testFindConfigInfoLike4PageCountRows() {
        String sql = configInfoMapperByMySql.findConfigInfoLike4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id LIKE ? ");
    }
    
    @Test
    public void testFindConfigInfoLike4PageFetchRows() {
        String sql = configInfoMapperByMySql.findConfigInfoLike4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info "
                        + "WHERE  tenant_id LIKE ?  LIMIT 0,5");
    }
    
    @Test
    public void testFindAllConfigInfoFetchRows() {
        String sql = configInfoMapperByMySql.findAllConfigInfoFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM (  SELECT id FROM config_info "
                        + "WHERE tenant_id LIKE ? ORDER BY id LIMIT ?,? ) g, config_info t  WHERE g.id = t.id ");
    }
    
    @Test
    public void testFindConfigInfosByIds() {
        String sql = configInfoMapperByMySql.findConfigInfosByIds(5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }
    
    @Test
    public void testRemoveConfigInfoByIdsAtomic() {
        String sql = configInfoMapperByMySql.removeConfigInfoByIdsAtomic(5);
        Assert.assertEquals(sql, "DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }
    
    @Test
    public void testGetTableName() {
        String sql = configInfoMapperByMySql.getTableName();
        Assert.assertEquals(sql, TableConstant.CONFIG_INFO);
    }
    
    @Test
    public void testGetDataSource() {
        String sql = configInfoMapperByMySql.getDataSource();
        Assert.assertEquals(sql, DataSourceConstant.MYSQL);
    }
    
    @Test
    public void testUpdateConfigInfoAtomicCas() {
        String sql = configInfoMapperByMySql.updateConfigInfoAtomicCas();
        Assert.assertEquals(sql, "UPDATE config_info SET "
                + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? "
                + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')");
    }
}