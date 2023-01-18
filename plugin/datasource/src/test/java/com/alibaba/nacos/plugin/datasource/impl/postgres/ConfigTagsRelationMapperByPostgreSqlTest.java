package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

@RunWith(JUnit4.class)
public class ConfigTagsRelationMapperByPostgreSqlTest {

    private ConfigTagsRelationMapperByPostgreSql configTagsRelationMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        configTagsRelationMapperByPostgreSql = new ConfigTagsRelationMapperByPostgreSql();
    }

    @Test
    public void testFindConfigInfo4PageCountRows() {
        String sql = configTagsRelationMapperByPostgreSql.findConfigInfoLike4PageCountRows(new HashMap<>(), 5);
        Assert.assertEquals(sql,
                "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id  WHERE  "
                        + "a.tenant_id LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?) ");
    }

    @Test
    public void testFindConfigInfo4PageFetchRows() {
        String sql = configTagsRelationMapperByPostgreSql.findConfigInfo4PageFetchRows(new HashMap<>(), 5, 0, 5);
        Assert.assertEquals(sql,
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
                        + "config_tags_relation b ON a.id=b.id WHERE  a.tenant_id=?  AND b.tag_name IN (?, ?, ?, ?, ?)  LIMIT 5 OFFSET 0");
    }

    @Test
    public void testFindConfigInfoLike4PageCountRows() {
        String sql = configTagsRelationMapperByPostgreSql.findConfigInfoLike4PageCountRows(new HashMap<>(), 5);
        Assert.assertEquals(sql, "SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id  "
                + "WHERE  a.tenant_id LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?) ");
    }

    @Test
    public void tsetFindConfigInfoLike4PageFetchRows() {
        String sql = configTagsRelationMapperByPostgreSql.findConfigInfoLike4PageFetchRows(new HashMap<>(), 5, 0, 5);
        Assert.assertEquals(sql,
                "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info a LEFT JOIN"
                        + " config_tags_relation b ON a.id=b.id  WHERE  a.tenant_id LIKE ?  AND b.tag_name IN (?, ?, ?, ?, ?)  LIMIT 5 OFFSET 0");
    }

    @Test
    public void testGetTableName() {
        String tableName = configTagsRelationMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.CONFIG_TAGS_RELATION);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = configTagsRelationMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
}
