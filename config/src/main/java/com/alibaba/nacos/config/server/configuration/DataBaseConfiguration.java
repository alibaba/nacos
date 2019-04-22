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
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.env.Environment;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.transaction.PlatformTransactionManager;
//
//import javax.sql.DataSource;
//
///**
// * DataBase {@link Configuration}
// *
// * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
// * @since 0.2.2
// */
//@Configuration
//public class DataBaseConfiguration {
//
//    /**
//     * The bean name of {@link DataSource} for Nacos Config
//     */
//    public static final String DATA_SOURCE_BEAN_NAME = "nacosConfigDataSource";
//
//    @Bean
//    @Autowired
//    public JdbcTemplate jdbcTemplate(@Qualifier(DATA_SOURCE_BEAN_NAME) DataSource dataSource, Environment environment) {
//        JdbcTemplate jdbcTemplate = new JdbcTemplate();
//        jdbcTemplate = new JdbcTemplate();
//        jdbcTemplate.setMaxRows(50000);
//        jdbcTemplate.setQueryTimeout(5000);
//        jdbcTemplate.setDataSource(dataSource);
//        return jdbcTemplate;
//    }
//
//    @Bean
//    @Autowired
//    public PlatformTransactionManager transactionManager(@Qualifier(DATA_SOURCE_BEAN_NAME) DataSource dataSource) {
//        DataSourceTransactionManager manager = new DataSourceTransactionManager();
//        manager.setDataSource(dataSource);
//        return manager;
//    }
//
//}
