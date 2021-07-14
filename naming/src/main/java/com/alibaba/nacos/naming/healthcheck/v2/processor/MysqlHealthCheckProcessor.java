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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.healthcheck.v2.HealthCheckTaskV2;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import io.netty.channel.ConnectTimeoutException;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import static com.alibaba.nacos.naming.misc.Loggers.SRV_LOG;

/**
 * TCP health check processor for v2.x.
 *
 * <p>Current health check logic is same as v1.x. TODO refactor health check for v2.x.
 *
 * @author xiweng.yy
 */
@Component
@SuppressWarnings("PMD.ThreadPoolCreationRule")
public class MysqlHealthCheckProcessor implements HealthCheckProcessorV2 {
    
    public static final String TYPE = HealthCheckType.MYSQL.name();
    
    private final HealthCheckCommonV2 healthCheckCommon;
    
    private final SwitchDomain switchDomain;
    
    public static final int CONNECT_TIMEOUT_MS = 500;
    
    private static final String CHECK_MYSQL_MASTER_SQL = "show global variables where variable_name='read_only'";
    
    private static final String MYSQL_SLAVE_READONLY = "ON";
    
    private static final ConcurrentMap<String, Connection> CONNECTION_POOL = new ConcurrentHashMap<String, Connection>();
    
    public MysqlHealthCheckProcessor(HealthCheckCommonV2 healthCheckCommon, SwitchDomain switchDomain) {
        this.healthCheckCommon = healthCheckCommon;
        this.switchDomain = switchDomain;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public void process(HealthCheckTaskV2 task, Service service, ClusterMetadata metadata) {
        HealthCheckInstancePublishInfo instance = (HealthCheckInstancePublishInfo) task.getClient()
                .getInstancePublishInfo(service);
        if (null == instance) {
            return;
        }
        SRV_LOG.debug("mysql check, ip:" + instance);
        try {
            // TODO handle marked(white list) logic like v1.x.
            if (!instance.tryStartCheck()) {
                SRV_LOG.warn("mysql check started before last one finished, service: {} : {} : {}:{}",
                        service.getGroupedServiceName(), instance.getCluster(), instance.getIp(), instance.getPort());
                healthCheckCommon
                        .reEvaluateCheckRT(task.getCheckRtNormalized() * 2, task, switchDomain.getMysqlHealthParams());
                return;
            }
            GlobalExecutor.executeMysqlCheckTask(new MysqlCheckTask(task, service, instance, metadata));
            MetricsMonitor.getMysqlHealthCheckMonitor().incrementAndGet();
        } catch (Exception e) {
            instance.setCheckRt(switchDomain.getMysqlHealthParams().getMax());
            healthCheckCommon.checkFail(task, service, "mysql:error:" + e.getMessage());
            healthCheckCommon.reEvaluateCheckRT(switchDomain.getMysqlHealthParams().getMax(), task,
                    switchDomain.getMysqlHealthParams());
        }
    }
    
    private class MysqlCheckTask implements Runnable {
        
        private final HealthCheckTaskV2 task;
        
        private final Service service;
        
        private final HealthCheckInstancePublishInfo instance;
        
        private final ClusterMetadata metadata;
        
        private long startTime = System.currentTimeMillis();
        
        public MysqlCheckTask(HealthCheckTaskV2 task, Service service, HealthCheckInstancePublishInfo instance,
                ClusterMetadata metadata) {
            this.task = task;
            this.service = service;
            this.instance = instance;
            this.metadata = metadata;
        }
        
        @Override
        public void run() {
            
            Statement statement = null;
            ResultSet resultSet = null;
            
            try {
                String clusterName = instance.getCluster();
                String key =
                        service.getGroupedServiceName() + ":" + clusterName + ":" + instance.getIp() + ":" + instance
                                .getPort();
                Connection connection = CONNECTION_POOL.get(key);
                Mysql config = (Mysql) metadata.getHealthChecker();
                
                if (connection == null || connection.isClosed()) {
                    String url = "jdbc:mysql://" + instance.getIp() + ":" + instance.getPort() + "?connectTimeout="
                            + CONNECT_TIMEOUT_MS + "&socketTimeout=" + CONNECT_TIMEOUT_MS + "&loginTimeout=" + 1;
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
                
                healthCheckCommon.checkOk(task, service, "mysql:+ok");
                healthCheckCommon.reEvaluateCheckRT(System.currentTimeMillis() - startTime, task,
                        switchDomain.getMysqlHealthParams());
            } catch (SQLException e) {
                // fail immediately
                healthCheckCommon.checkFailNow(task, service, "mysql:" + e.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getHttpHealthParams().getMax(), task,
                        switchDomain.getMysqlHealthParams());
            } catch (Throwable t) {
                Throwable cause = t;
                int maxStackDepth = 50;
                for (int deepth = 0; deepth < maxStackDepth && cause != null; deepth++) {
                    if (cause instanceof SocketTimeoutException || cause instanceof ConnectTimeoutException
                            || cause instanceof TimeoutException || cause.getCause() instanceof TimeoutException) {
                        
                        healthCheckCommon.checkFail(task, service, "mysql:timeout:" + cause.getMessage());
                        healthCheckCommon.reEvaluateCheckRT(task.getCheckRtNormalized() * 2, task,
                                switchDomain.getMysqlHealthParams());
                        return;
                    }
                    
                    cause = cause.getCause();
                }
                
                // connection error, probably not reachable
                healthCheckCommon.checkFail(task, service, "mysql:error:" + t.getMessage());
                healthCheckCommon.reEvaluateCheckRT(switchDomain.getMysqlHealthParams().getMax(), task,
                        switchDomain.getMysqlHealthParams());
            } finally {
                instance.setCheckRt(System.currentTimeMillis() - startTime);
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
