package com.alibaba.nacos.config.server.service.repository.extrnal;

import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.alibaba.nacos.config.server.Config;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;


import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.*;
import static org.junit.Assert.*;

@Sql({"schema.sql", "test-data.sql"})
public class ExternalStoragePersistServiceImplTest {

    String dataIdInsert = "app-insert.yaml";

    String dataIdUpdate = "app-update.yaml";

    String groupId = "DEFAULT_GROUP";

    String content = "unit test";

    String md5 = "6264c783ba8e876293b2fa9be5b9475f";

    String srcIp = "127.0.0.1";

    String srcUser = "user";

    String appName = "testApp";

    String tenantId = "public";

    String type = "text";


    static ExternalStoragePersistServiceImpl serviceImpl;
    static JdbcTemplate jt;
    static TransactionTemplate tjt;

    @BeforeClass
    public static void setUp() throws Exception {

        serviceImpl = new ExternalStoragePersistServiceImpl();

        // 建表语句 schema.sql
        // 初始化数据 test-data.sql
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb;MODE=MySQL")
                .addScript("schema.sql")
                .build();
        jt = new JdbcTemplate(dataSource);
        tjt = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ReflectionTestUtils.setField(serviceImpl, "jt", jt);
        ReflectionTestUtils.setField(serviceImpl, "tjt", tjt);

    }


