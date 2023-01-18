package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigInfoBetaMapperByPostgreSqlTest {

    private ConfigInfoBetaMapperByPostgreSql configInfoBetaMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        configInfoBetaMapperByPostgreSql = new ConfigInfoBetaMapperByPostgreSql();
    }

    @Test
    public void testUpdateConfigInfo4BetaCas() {
        String sql = configInfoBetaMapperByPostgreSql.updateConfigInfo4BetaCas();
        Assert.assertEquals(sql,
                "UPDATE config_info_beta SET content = ?,md5 = ?,beta_ips = ?,src_ip = ?,src_user = ?,gmt_modified = ?,app_name = ? "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND (md5 = ? OR md5 is null OR md5 = '')");
    }

    @Test
    public void testFindAllConfigInfoBetaForDumpAllFetchRows() {
        String sql = configInfoBetaMapperByPostgreSql.findAllConfigInfoBetaForDumpAllFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips,encrypted_data_key "
                        + " FROM ( SELECT id FROM config_info_beta  ORDER BY id LIMIT " + 5 + " OFFSET " + 0 + " )"
                        + "  g, config_info_beta t WHERE g.id = t.id");
    }

    @Test
    public void testGetTableName() {
        String tableName = configInfoBetaMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_BETA);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = configInfoBetaMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}
