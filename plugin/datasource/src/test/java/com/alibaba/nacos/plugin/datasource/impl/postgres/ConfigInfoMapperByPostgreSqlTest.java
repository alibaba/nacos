package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

@RunWith(JUnit4.class)
public class ConfigInfoMapperByPostgreSqlTest {

    private ConfigInfoMapperByPostgreSql configInfoMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        configInfoMapperByPostgreSql = new ConfigInfoMapperByPostgreSql();
    }

    @Test
    public void testFindConfigMaxId() {
        String sql = configInfoMapperByPostgreSql.findConfigMaxId();
        Assert.assertEquals(sql, "SELECT MAX(id) FROM config_info");
    }

    @Test
    public void testFindAllDataIdAndGroup() {
        String sql = configInfoMapperByPostgreSql.findAllDataIdAndGroup();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id FROM config_info");
    }

    @Test
    public void testFindConfigInfoByAppCountRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoByAppCountRows();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ? AND app_name = ?");
    }

    @Test
    public void testFindConfigInfoByAppFetchRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoByAppFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE tenant_id LIKE ? AND app_name= ? LIMIT 5 OFFSET 0");
    }

    @Test
    public void testConfigInfoLikeTenantCount() {
        String sql = configInfoMapperByPostgreSql.configInfoLikeTenantCount();
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE tenant_id LIKE ?");
    }

    @Test
    public void testGetTenantIdList() {
        String sql = configInfoMapperByPostgreSql.getTenantIdList(0, 5);
        Assert.assertEquals(sql,
                "SELECT tenant_id FROM config_info WHERE tenant_id != '"
                        + NamespaceUtil.getNamespaceDefaultId() + "' GROUP BY tenant_id LIMIT 5 OFFSET 0");
    }

    @Test
    public void testGetGroupIdList() {
        String sql = configInfoMapperByPostgreSql.getGroupIdList(0, 5);
        Assert.assertEquals(sql, "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindAllConfigKey() {
        String sql = configInfoMapperByPostgreSql.findAllConfigKey(0, 5);
        Assert.assertEquals(sql, "SELECT data_id,group_id,app_name  FROM ( "
                + " SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT 5 OFFSET 0 )"
                + " g, config_info t WHERE g.id = t.id");
    }

    @Test
    public void testFindAllConfigInfoBaseFetchRows() {
        String sql = configInfoMapperByPostgreSql.findAllConfigInfoBaseFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,content,md5 FROM ( SELECT id FROM config_info ORDER BY id LIMIT 5 OFFSET 0"
                        + ")  g, config_info t  WHERE g.id = t.id ");
    }

    @Test
    public void testFindAllConfigInfoFragment() {
        String sql = configInfoMapperByPostgreSql.findAllConfigInfoFragment(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type,encrypted_data_key "
                        + "FROM config_info WHERE id > ? ORDER BY id ASC LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindChangeConfig() {
        String sql = configInfoMapperByPostgreSql.findChangeConfig();
        Assert.assertEquals(sql,
                "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified, encrypted_data_key "
                        + "FROM config_info WHERE gmt_modified >= ? AND gmt_modified <= ?");
    }

    @Test
    public void testFindChangeConfigCountRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByPostgreSql.findChangeConfigCountRows(new HashMap<>(), timestamp, timestamp);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=? ");
    }

    @Test
    public void testFindChangeConfigFetchRows() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String sql = configInfoMapperByPostgreSql.findChangeConfigFetchRows(new HashMap<>(), timestamp, timestamp, 0, 5,
                100);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified FROM config_info "
                        + "WHERE  1=1  AND gmt_modified >=?  AND gmt_modified <=?  AND id > 100 ORDER BY id ASC LIMIT 5 OFFSET 0");
    }

    @Test
    public void testListGroupKeyMd5ByPageFetchRows() {
        String sql = configInfoMapperByPostgreSql.listGroupKeyMd5ByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
                        + "( SELECT id FROM config_info ORDER BY id LIMIT 5 OFFSET 0 ) g, config_info t WHERE g.id = t.id");
    }

    @Test
    public void testFindAllConfigInfo4Export() {
        String sql = configInfoMapperByPostgreSql.findAllConfigInfo4Export(new ArrayList<>(), new HashMap<>());
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,"
                        + "src_ip,c_desc,c_use,effect,c_schema,encrypted_data_key FROM config_info WHERE  tenant_id = ? ");
    }

    @Test
    public void testFindConfigInfoBaseLikeCountRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoBaseLikeCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  1=1 AND tenant_id='' ");
    }

    @Test
    public void testFindConfigInfoBaseLikeFetchRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoBaseLikeFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,content FROM config_info WHERE  1=1 AND tenant_id=''  LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindConfigInfo4PageCountRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfo4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id=? ");
    }

    @Test
    public void testFindConfigInfo4PageFetchRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfo4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key FROM config_info"
                        + " WHERE  tenant_id=?  LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindConfigInfoBaseByGroupFetchRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoBaseByGroupFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,content FROM config_info WHERE group_id=? AND tenant_id=? LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindConfigInfoLike4PageCountRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoLike4PageCountRows(new HashMap<>());
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info WHERE  tenant_id LIKE ? ");
    }

    @Test
    public void testFindConfigInfoLike4PageFetchRows() {
        String sql = configInfoMapperByPostgreSql.findConfigInfoLike4PageFetchRows(new HashMap<>(), 0, 5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key FROM config_info "
                        + "WHERE  tenant_id LIKE ?  LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindAllConfigInfoFetchRows() {
        String sql = configInfoMapperByPostgreSql.findAllConfigInfoFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5  FROM (SELECT id FROM config_info "
                        + "WHERE tenant_id LIKE ? ORDER BY id LIMIT 5 OFFSET 0) g, config_info t  WHERE g.id = t.id");
    }

    @Test
    public void testFindConfigInfosByIds() {
        String sql = configInfoMapperByPostgreSql.findConfigInfosByIds(5);
        Assert.assertEquals(sql,
                "SELECT id,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }

    @Test
    public void testRemoveConfigInfoByIdsAtomic() {
        String sql = configInfoMapperByPostgreSql.removeConfigInfoByIdsAtomic(5);
        Assert.assertEquals(sql, "DELETE FROM config_info WHERE id IN (?, ?, ?, ?, ?) ");
    }

    @Test
    public void testGetTableName() {
        String sql = configInfoMapperByPostgreSql.getTableName();
        Assert.assertEquals(sql, TableConstant.CONFIG_INFO);
    }

    @Test
    public void testGetDataSource() {
        String sql = configInfoMapperByPostgreSql.getDataSource();
        Assert.assertEquals(sql, DataSourceConstant.POSTGRES);
    }

    @Test
    public void testUpdateConfigInfoAtomicCas() {
        String sql = configInfoMapperByPostgreSql.updateConfigInfoAtomicCas();
        Assert.assertEquals(sql, "UPDATE config_info SET "
                + "content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?, app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? "
                + "WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? OR md5 IS NULL OR md5='')");
    }
}
