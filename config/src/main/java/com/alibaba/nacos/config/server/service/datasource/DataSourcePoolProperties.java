/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.concurrent.TimeUnit;

/**
 * DataSource pool properties.
 *
 * <p>Nacos server use HikariCP as the datasource pool. So the basic pool properties will based on {@link
 * com.zaxxer.hikari.HikariDataSource}.
 *
 * @author xiweng.yy
 */
public class DataSourcePoolProperties {
    
    public static final long DEFAULT_CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(30L);
    
    public static final long DEFAULT_VALIDATION_TIMEOUT = TimeUnit.SECONDS.toMillis(10L);
    
    public static final int DEFAULT_MAX_POOL_SIZE = 20;
    
    public static final int DEFAULT_MINIMUM_IDLE = 2;
    
    private final HikariDataSource dataSource;
    
    private DataSourcePoolProperties() {
        dataSource = new HikariDataSource();
        dataSource.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        dataSource.setValidationTimeout(DEFAULT_VALIDATION_TIMEOUT);
        dataSource.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
        dataSource.setMinimumIdle(DEFAULT_MINIMUM_IDLE);
    }
    
    /**
     * Build new Hikari config.
     *
     * @return new hikari config
     */
    public static DataSourcePoolProperties build(Environment environment) {
        DataSourcePoolProperties result = new DataSourcePoolProperties();
        Binder.get(environment).bind("db.pool.config", Bindable.ofInstance(result.getDataSource()));
        return result;
    }
    
    public void setDriverClassName(final String driverClassName) {
        dataSource.setDriverClassName(driverClassName);
    }
    
    public void setJdbcUrl(final String jdbcUrl) {
        dataSource.setJdbcUrl(jdbcUrl);
    }
    
    public void setUsername(final String username) {
        dataSource.setUsername(username);
    }
    
    public void setPassword(final String password) {
        dataSource.setPassword(password);
    }
    
    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
