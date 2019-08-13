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
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.context.junit4.SpringRunner;
//import org.springframework.transaction.PlatformTransactionManager;
//
//import javax.sql.DataSource;
//
//import static com.alibaba.nacos.config.server.configuration.DataBaseConfiguration.DATA_SOURCE_BEAN_NAME;
//
///**
// * {@link LocalDataSourceConfiguration} Test
// *
// * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
// * @since 0.2.2
// */
//@RunWith(SpringRunner.class)
//@ContextConfiguration(classes = {
//        LocalDataSourceConfiguration.class,
//        DataBaseConfiguration.class,
//        LocalDataSourceConfigurationTest.class
//})
//@TestPropertySource(properties = {
//        "db.initialSize=1",
//        "db.maxActive=2",
//        "db.maxIdle=3",
//        "db.maxWait=4",
//})
//@ActiveProfiles("standalone")
//public class LocalDataSourceConfigurationTest {
//
//    @Autowired
//    @Qualifier(DATA_SOURCE_BEAN_NAME)
//    private DataSource nacosConfigDataSource;
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    @Autowired
//    private PlatformTransactionManager platformTransactionManager;
//
//    @Test
//    public void testDataSource() {
//        Assert.assertNotNull(nacosConfigDataSource);
//        Assert.assertTrue(nacosConfigDataSource instanceof BasicDataSource);
//        BasicDataSource dataSource = BasicDataSource.class.cast(nacosConfigDataSource);
//        Assert.assertEquals(1, dataSource.getInitialSize());
//        Assert.assertEquals(2, dataSource.getMaxActive());
//        Assert.assertEquals(3, dataSource.getMaxIdle());
//        Assert.assertEquals(4, dataSource.getMaxWait());
//    }
//
//    @Test
//    public void testJdbcTemplate() {
//        Assert.assertEquals(jdbcTemplate.getDataSource(), nacosConfigDataSource);
//    }
//
//    @Test
//    public void testPlatformTransactionManager() {
//        Assert.assertTrue(platformTransactionManager instanceof DataSourceTransactionManager);
//        DataSourceTransactionManager transactionManager = (DataSourceTransactionManager) platformTransactionManager;
//        Assert.assertEquals(transactionManager.getDataSource(), nacosConfigDataSource);
//    }
//
//}
