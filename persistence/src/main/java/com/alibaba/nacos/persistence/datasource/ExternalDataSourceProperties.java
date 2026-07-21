/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.common.utils.Preconditions;
import com.alibaba.nacos.common.utils.StringUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Properties of external DataSource.
 *
 * @author Nacos
 */
public class ExternalDataSourceProperties {
    
    private static final String JDBC_DRIVER_NAME = "com.mysql.cj.jdbc.Driver";
    
    private static final String TEST_QUERY = "SELECT 1";
    
    /**
     * Build serveral HikariDataSource.
     *
     * @param environment {@link Environment}
     * @param callback    Callback function when constructing data source
     * @return List of {@link HikariDataSource}
     */
    List<HikariDataSource> build(Environment environment, Callback<HikariDataSource> callback) {
        List<HikariDataSource> dataSources = new ArrayList<>();
        DatasourceConfigResolver configResolver = new DatasourceConfigResolver(environment);
        Integer num = configResolver.resolve("num", Integer.class);
        Preconditions.checkArgument(Objects.nonNull(num),
            "nacos.plugin.datasource.db.num (legacy db.num) is null");
        String defaultUser = configResolver.resolveIndexed("user", 0, true);
        Preconditions.checkArgument(Objects.nonNull(defaultUser),
            "nacos.plugin.datasource.db.user[.index] (legacy db.user[.index]) is null");
        String defaultPassword = configResolver.resolveIndexed("password", 0, true);
        Preconditions.checkArgument(Objects.nonNull(defaultPassword),
            "nacos.plugin.datasource.db.password[.index] "
                + "(legacy db.password[.index]) is null");
        for (int index = 0; index < num; index++) {
            String url = configResolver.resolveIndexed("url", index, false);
            Preconditions.checkArgument(Objects.nonNull(url),
                "nacos.plugin.datasource.db.url.%s (legacy db.url.%s) is null", index,
                index);
            String user = configResolver.resolveIndexed("user", index, true);
            String password = configResolver.resolveIndexed("password", index, true);
            DataSourcePoolProperties poolProperties =
                DataSourcePoolProperties.build(configResolver);
            if (StringUtils.isEmpty(poolProperties.getDataSource().getDriverClassName())) {
                poolProperties.setDriverClassName(JDBC_DRIVER_NAME);
            }
            poolProperties.setJdbcUrl(url.trim());
            poolProperties.setUsername(user.trim());
            poolProperties.setPassword(password.trim());
            HikariDataSource ds = poolProperties.getDataSource();
            if (StringUtils.isEmpty(ds.getConnectionTestQuery())) {
                ds.setConnectionTestQuery(TEST_QUERY);
            }
            
            dataSources.add(ds);
            callback.accept(ds);
        }
        Preconditions.checkArgument(!dataSources.isEmpty(), "no datasource available");
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
