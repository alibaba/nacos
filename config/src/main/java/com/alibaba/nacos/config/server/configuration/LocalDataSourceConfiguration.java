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
//import org.apache.commons.dbcp.BasicDataSource;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
//import org.springframework.core.env.Environment;
//
//import javax.sql.DataSource;
//import java.io.File;
//import java.util.concurrent.TimeUnit;
//
//import static com.alibaba.nacos.config.server.configuration.DataBaseConfiguration.DATA_SOURCE_BEAN_NAME;
//
///**
// * Local {@link DataSource} {@link Configuration}
// *
// * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
// * @since 0.2.2
// */
//@Profile("standalone")
//@Configuration
//public class LocalDataSourceConfiguration {
//
//    private static final String JDBC_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
//
//    private static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";
//
//    private static final String NACOS_HOME_PROPERTY_NAME = "nacos.home";
//
//    private static final String DEFAULT_APP_NAME = System.getProperty("user.home") + File.separator + "nacos";
//
//    private static final String APP_HOME = System.getProperty(NACOS_HOME_PROPERTY_NAME, DEFAULT_APP_NAME);
//
//    private static final String DATA_SOURCE_URL = "jdbc:derby:" + APP_HOME + File.separator + DERBY_BASE_DIR +
//            ";create=true";
//
//    private static final String USER_NAME = "nacos";
//
//    private static final String PASSWORD = "nacos";
//
//    @Bean(name = DATA_SOURCE_BEAN_NAME,destroyMethod = "close")
//    public DataSource nacosConfigDataSource(Environment environment) {
//        BasicDataSource ds = new BasicDataSource();
//        ds.setDriverClassName(JDBC_DRIVER_NAME);
//        ds.setUrl(DATA_SOURCE_URL);
//        ds.setUsername(USER_NAME);
//        ds.setPassword(PASSWORD);
//        ds.setInitialSize(environment.getProperty("db.initialSize", int.class, 20));
//        ds.setMaxActive(environment.getProperty("db.maxActive", int.class, 30));
//        ds.setMaxIdle(environment.getProperty("db.maxIdle", int.class, 50));
//        ds.setMaxWait(environment.getProperty("db.maxWait", long.class, 10000L));
//        ds.setPoolPreparedStatements(true);
//        ds.setTimeBetweenEvictionRunsMillis(TimeUnit.MINUTES.toMillis(10L));
//        ds.setTestWhileIdle(true);
//        return ds;
//    }
//
//}
