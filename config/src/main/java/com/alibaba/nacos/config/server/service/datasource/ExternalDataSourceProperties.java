/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.config.server.service.datasource;

import static com.alibaba.nacos.common.utils.CollectionUtils.getOrDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Properties of external DataSource.
 *
 * @author Nacos
 */
public class ExternalDataSourceProperties {
    
    private static final String JDBC_DRIVER_NAME = "com.mysql.cj.jdbc.Driver";
    
    public static final long CONNECTION_TIMEOUT_MS = 3000L;
    
    public static final long VALIDATION_TIMEOUT = 10L;
    
    public static final String TEST_QUERY = "SELECT 1 FROM dual";
    
    public static final int DEFAULT_MAX_POOL_SIZE = 20;
    
    public static final int DEFAULT_MINIMUM_IDLE = 20;
    
    private Integer num;
    
    private List<String> url = new ArrayList<>();
    
    private List<String> user = new ArrayList<>();
    
    private List<String> password = new ArrayList<>();
    
    private List<Integer> maxPoolSize = new ArrayList<>();
    
    private List<Integer> minIdle = new ArrayList<>();
    
    public void setNum(Integer num) {
        this.num = num;
    }
    
    public void setUrl(List<String> url) {
        this.url = url;
    }
    
    public void setUser(List<String> user) {
        this.user = user;
    }
    
    public void setPassword(List<String> password) {
        this.password = password;
    }
    
    public void setMaxPoolSize(List<Integer> maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
    
    public void setMinIdle(List<Integer> minIdle) {
        this.minIdle = minIdle;
    }
    
    /**
     * Build serveral HikariDataSource.
     *
     * @param environment {@link Environment}
     * @param callback    Callback function when constructing data source
     * @return List of {@link HikariDataSource}
     */
    List<HikariDataSource> build(Environment environment, Callback<HikariDataSource> callback) {
        List<HikariDataSource> dataSources = new ArrayList<>();
        Binder.get(environment).bind("db", Bindable.ofInstance(this));
        Preconditions.checkArgument(Objects.nonNull(num), "db.num is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(user), "db.user or db.user.[index] is null");
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(password), "db.password or db.password.[index] is null");
        for (int index = 0; index < num; index++) {
            int currentSize = index + 1;
            Preconditions.checkArgument(url.size() >= currentSize, "db.url.%s is null", index);
            HikariDataSource ds = new HikariDataSource();
            ds.setDriverClassName(JDBC_DRIVER_NAME);
            ds.setJdbcUrl(url.get(index).trim());
            ds.setUsername(getOrDefault(user, index, user.get(0)).trim());
            ds.setPassword(getOrDefault(password, index, password.get(0)).trim());
            ds.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            ds.setMaximumPoolSize(getOrDefault(maxPoolSize, index, DEFAULT_MAX_POOL_SIZE));
            ds.setMinimumIdle(getOrDefault(minIdle, index, DEFAULT_MINIMUM_IDLE));
            // Check the connection pool every 10 minutes
            ds.setValidationTimeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT));
            ds.setConnectionTestQuery(TEST_QUERY);
            dataSources.add(ds);
            callback.accept(ds);
        }
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(dataSources), "no datasource available");
        return dataSources;
    }
    
    interface Callback<D> {
        
        /**
         * Perform custom logic.
         *
         * @param datasource dataSource.
         */
        void accept(D datasource);
    }
}
