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
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.utils.DatasourcePlatformUtil;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ExternalDataSourceProperties.class);
    
    /**
     * Compatibility default driver, used only when neither the pool config nor the selected
     * dialect plugin provides a driver class name.
     */
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
        String defaultDriverClassName = resolveDefaultDriverClassName(environment);
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
                poolProperties.setDriverClassName(defaultDriverClassName);
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
    
    /**
     * Resolve the driver class used when {@code pool.config.driver-class-name} is blank.
     *
     * <p>The selected {@link DatabaseDialect} plugin is asked first, so that selecting a dialect via
     * {@code nacos.plugin.datasource-dialect.type} is enough for the built-in datasource plugins.
     * When the dialect cannot be resolved or does not provide a default driver, the MySQL
     * compatibility default is kept.
     *
     * @param environment environment used to resolve the selected dialect
     * @return default JDBC driver class name, never blank
     */
    String resolveDefaultDriverClassName(Environment environment) {
        String dialectType =
            DatasourcePlatformUtil.getDatasourcePlatform(environment, PersistenceConstant.MYSQL);
        String driverClassName = null;
        try {
            DatabaseDialect dialect = DatabaseDialectManager.getInstance().getDialect(dialectType);
            driverClassName = dialect.getDefaultDriverClassName();
        } catch (IllegalStateException e) {
            LOGGER.warn("[ExternalDataSourceProperties] Cannot resolve DatabaseDialect `{}` "
                + "for default driver class name: {}", dialectType, e.getMessage());
        }
        if (StringUtils.isBlank(driverClassName)) {
            LOGGER.info("[ExternalDataSourceProperties] DatabaseDialect `{}` provides no default "
                + "driver class name, fallback to compatibility default `{}`", dialectType,
                JDBC_DRIVER_NAME);
            return JDBC_DRIVER_NAME;
        }
        LOGGER.info("[ExternalDataSourceProperties] Use default driver class name `{}` "
            + "provided by DatabaseDialect `{}`", driverClassName, dialectType);
        return driverClassName.trim();
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