    private ConfigInfo getNewConfigInfo() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(dataIdInsert);
        configInfo.setContent(content);
        configInfo.setAppName(appName);
        configInfo.setType(type);
        configInfo.setGroup(groupId);
        configInfo.setTenant(tenantId);
        return configInfo;
    }

    private Timestamp getTimestampNow() {
        return new Timestamp(new Date().getTime());
    }

    private void clearTable(String tableName){
        jt.execute("delete from "+tableName);
    }
    @Test
    public void addConfigInfo() {
        clearTable("config_info");
        ConfigInfo configInfo = getNewConfigInfo();
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        boolean notify = false;
        // 调接口添加配置serviceImpl.addConfigInfo();
        serviceImpl.addConfigInfo(srcIp, srcUser, configInfo, getTimestampNow(), configAdvanceInfo, notify);
        // 查询
        String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,type FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?";
        ConfigInfo configInfo1 = jt.queryForObject(sql, new Object[]{configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        // 断言
        Assert.assertNotNull(configInfo);
        Assert.assertEquals(configInfo.getContent(), configInfo1.getContent());
    }

    @Test
    public void addConfigInfo4Beta() {
        clearTable("config_info_beta");
        ConfigInfo configInfo = getNewConfigInfo();
        String betaIps = "127.0.0.1";
        serviceImpl.addConfigInfo4Beta(configInfo, betaIps, srcIp, srcUser,getTimestampNow(), false);
        String sql = "SELECT * FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?";
        ConfigInfo configInfo1 = jt.queryForObject(sql, new Object[] {configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()}, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        Assert.assertNotNull(configInfo1);
        Assert.assertEquals(configInfo.getContent(), configInfo1.getContent());
    }

    @Test
    public void addConfigInfo4Tag() {
        clearTable("config_info_tag");
        ConfigInfo configInfo = getNewConfigInfo();
        String tag = "v1";
        serviceImpl.addConfigInfo4Tag(configInfo,tag,srcIp, srcUser, getTimestampNow(), false);
        String sql = "SELECT * FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?";
        System.out.println(configInfo.getDataId()+configInfo.getGroup()+configInfo.getContent()+tag);
        ConfigInfo configinfo1 = jt.queryForObject(sql, new Object[] {configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag},CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
        Assert.assertNotNull(configinfo1);
    }

    @Test
    public void updateConfigInfo() {
        clearTable("config_info");
        String sql =
                "INSERT INTO config_info(data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_create,"
                        + "gmt_modified,c_desc,c_use,effect,type,c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, dataIdUpdate);
                ps.setString(2, groupId);
                ps.setString(3, tenantId);
                ps.setString(4, appName);
                ps.setString(5, content);
                ps.setString(6, md5);
                ps.setString(7, srcIp);
                ps.setString(8, srcUser);
                ps.setTimestamp(9, getTimestampNow());
                ps.setTimestamp(10, getTimestampNow());
                ps.setString(11, null);
                ps.setString(12, null);
                ps.setString(13, null);
                ps.setString(14, type);
                ps.setString(15, null);
                return ps;
            }
        },keyHolder);
        Number num = keyHolder.getKey();
        String sqlQuery = "select * from config_info where id=?";
        ConfigInfo configInfo = jt.queryForObject(sqlQuery, new Object[]{num}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        serviceImpl.updateConfigInfo(configInfo, srcIp, srcUser, getTimestampNow(),configAdvanceInfo, false);
        ConfigInfo configInfo1 = jt.queryForObject(sqlQuery, new Object[]{num}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        Assert.assertNotEquals(configInfo.getMd5(), configInfo1.getMd5());
    }

    @Test
    public void updateConfigInfoCas() {
        clearTable("config_info");
        String sql =
                "INSERT INTO config_info(data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_create,"
                        + "gmt_modified,c_desc,c_use,effect,type,c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, dataIdUpdate);
                ps.setString(2, groupId);
                ps.setString(3, tenantId);
                ps.setString(4, appName);
                ps.setString(5, content);
                ps.setString(6, md5);
                ps.setString(7, srcIp);
                ps.setString(8, srcUser);
                ps.setTimestamp(9, getTimestampNow());
                ps.setTimestamp(10, getTimestampNow());
                ps.setString(11, null);
                ps.setString(12, null);
                ps.setString(13, null);
                ps.setString(14, type);
                ps.setString(15, null);
                return ps;
            }
        },keyHolder);
        Number num = keyHolder.getKey();
        String sqlQuery = "select * from config_info where id=?";
        ConfigInfo configInfo = jt.queryForObject(sqlQuery, new Object[]{num}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        serviceImpl.updateConfigInfoCas(configInfo, srcIp, srcUser, getTimestampNow(),configAdvanceInfo, false);
        ConfigInfo configInfo1 = jt.queryForObject(sqlQuery, new Object[]{num}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        Assert.assertNotEquals(configInfo.getMd5(), configInfo1.getMd5());
    }

    @Test
    public void updateConfigInfo4Beta() {
        clearTable("config_info_beta");
        String betaIps = "127.0.0.1";
        String sql = "INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip,src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, dataIdUpdate);
                ps.setString(2, groupId);
                ps.setString(3, tenantId);
                ps.setString(4, appName);
                ps.setString(5, content);
                ps.setString(6, md5);
                ps.setString(7, betaIps);
                ps.setString(8, srcIp);
                ps.setString(9, srcUser);
                ps.setTimestamp(10, getTimestampNow());
                ps.setTimestamp(11, getTimestampNow());
                return ps;
            }
        },keyHolder);
        Number num = keyHolder.getKey();
        String sqlQuery = "select * from config_info_beta where id=?";
        ConfigInfo configInfo = jt.queryForObject(sqlQuery, new Object[]{num},CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        serviceImpl.updateConfigInfo4Beta(configInfo,betaIps, srcIp, srcUser,getTimestampNow(),false);
        ConfigInfo configInfo1 = jt.queryForObject(sqlQuery, new Object[]{num},CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        Assert.assertNotEquals(configInfo.getMd5(), configInfo1.getMd5());
    }

    @Test
    public void updateConfigInfo4BetaCas() {
        clearTable("config_info_beta");
        String betaIps = "127.0.0.1";
        String sql = "INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip,src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jt.update(new PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, dataIdUpdate);
                ps.setString(2, groupId);
                ps.setString(3, tenantId);
                ps.setString(4, appName);
                ps.setString(5, content);
                ps.setString(6, md5);
                ps.setString(7, betaIps);
                ps.setString(8, srcIp);
                ps.setString(9, srcUser);
                ps.setTimestamp(10, getTimestampNow());
                ps.setTimestamp(11, getTimestampNow());
                return ps;
            }
        },keyHolder);
        Number num = keyHolder.getKey();
        String sqlQuery = "select * from config_info_beta where id=?";
        ConfigInfo configInfo = jt.queryForObject(sqlQuery, new Object[]{num},CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        serviceImpl.updateConfigInfo4BetaCas(configInfo,betaIps, srcIp, srcUser,getTimestampNow(),false);
        ConfigInfo configInfo1 = jt.queryForObject(sqlQuery, new Object[]{num},CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        Assert.assertNotEquals(configInfo.getMd5(), configInfo1.getMd5());
    }

}
