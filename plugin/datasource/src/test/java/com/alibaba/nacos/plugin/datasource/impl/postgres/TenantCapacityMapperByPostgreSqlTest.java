package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TenantCapacityMapperByPostgreSqlTest {

    private TenantCapacityMapperByPostgreSql tenantCapacityMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        tenantCapacityMapperByPostgreSql = new TenantCapacityMapperByPostgreSql();
    }

    @Test
    public void testGetTableName() {
        String tableName = tenantCapacityMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.TENANT_CAPACITY);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = tenantCapacityMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }

    @Test
    public void testIncrementUsageWithDefaultQuotaLimit() {
        String sql = tenantCapacityMapperByPostgreSql.incrementUsageWithDefaultQuotaLimit();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage <"
                        + " ? AND quota = 0");
    }

    @Test
    public void testIncrementUsageWithQuotaLimit() {
        String sql = tenantCapacityMapperByPostgreSql.incrementUsageWithQuotaLimit();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage < "
                        + "quota AND quota != 0");
    }

    @Test
    public void testIncrementUsage() {
        String sql = tenantCapacityMapperByPostgreSql.incrementUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ?");
    }

    @Test
    public void testDecrementUsage() {
        String sql = tenantCapacityMapperByPostgreSql.decrementUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET usage = usage - 1, gmt_modified = ? WHERE tenant_id = ? AND usage > 0");
    }

    @Test
    public void testCorrectUsage() {
        String sql = tenantCapacityMapperByPostgreSql.correctUsage();
        Assert.assertEquals(sql,
                "UPDATE tenant_capacity SET usage = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                        + "gmt_modified = ? WHERE tenant_id = ?");
    }

    @Test
    public void testGetCapacityList4CorrectUsage() {
        String sql = tenantCapacityMapperByPostgreSql.getCapacityList4CorrectUsage();
        Assert.assertEquals(sql, "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?");
    }

    @Test
    public void testInsertTenantCapacity() {
        String sql = tenantCapacityMapperByPostgreSql.insertTenantCapacity();
        Assert.assertEquals(sql,
                "INSERT INTO tenant_capacity (tenant_id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;");
    }
}
