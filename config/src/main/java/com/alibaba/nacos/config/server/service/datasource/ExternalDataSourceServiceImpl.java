/*
 *
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 *
 */
package com.alibaba.nacos.config.server.service.datasource;

import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.CONFIG_INFO4BETA_ROW_MAPPER;
import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * Base data source
 *
 * @author Nacos
 */
public class ExternalDataSourceServiceImpl implements DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(
            ExternalDataSourceServiceImpl.class);
    private static final String DEFAULT_MYSQL_DRIVER = "com.mysql.jdbc.Driver";
    private static final String MYSQL_HIGH_LEVEL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static String JDBC_DRIVER_NAME;

    /**
     * JDBC执行超时时间, 单位秒
     */
    private int queryTimeout = 3;

    private static final int TRANSACTION_QUERY_TIMEOUT = 5;

    private static final String DB_LOAD_ERROR_MSG = "[db-load-error]load jdbc.properties error";

    private List<HikariDataSource> dataSourceList = new ArrayList<>();
    private JdbcTemplate jt;
    private DataSourceTransactionManager tm;
    private TransactionTemplate tjt;

    private JdbcTemplate testMasterJT;
    private JdbcTemplate testMasterWritableJT;

    volatile private List<JdbcTemplate> testJTList;
    volatile private List<Boolean> isHealthList;
    private volatile int masterIndex;
    private static Pattern ipPattern = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    static {
        try {
            Class.forName(MYSQL_HIGH_LEVEL_DRIVER);
            JDBC_DRIVER_NAME = MYSQL_HIGH_LEVEL_DRIVER;
            log.info("Use Mysql 8 as the driver");
        } catch (ClassNotFoundException e) {
            log.info("Use Mysql as the driver");
            JDBC_DRIVER_NAME = DEFAULT_MYSQL_DRIVER;
        }
    }

    @PostConstruct
    public void init() {
        queryTimeout = ConvertUtils.toInt(System.getProperty("QUERYTIMEOUT"), 3);
        jt = new JdbcTemplate();
        /**
         *  设置最大记录数，防止内存膨胀
         */
        jt.setMaxRows(50000);
        jt.setQueryTimeout(queryTimeout);

        testMasterJT = new JdbcTemplate();
        testMasterJT.setQueryTimeout(queryTimeout);

        testMasterWritableJT = new JdbcTemplate();
        /**
         * 防止login接口因为主库不可用而rt太长
         */
        testMasterWritableJT.setQueryTimeout(1);
        /**
         * 数据库健康检测
         */
        testJTList = new ArrayList<JdbcTemplate>();
        isHealthList = new ArrayList<Boolean>();

        tm = new DataSourceTransactionManager();
        tjt = new TransactionTemplate(tm);
        /**
         *  事务的超时时间需要与普通操作区分开
         */
        tjt.setTimeout(TRANSACTION_QUERY_TIMEOUT);
        if (PropertyUtil.isUseExternalDB()) {
            try {
                reload();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(DB_LOAD_ERROR_MSG);
            }

            ConfigExecutor.scheduleWithFixedDelay(new SelectMasterTask(), 10, 10,
                TimeUnit.SECONDS);
            ConfigExecutor.scheduleWithFixedDelay(new CheckDBHealthTask(), 10, 10,
                TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void reload() throws IOException {
        List<HikariDataSource> dblist = new ArrayList<>();
        try {
            String val = null;
            val = ApplicationUtils.getProperty("db.num");
            if (null == val) {
                throw new IllegalArgumentException("db.num is null");
            }
            int dbNum = Integer.parseInt(val.trim());

            for (int i = 0; i < dbNum; i++) {
                HikariDataSource ds = new HikariDataSource();
                ds.setDriverClassName(JDBC_DRIVER_NAME);

                val = ApplicationUtils.getProperty("db.url." + i);
                if (null == val) {
                    fatalLog.error("db.url." + i + " is null");
                    throw new IllegalArgumentException("db.url." + i + " is null");
                }
                ds.setJdbcUrl(val.trim());

                val = ApplicationUtils.getProperty("db.user." + i, ApplicationUtils.getProperty("db.user"));
                if (null == val) {
                    fatalLog.error("db.user." + i + " is null");
                    throw new IllegalArgumentException("db.user." + i + " is null");
                }
                ds.setUsername(val.trim());

                val = ApplicationUtils.getProperty("db.password." + i, ApplicationUtils.getProperty("db.password"));
                if (null == val) {
                    fatalLog.error("db.password." + i + " is null");
                    throw new IllegalArgumentException("db.password." + i + " is null");
                }
                ds.setPassword(val.trim());

                val = ApplicationUtils.getProperty("db.maxPoolSize." + i, ApplicationUtils.getProperty("db.maxPoolSize"));
                ds.setMaximumPoolSize(Integer.parseInt(defaultIfNull(val, "20")));

                val = ApplicationUtils.getProperty("db.minIdle." + i, ApplicationUtils.getProperty("db.minIdle"));
                ds.setMinimumIdle(Integer.parseInt(defaultIfNull(val, "50")));

                ds.setConnectionTimeout(3000L);

                // 每10分钟检查一遍连接池
                ds.setValidationTimeout(TimeUnit.MINUTES.toMillis(10L));
                ds.setConnectionTestQuery("SELECT 1 FROM dual");

                dblist.add(ds);

                JdbcTemplate jdbcTemplate = new JdbcTemplate();
                jdbcTemplate.setQueryTimeout(queryTimeout);
                jdbcTemplate.setDataSource(ds);

                testJTList.add(jdbcTemplate);
                isHealthList.add(Boolean.TRUE);
            }

            if (dblist == null || dblist.size() == 0) {
                throw new RuntimeException("no datasource available");
            }

            dataSourceList = dblist;
            new SelectMasterTask().run();
            new CheckDBHealthTask().run();
        } catch (RuntimeException e) {
            fatalLog.error(DB_LOAD_ERROR_MSG, e);
            throw new IOException(e);
        } finally {
        }
    }

    @Override
    public boolean checkMasterWritable() {

        testMasterWritableJT.setDataSource(jt.getDataSource());
        /**
         *  防止login接口因为主库不可用而rt太长
         */
        testMasterWritableJT.setQueryTimeout(1);
        String sql = " SELECT @@read_only ";

        try {
            Integer result = testMasterWritableJT.queryForObject(sql, Integer.class);
            if (result == null) {
                return false;
            } else {
                return result == 0;
            }
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            return false;
        }

    }

    @Override
    public JdbcTemplate getJdbcTemplate() {
        return this.jt;
    }

    @Override
    public TransactionTemplate getTransactionTemplate() {
        return this.tjt;
    }

    @Override
    public String getCurrentDBUrl() {
        DataSource ds = this.jt.getDataSource();
        if (ds == null) {
            return StringUtils.EMPTY;
        }
        HikariDataSource bds = (HikariDataSource) ds;
        return bds.getJdbcUrl();
    }

    @Override
    public String getHealth() {
        for (int i = 0; i < isHealthList.size(); i++) {
            if (!isHealthList.get(i)) {
                if (i == masterIndex) {
                    /**
                     * 主库不健康
                     */
                    return "DOWN:" + getIpFromUrl(dataSourceList.get(i).getJdbcUrl());
                } else {
                    /**
                     * 从库不健康
                     */
                    return "WARN:" + getIpFromUrl(dataSourceList.get(i).getJdbcUrl());
                }
            }
        }

        return "UP";
    }

    private String getIpFromUrl(String url) {

        Matcher m = ipPattern.matcher(url);
        if (m.find()) {
            return m.group();
        }

        return "";
    }

    static String defaultIfNull(String value, String defaultValue) {
        return null == value ? defaultValue : value;
    }

    class SelectMasterTask implements Runnable {

        @Override
        public void run() {
            if (defaultLog.isDebugEnabled()) {
                defaultLog.debug("check master db.");
            }
            boolean isFound = false;

            int index = -1;
            for (HikariDataSource ds : dataSourceList) {
                index++;
                testMasterJT.setDataSource(ds);
                testMasterJT.setQueryTimeout(queryTimeout);
                try {
                    testMasterJT
                        .update(
                            "DELETE FROM config_info WHERE data_id='com.alibaba.nacos.testMasterDB'");
                    if (jt.getDataSource() != ds) {
                        fatalLog.warn("[master-db] {}", ds.getJdbcUrl());
                    }
                    jt.setDataSource(ds);
                    tm.setDataSource(ds);
                    isFound = true;
                    masterIndex = index;
                    break;
                } catch (DataAccessException e) { // read only
                    e.printStackTrace(); // TODO remove
                }
            }

            if (!isFound) {
                fatalLog.error("[master-db] master db not found.");
                MetricsMonitor.getDbException().increment();
            }
        }
    }

    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    class CheckDBHealthTask implements Runnable {

        @Override
        public void run() {
            if (defaultLog.isDebugEnabled()) {
                defaultLog.debug("check db health.");
            }
            String sql = "SELECT * FROM config_info_beta WHERE id = 1";

            for (int i = 0; i < testJTList.size(); i++) {
                JdbcTemplate jdbcTemplate = testJTList.get(i);
                try {
                    jdbcTemplate.query(sql, CONFIG_INFO4BETA_ROW_MAPPER);
                    isHealthList.set(i, Boolean.TRUE);
                } catch (DataAccessException e) {
                    if (i == masterIndex) {
                        fatalLog.error("[db-error] master db {} down.",
                            getIpFromUrl(dataSourceList.get(i).getJdbcUrl()));
                    } else {
                        fatalLog.error("[db-error] slave db {} down.",
                            getIpFromUrl(dataSourceList.get(i).getJdbcUrl()));
                    }
                    isHealthList.set(i, Boolean.FALSE);

                    MetricsMonitor.getDbException().increment();
                }
            }
        }
    }
}
