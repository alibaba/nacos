package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TenantInfoMapperByPostgreSqlTest {

    private TenantInfoMapperByPostgreSql tenantInfoMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        tenantInfoMapperByPostgreSql = new TenantInfoMapperByPostgreSql();
    }

    @Test
    public void testGetTableName() {
        String tableName = tenantInfoMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.TENANT_INFO);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = tenantInfoMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}
