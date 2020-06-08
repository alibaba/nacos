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

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Nacos
 */
@Slf4j
public class DynamicDataSource implements DataSource, InitializingBean {
    public static final String KEEPALIVE_SQL = "DELETE FROM config_info WHERE data_id='com.alibaba.nacos.testMasterDB'";
    private final HikariDataSource master;
    private final HikariDataSource slave;
    private volatile HikariDataSource currentDataSource;
    private static ScheduledExecutorService scheduledExecutorService = Executors
            .newScheduledThreadPool(10, new ThreadFactory() {
                AtomicInteger count = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("com.alibaba.nacos.server.Timer-" + count.getAndIncrement());
                    return t;
                }
            });

    public DynamicDataSource(HikariDataSource master, HikariDataSource slave) {
        this.master = master;
        this.slave = slave;
        this.currentDataSource = master;
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
        scheduledExecutorService.scheduleWithFixedDelay(new SelectMasterTask(), 10, 10,
                TimeUnit.SECONDS);
    }

    class SelectMasterTask implements Runnable {

        @Override
        public void run() {
            List<HikariDataSource> dataSources = Stream.of(master, slave).collect(Collectors.toList());
            for (HikariDataSource dataSource : dataSources) {
                try {
                    executeSql(dataSource);
                    if (!Objects.equals(currentDataSource, dataSource)) {
                        currentDataSource = dataSource;
                    }
                    log.info("Current Data Source :{}", dataSource.getPoolName());
                    break;
                } catch (SQLException e) {
                    log.error("{} was down, Error Code:{}", dataSource.getPoolName(),
                            e.getMessage());
                }

            }
        }
    }

    private void executeSql(HikariDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(KEEPALIVE_SQL);
        }
    }
}
