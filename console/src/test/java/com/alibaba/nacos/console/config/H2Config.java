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

package com.alibaba.nacos.console.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Configuration
@EnableJpaRepositories(entityManagerFactoryRef = "customerEntityManagerFactory", transactionManagerRef = "customerTransactionManager", basePackages = "com.alibaba.nacos.config.server.modules.repository")
public class H2Config {
    
    @Bean
    public PlatformTransactionManager customerTransactionManager() {
        return new JpaTransactionManager(customerEntityManagerFactory().getObject());
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean customerEntityManagerFactory() {
        
        HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
        jpaVendorAdapter.setGenerateDdl(true);
        
        LocalContainerEntityManagerFactoryBean factoryBean = new LocalContainerEntityManagerFactoryBean();
        
        factoryBean.setDataSource(customerDataSource());
        factoryBean.setJpaVendorAdapter(jpaVendorAdapter);
        factoryBean.setPackagesToScan("com.alibaba.nacos.config.server.modules.entity");
        
        return factoryBean;
    }
    
    @Bean
    public DataSource customerDataSource() {
        
        return new EmbeddedDatabaseBuilder().//
                setType(EmbeddedDatabaseType.H2).//
                setName("customers").//
                build();
    }
}
