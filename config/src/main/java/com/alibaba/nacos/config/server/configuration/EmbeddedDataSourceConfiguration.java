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

import com.alibaba.nacos.config.server.configuration.datasource.EmbeddedDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;

/**
 * @author zhangshun
 * @date: 2020/1/18 17:32
 */
@ConditionalOnExpression("T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType)."
    + "EMBEDDED.matches('${nacos.datasource.type}')")
@Configuration
public class EmbeddedDataSourceConfiguration {

    private static final String DERBY_BASE_DIR = "data" + File.separator + "derby-data";
    private static final String USER_NAME = "nacos";
    private static final String PASSWORD = "nacos";
    @Value("#{systemProperties['nacos.home']?:systemProperties['user.home']}\\nacos")
    private String nacosHome;


    @Bean("nacos-embedded-db")
    @Primary
    public DataSource embeddedDataSource() {

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(EmbeddedDriver.class.getName());
        hikariConfig.setJdbcUrl("jdbc:derby:" + nacosHome + File.separator + DERBY_BASE_DIR + ";create=true");
        hikariConfig.setUsername(USER_NAME);
        hikariConfig.setPassword(PASSWORD);
        hikariConfig.setMaximumPoolSize(80);
        hikariConfig.setIdleTimeout(30_000L);
        hikariConfig.setConnectionTimeout(10000L);

        return new EmbeddedDataSource(new HikariDataSource(hikariConfig));


    }
}
