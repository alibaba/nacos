package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigInfoTagMapperByPostgreSqlTest {

    private ConfigInfoTagMapperByPostgreSql configInfoTagMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        configInfoTagMapperByPostgreSql = new ConfigInfoTagMapperByPostgreSql();
    }

    @Test
    public void testUpdateConfigInfo4TagCas() {
        String sql = configInfoTagMapperByPostgreSql.updateConfigInfo4TagCas();
        Assert.assertEquals(sql,
                "UPDATE config_info_tag SET content = ?, md5 = ?, src_ip = ?,src_user = ?,gmt_modified = ?,"
                        + "app_name = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND tag_id = ? AND "
                        + "(md5 = ? OR md5 IS NULL OR md5 = '')");
    }

    @Test
    public void testFindAllConfigInfoTagForDumpAllFetchRows() {
        String sql = configInfoTagMapperByPostgreSql.findAllConfigInfoTagForDumpAllFetchRows(0, 100);
        Assert.assertEquals(sql,
                "SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified  FROM (  "
                        + "SELECT id FROM config_info_tag  ORDER BY id LIMIT 100 OFFSET 0 ) g, config_info_tag t  WHERE g.id = t.id");
    }

    @Test
    public void testGetTableName() {
        String tableName = configInfoTagMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_TAG);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = configInfoTagMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}
