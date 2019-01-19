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
package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import io.netty.channel.ConnectTimeoutException;
import org.apache.commons.collections.CollectionUtils;

import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.*;

import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * MYSQL health check processor
 *
 * @author nacos
 */
public class MysqlHealthCheckProcessor extends AbstractHealthCheckProcessor {

    private static final String CHECK_MYSQL_MASTER_SQL = "show global variables where variable_name='read_only'";
    private static final String MYSQL_SLAVE_READONLY = "ON";

    private static ConcurrentMap<String, Connection> CONNECTION_POOL
            = new ConcurrentHashMap<String, Connection>();

    private static ExecutorService EXECUTOR;

    static {

        int processorCount = Runtime.getRuntime().availableProcessors();
        EXECUTOR
                = Executors.newFixedThreadPool(processorCount <= 1 ? 1 : processorCount / 2,
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName("com.nacos.mysql.checker");
                        return thread;
                    }
                }
        );
    }

    public MysqlHealthCheckProcessor() {
    }

    @Override
    public String getType() {
        return "MYSQL";
    }

    @Override
    public void process(HealthCheckTask task) {
        List<IpAddress> ips = task.getCluster().allIPs();

        SRV_LOG.debug("mysql check, ips:" + ips);
        if (CollectionUtils.isEmpty(ips)) {
            return;
        }

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) task.getCluster().getDom();

        if (!isHealthCheckEnabled(virtualClusterDomain)) {
            return;
        }

        for (IpAddress ip : ips) {
            try {

                if (ip.isMarked()) {
                    if (SRV_LOG.isDebugEnabled()) {
                        SRV_LOG.debug("mysql check, ip is marked as to skip health check, ip: {}", ip.getIp());
                    }
                    continue;
                }

                if (!ip.markChecking()) {
                    SRV_LOG.warn("mysql check started before last one finished, dom: {}:{}:{}",
                        task.getCluster().getDom().getName(), task.getCluster().getName(), ip.getIp());

                    reEvaluateCheckRT(task.getCheckRTNormalized() * 2, task, Switch.getMysqlHealthParams());
                    continue;
                }

                EXECUTOR.execute(new MysqlCheckTask(ip, task));
                MetricsMonitor.getMysqlHealthCheckMonitor().incrementAndGet();
            } catch (Exception e) {
                ip.setCheckRT(Switch.getMysqlHealthParams().getMax());
                checkFail(ip, task, "mysql:error:" + e.getMessage());
                reEvaluateCheckRT(Switch.getMysqlHealthParams().getMax(), task, Switch.getMysqlHealthParams());
            }
        }
    }

    private class MysqlCheckTask implements Runnable {
        private IpAddress ip;
        private HealthCheckTask task;
        private long startTime = System.currentTimeMillis();

        public MysqlCheckTask(IpAddress ip, HealthCheckTask task) {
            this.ip = ip;
            this.task = task;
        }

        @Override
        public void run() {

            Statement statement = null;
            ResultSet resultSet = null;

            try {
                ;
                Cluster cluster = task.getCluster();
                String key = cluster.getDom().getName() + ":" + cluster.getName() + ":" + ip.getIp() + ":" + ip.getPort();
                Connection connection = CONNECTION_POOL.get(key);
                AbstractHealthChecker.Mysql config = (AbstractHealthChecker.Mysql) cluster.getHealthChecker();

                if (connection == null || connection.isClosed()) {
                    MysqlDataSource dataSource = new MysqlDataSource();
                    dataSource.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    dataSource.setSocketTimeout(CONNECT_TIMEOUT_MS);
                    dataSource.setUser(config.getUser());
                    dataSource.setPassword(config.getPwd());
                    dataSource.setLoginTimeout(1);

                    dataSource.setServerName(ip.getIp());
                    dataSource.setPort(ip.getPort());

                    connection = dataSource.getConnection();
                    CONNECTION_POOL.put(key, connection);
                }

                statement = connection.createStatement();
                statement.setQueryTimeout(1);

                resultSet = statement.executeQuery(config.getCmd());
                int resultColumnIndex = 2;

                if (CHECK_MYSQL_MASTER_SQL.equals(config.getCmd())) {
                    resultSet.next();
                    if (MYSQL_SLAVE_READONLY.equals(resultSet.getString(resultColumnIndex))) {
                        throw new IllegalStateException("current node is slave!");
                    }
                }

                checkOK(ip, task, "mysql:+ok");
                reEvaluateCheckRT(System.currentTimeMillis() - startTime, task, Switch.getMysqlHealthParams());
            } catch (SQLException e) {
                // fail immediately
                checkFailNow(ip, task, "mysql:" + e.getMessage());
                reEvaluateCheckRT(Switch.getHttpHealthParams().getMax(), task, Switch.getMysqlHealthParams());
            } catch (Throwable t) {
                Throwable cause = t;
                int maxStackDepth = 50;
                for (int deepth = 0; deepth < maxStackDepth && cause != null; deepth++) {
                    if (cause instanceof SocketTimeoutException
                            || cause instanceof ConnectTimeoutException
                            || cause instanceof TimeoutException
                            || cause.getCause() instanceof TimeoutException) {

                        checkFail(ip, task, "mysql:timeout:" + cause.getMessage());
                        reEvaluateCheckRT(task.getCheckRTNormalized() * 2, task, Switch.getMysqlHealthParams());
                        return;
                    }

                    cause = cause.getCause();
                }

                // connection error, probably not reachable
                checkFail(ip, task, "mysql:error:" + t.getMessage());
                reEvaluateCheckRT(Switch.getMysqlHealthParams().getMax(), task, Switch.getMysqlHealthParams());
            } finally {
                ip.setCheckRT(System.currentTimeMillis() - startTime);
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        Loggers.SRV_LOG.error("[MYSQL-CHECK] failed to close statement:" + statement, e);
                    }
                }
                if (resultSet != null) {
                    try {
                        resultSet.close();
                    } catch (SQLException e) {
                        Loggers.SRV_LOG.error("[MYSQL-CHECK] failed to close resultSet:" + resultSet, e);
                    }
                }
            }
        }
    }
}
