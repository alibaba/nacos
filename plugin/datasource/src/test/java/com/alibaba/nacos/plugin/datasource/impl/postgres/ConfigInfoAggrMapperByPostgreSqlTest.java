package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConfigInfoAggrMapperByPostgreSqlTest {

    private ConfigInfoAggrMapperByPostgreSql configInfoAggrMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        configInfoAggrMapperByPostgreSql = new ConfigInfoAggrMapperByPostgreSql();
    }

    @Test
    public void testBatchRemoveAggr() {
        String sql = configInfoAggrMapperByPostgreSql.batchRemoveAggr(5);
        Assert.assertEquals(sql, "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
                + "AND datum_id IN (?, ?, ?, ?, ?)");
    }

    @Test
    public void testFindConfigInfoAggrIsOrdered() {
        String sql = configInfoAggrMapperByPostgreSql.findConfigInfoAggrIsOrdered();
        Assert.assertEquals(sql, "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id");
    }

    @Test
    public void testFindConfigInfoAggrByPageFetchRows() {
        String sql = configInfoAggrMapperByPostgreSql.findConfigInfoAggrByPageFetchRows(0, 5);
        Assert.assertEquals(sql,
                "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE "
                        + "data_id= ? AND group_id= ? AND tenant_id= ? ORDER BY datum_id LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindAllAggrGroupByDistinct() {
        String sql = configInfoAggrMapperByPostgreSql.findAllAggrGroupByDistinct();
        Assert.assertEquals(sql, "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr");
    }

    @Test
    public void testGetTableName() {
        String tableName = configInfoAggrMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_INFO_AGGR);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = configInfoAggrMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }

}
