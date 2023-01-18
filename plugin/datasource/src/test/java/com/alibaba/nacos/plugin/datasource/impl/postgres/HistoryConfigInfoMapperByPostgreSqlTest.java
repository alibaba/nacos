package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HistoryConfigInfoMapperByPostgreSqlTest {

    private HistoryConfigInfoMapperByPostgreSql historyConfigInfoMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        historyConfigInfoMapperByPostgreSql = new HistoryConfigInfoMapperByPostgreSql();
    }

    @Test
    public void testRemoveConfigHistory() {
        String sql = historyConfigInfoMapperByPostgreSql.removeConfigHistory();
        Assert.assertEquals(sql, "DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?");
    }

    @Test
    public void testFindConfigHistoryCountByTime() {
        String sql = historyConfigInfoMapperByPostgreSql.findConfigHistoryCountByTime();
        Assert.assertEquals(sql, "SELECT count(*) FROM his_config_info WHERE gmt_modified < ?");
    }

    @Test
    public void testFindDeletedConfig() {
        String sql = historyConfigInfoMapperByPostgreSql.findDeletedConfig();
        Assert.assertEquals(sql,
                "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
                        + "gmt_modified >= ? AND gmt_modified <= ?");
    }

    @Test
    public void testFindConfigHistoryFetchRows() {
        String sql = historyConfigInfoMapperByPostgreSql.findConfigHistoryFetchRows();
        Assert.assertEquals(sql,
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC");
    }

    @Test
    public void testDetailPreviousConfigHistory() {
        String sql = historyConfigInfoMapperByPostgreSql.detailPreviousConfigHistory();
        Assert.assertEquals(sql,
                "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,"
                        + "gmt_modified FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)");
    }

    @Test
    public void testGetTableName() {
        String tableName = historyConfigInfoMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.HIS_CONFIG_INFO);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}
