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

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import io.netty.channel.ConnectTimeoutException;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * MYSQL health check processor.
 *
 * @author nacos
 */
@Component("mysqlHealthCheckProcessorV1")
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class MysqlHealthCheckProcessor implements HealthCheckProcessor {
    
    public static final String TYPE = "MYSQL";
    
    @Autowired
    private HealthCheckCommon healthCheckCommon;
    
    @Autowired
    private SwitchDomain switchDomain;
    
    public static final int CONNECT_TIMEOUT_MS = 500;
    
    private static final String CHECK_MYSQL_MASTER_SQL = "show global variables where variable_name='read_only'";
    
    private static final String MYSQL_SLAVE_READONLY = "ON";
    
    private static final ConcurrentMap<String, Connection> CONNECTION_POOL = new ConcurrentHashMap<String, Connection>();
    
    public MysqlHealthCheckProcessor() {
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public void process(HealthCheckTask task) {
        List<Instance> ips = task.getCluster().allIPs(false);
        
        SRV_LOG.debug("mysql check, ips:" + ips);
        if (CollectionUtils.isEmpty(ips)) {
            return;
        }
        
        for (Instance ip : ips) {
            try {
                
                if (ip.isMarked()) {
                    if (SRV_LOG.isDebugEnabled()) {
                        SRV_LOG.debug("mysql check, ip is marked as to skip health check, ip: {}", ip.getIp());
                    }
                    continue;
                }
                
                if (!ip.markChecking()) {
                    SRV_LOG.warn("mysql check started before last one finished, service: {}:{}:{}",
                            task.getCluster().getService().getName(), task.getCluster().getName(), ip.getIp());
                    
                    healthCheckCommon.reEvaluateCheckRT(task.getCheckRtNormalized() * 2, task,
                            switchDomain.getMysqlHealthParams());
                    continue;
                }
                
                GlobalExecutor.executeMysqlCheckTask(new MysqlCheckTask(ip, task));
                MetricsMonitor.getMysqlHealthCheckMonitor().incrementAndGet();
            } catch (Exception e) {
                ip.setCheckRt(switchDomain.getMysqlHealthParams().getMax());
                healthCheckCommon.checkFail(ip, task, "mysql:error:" + e.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getMysqlHealthParams().getMax(), task,
                        switchDomain.getMysqlHealthParams());
            }
        }
    }
    
    private class MysqlCheckTask implements Runnable {
        
        private Instance ip;
        
        private HealthCheckTask task;
        
        private long startTime = System.currentTimeMillis();
        
        public MysqlCheckTask(Instance ip, HealthCheckTask task) {
            this.ip = ip;
            this.task = task;
        }
        
        @Override
        public void run() {
            
            Statement statement = null;
            ResultSet resultSet = null;
            
            try {
                
                Cluster cluster = task.getCluster();
                String key = cluster.getService().getName() + ":" + cluster.getName() + ":" + ip.getIp() + ":" + ip
                        .getPort();
                Connection connection = CONNECTION_POOL.get(key);
                Mysql config = (Mysql) cluster.getHealthChecker();
                
                if (connection == null || connection.isClosed()) {
                    String url =
                            "jdbc:mysql://" + ip.getIp() + ":" + ip.getPort() + "?connectTimeout=" + CONNECT_TIMEOUT_MS
                                    + "&socketTimeout=" + CONNECT_TIMEOUT_MS + "&loginTimeout=" + 1;
                    connection = DriverManager.getConnection(url, config.getUser(), config.getPwd());
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
                
                healthCheckCommon.checkOK(ip, task, "mysql:+ok");
                healthCheckCommon.reEvaluateCheckRT(System.currentTimeMillis() - startTime, task,
                        switchDomain.getMysqlHealthParams());
            } catch (SQLException e) {
                // fail immediately
                healthCheckCommon.checkFailNow(ip, task, "mysql:" + e.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task,
                        switchDomain.getMysqlHealthParams());
            } catch (Throwable t) {
                Throwable cause = t;
                int maxStackDepth = 50;
                for (int deepth = 0; deepth < maxStackDepth && cause != null; deepth++) {
                    if (cause instanceof SocketTimeoutException || cause instanceof ConnectTimeoutException
                            || cause instanceof TimeoutException || cause.getCause() instanceof TimeoutException) {
                        
                        healthCheckCommon.checkFail(ip, task, "mysql:timeout:" + cause.getMessage());
                        healthCheckCommon.reEvaluateCheckRT(task.getCheckRtNormalized() * 2, task,
                                switchDomain.getMysqlHealthParams());
                        return;
                    }
                    
                    cause = cause.getCause();
                }
                
                // connection error, probably not reachable
                healthCheckCommon.checkFail(ip, task, "mysql:error:" + t.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getMysqlHealthParams().getMax(), task,
                        switchDomain.getMysqlHealthParams());
            } finally {
                ip.setCheckRt(System.currentTimeMillis() - startTime);
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
