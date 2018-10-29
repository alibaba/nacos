///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.alibaba.nacos.config.server.configuration;
//
//import com.alibaba.nacos.config.server.service.BasicDataSourceServiceImpl;
//import com.alibaba.nacos.config.server.service.TimerTaskService;
//import com.alibaba.nacos.config.server.utils.PropertyUtil;
//import org.apache.commons.dbcp.BasicDataSource;
//import org.apache.commons.lang.math.NumberUtils;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.transaction.support.TransactionTemplate;
//
//import javax.annotation.PostConstruct;
//import javax.sql.DataSource;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//import java.util.regex.Pattern;
//
//import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;
//
///**
// * Cluster {@link DataSource} {@link Configuration}
// *
// * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
// * @since 0.2.2
// */
//@Profile("!standalone")
//@Configuration
//public class ClusterDataSourceConfiguration {
//
//    private static final String JDBC_DRIVER_NAME = "com.mysql.jdbc.Driver";
//
//    /**
//     *  JDBC执行超时时间, 单位秒
//     */
//    private int queryTimeout = 3;
//
//    private static final int TRANSACTION_QUERY_TIMEOUT = 5;
//
//    private static final String DB_LOAD_ERROR_MSG = "[db-load-error]load jdbc.properties error";
//
//    private List<BasicDataSource> dataSourceList = new ArrayList<BasicDataSource>();
//    private JdbcTemplate jt;
//    private DataSourceTransactionManager tm;
//    private TransactionTemplate tjt;
//
//    private JdbcTemplate testMasterJT;
//    private JdbcTemplate testMasterWritableJT;
//
//    volatile private List<JdbcTemplate> testJTList;
//    volatile private List<Boolean> isHealthList;
//    private volatile int masterIndex;
//    private static Pattern ipPattern = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
//
//
//
//    @PostConstruct
//    public void init() {
//        queryTimeout = NumberUtils
//                .toInt(System.getProperty("QUERYTIMEOUT"), 3);
//        jt = new JdbcTemplate();
//        /**
//         *  设置最大记录数，防止内存膨胀
//         */
//        jt.setMaxRows(50000);
//        jt.setQueryTimeout(queryTimeout);
//
//        testMasterJT = new JdbcTemplate();
//        testMasterJT.setQueryTimeout(queryTimeout);
//
//        testMasterWritableJT = new JdbcTemplate();
//        /**
//         * 防止login接口因为主库不可用而rt太长
//         */
//        testMasterWritableJT.setQueryTimeout(1);
//        /**
//         * 数据库健康检测
//         */
//        testJTList = new ArrayList<JdbcTemplate>();
//        isHealthList = new ArrayList<Boolean>();
//
//        tm = new DataSourceTransactionManager();
//        tjt = new TransactionTemplate(tm);
//        /**
//         *  事务的超时时间需要与普通操作区分开
//         */
//        tjt.setTimeout(TRANSACTION_QUERY_TIMEOUT);
//        if (!STANDALONE_MODE) {
//            try {
//                reload();
//            } catch (IOException e) {
//                e.printStackTrace();
//                throw new RuntimeException(DB_LOAD_ERROR_MSG);
//            }
//
//            TimerTaskService.scheduleWithFixedDelay(new BasicDataSourceServiceImpl.SelectMasterTask(), 10, 10,
//                    TimeUnit.SECONDS);
//            TimerTaskService.scheduleWithFixedDelay(new BasicDataSourceServiceImpl.CheckDBHealthTask(), 10, 10,
//                    TimeUnit.SECONDS);
//        }
//    }
//
//    public synchronized void reload() throws IOException {
//        List<BasicDataSource> dblist = new ArrayList<BasicDataSource>();
//        try {
//            String val = null;
//            val = env.getProperty("db.num");
//            if (null == val) {
//                throw new IllegalArgumentException("db.num is null");
//            }
//            int dbNum = Integer.parseInt(val.trim());
//
//            for (int i = 0; i < dbNum; i++) {
//                BasicDataSource ds = new BasicDataSource();
//                ds.setDriverClassName(JDBC_DRIVER_NAME);
//
//                val = env.getProperty("db.url." + i);
//                if (null == val) {
//                    fatalLog.error("db.url." + i + " is null");
//                    throw new IllegalArgumentException();
//                }
//                ds.setUrl(val.trim());
//
//                val = env.getProperty("db.user");
//                if (null == val) {
//                    fatalLog.error("db.user is null");
//                    throw new IllegalArgumentException();
//                }
//                ds.setUsername(val.trim());
//
//                val = env.getProperty("db.password");
//                if (null == val) {
//                    fatalLog.error("db.password is null");
//                    throw new IllegalArgumentException();
//                }
//                ds.setPassword(val.trim());
//
//                val = env.getProperty("db.initialSize");
//                ds.setInitialSize(Integer.parseInt(defaultIfNull(val, "10")));
//
//                val = env.getProperty("db.maxActive");
//                ds.setMaxActive(Integer.parseInt(defaultIfNull(val, "20")));
//
//                val = env.getProperty("db.maxIdle");
//                ds.setMaxIdle(Integer.parseInt(defaultIfNull(val, "50")));
//
//                ds.setMaxWait(3000L);
//                ds.setPoolPreparedStatements(true);
//
//                // 每10分钟检查一遍连接池
//                ds.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES
//                        .toMillis(10L));
//                ds.setTestWhileIdle(true);
//                ds.setValidationQuery("SELECT 1 FROM dual");
//
//                dblist.add(ds);
//
//                JdbcTemplate jdbcTemplate = new JdbcTemplate();
//                jdbcTemplate.setQueryTimeout(queryTimeout);
//                jdbcTemplate.setDataSource(ds);
//
//                testJTList.add(jdbcTemplate);
//                isHealthList.add(Boolean.TRUE);
//            }
//
//            if (dblist == null || dblist.size() == 0) {
//                throw new RuntimeException("no datasource available");
//            }
//
//            dataSourceList = dblist;
//            new BasicDataSourceServiceImpl.SelectMasterTask().run();
//            new BasicDataSourceServiceImpl.CheckDBHealthTask().run();
//        } catch (RuntimeException e) {
//            fatalLog.error(DB_LOAD_ERROR_MSG, e);
//            throw new IOException(e);
//        } finally {
//        }
//    }
//}
