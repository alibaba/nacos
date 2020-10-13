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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

/**
 * RelationalDataSourceConfiguration.
 *
 * @author Nacos
 */
@ConditionalOnExpression(
        "T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).MYSQL.matches('${nacos.datasource.type}')"
                + " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).ORACLE.matches('${nacos.datasource.type}')"
                + " || T(com.alibaba.nacos.config.server.configuration.datasource.DataSourceType).POSTGRESQL.matches('${nacos.datasource.type}')")
@EnableJpaRepositories(basePackages = "com.alibaba.nacos.config.server.modules.repository")
@Configuration
public class RelationalDataSourceConfiguration {
    
    @Bean("nacos-db-properties")
    @ConfigurationProperties("nacos.datasource.relational")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Primary
    @Bean
    public DataSource dataSource(NacosMultipleDataSourceProperties multipleDataSourceProperties,
            DataSourceProperties properties) {
        return new DynamicDataSource(multipleDataSourceProperties, properties);
    }
    
}
