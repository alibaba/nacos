/*
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
 */
package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.configuration.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * @author Nacos
 */
@ConditionalOnExpression("T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).MYSQL.matches('${nacos.datasource.type}')" +
    " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).ORACLE.matches('${nacos.datasource.type}')" +
    " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).POSTGRESQL.matches('${nacos.datasource.type}')")
@Configuration
public class RelationalDataSourceConfiguration {

//    @Bean("db-master-properties")
//    @ConfigurationProperties("nacos.datasource.relational.master")
//    public DataSourceProperties masterDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//    @Bean("db-slave-properties")
//    @ConfigurationProperties("nacos.datasource.relational.slave")
//    public DataSourceProperties slaveDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//
//    @Bean("nacos-db-master")
//    @ConfigurationProperties(prefix = "nacos.datasource.relational.master.hikari")
//    HikariDataSource masterDataSource(NacosMultipleDataSourceProperties nacosMultipleDataSourceProperties) {
//        DataSourceProperties master =
//                nacosMultipleDataSourceProperties.getRelational().getMaster();
//        HikariDataSource dataSource = createDataSource(master);
//        if (StringUtils.hasText(master.getName())) {
//            dataSource.setPoolName(master.getName());
//        } else {
//            dataSource.setPoolName("nacos-db-master");
//        }
//        return dataSource;
//    }
//
//    @Bean("nacos-db-salve")
//    @ConfigurationProperties(prefix = "nacos.datasource.relational.slave.hikari")
//    HikariDataSource salveDataSource(NacosMultipleDataSourceProperties nacosMultipleDataSourceProperties) {
//        boolean slaveEnable = nacosMultipleDataSourceProperties.getRelational().isSlaveEnable();
//        DataSourceProperties master = nacosMultipleDataSourceProperties.getRelational().getMaster();
//        DataSourceProperties slave = nacosMultipleDataSourceProperties.getRelational().getSlave();
//        DataSourceProperties properties = slaveEnable ? slave : master;
//        HikariDataSource dataSource = createDataSource(properties);
//        if (StringUtils.hasText(properties.getName())) {
//            dataSource.setPoolName(properties.getName());
//        } else {
//            dataSource.setPoolName("nacos-db-salve");
//        }
//        return dataSource;
//    }


    @Primary
    @Bean
    public DataSource dataSource(NacosMultipleDataSourceProperties multipleDataSourceProperties, DataSourceProperties properties) {
        return new DynamicDataSource(multipleDataSourceProperties, properties);
    }



}
