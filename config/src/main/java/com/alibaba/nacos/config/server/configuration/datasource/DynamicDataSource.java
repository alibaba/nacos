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

package com.alibaba.nacos.config.server.configuration.datasource;

import com.alibaba.nacos.config.server.configuration.NacosMultipleDataSourceProperties;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DynamicDataSource.
 *
 * @author Nacos
 */
@Slf4j
public class DynamicDataSource implements DataSource, InitializingBean {
    
    public static final String KEEPALIVE_SQL = "DELETE FROM config_info WHERE data_id='com.alibaba.nacos.testMasterDB'";
    
    public static final String CHECK_DB_HEALTH_SQL = "SELECT * FROM config_info_beta WHERE id = 1";
    
    private List<HikariDataSource> dataSourceList;
    
    private final NacosMultipleDataSourceProperties multipleDataSourceProperties;
    
    private final DataSourceProperties properties;
    
    private volatile HikariDataSource currentDataSource;
    
    private volatile List<Boolean> isHealthList;
    
    private volatile int masterIndex;
    
    private static Pattern ipPattern = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    
    public DynamicDataSource(NacosMultipleDataSourceProperties multipleDataSourceProperties,
            DataSourceProperties properties) {
        this.multipleDataSourceProperties = multipleDataSourceProperties;
        this.properties = properties;
        initDataSource();
        ConfigExecutor.scheduleConfigTask(new DynamicDataSource.SelectMasterTask(), 10, 10, TimeUnit.SECONDS);
        ConfigExecutor.scheduleConfigTask(new DynamicDataSource.CheckDbHealthTask(), 10, 10, TimeUnit.SECONDS);
    }
    
    private void initDataSource() {
        List<DataSourceProperties> dsPropertiesList = multipleDataSourceProperties.getRelational().getDsList();
        dataSourceList = dsPropertiesList.stream().map(this::createDataSource).collect(Collectors.toList());
        new SelectMasterTask().run();
        new CheckDbHealthTask().run();
    }
    
    private HikariDataSource createDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return currentDataSource.getConnection();
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return currentDataSource.getConnection(username, password);
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return currentDataSource.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return currentDataSource.isWrapperFor(iface);
    }
    
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return currentDataSource.getLogWriter();
    }
    
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        currentDataSource.setLogWriter(out);
    }
    
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        currentDataSource.setLoginTimeout(seconds);
    }
    
    @Override
    public int getLoginTimeout() throws SQLException {
        return currentDataSource.getLoginTimeout();
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return currentDataSource.getParentLogger();
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
    
    }
    
    class SelectMasterTask implements Runnable {
        
        @Override
        public void run() {
            for (HikariDataSource dataSource : dataSourceList) {
                try {
                    executeSql(dataSource, KEEPALIVE_SQL);
                    if (!Objects.equals(currentDataSource, dataSource)) {
                        currentDataSource = dataSource;
                    }
                    log.info("current data source :{}", dataSource.getPoolName());
                    break;
                } catch (SQLException e) {
                    log.error("{} was down, Error Code:{}", dataSource.getPoolName(), e.getMessage());
                }
            }
        }
    }
    
    private void executeSql(HikariDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
    
    class CheckDbHealthTask implements Runnable {
        
        @Override
        public void run() {
            for (HikariDataSource dataSource : dataSourceList) {
                try {
                    executeSql(dataSource, CHECK_DB_HEALTH_SQL);
                } catch (SQLException e) {
                    log.error("{} was down, Error Code:{}", dataSource.getPoolName(), e.getMessage());
                }
            }
        }
        
    }
    
}
