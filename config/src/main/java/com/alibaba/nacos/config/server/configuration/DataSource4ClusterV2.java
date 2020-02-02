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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.config.server.enums.ConfigOperationEnum;
import com.alibaba.nacos.config.server.model.log.DBRequest;
import com.alibaba.nacos.config.server.utils.LogKeyUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.distributed.NDatum;
import com.alibaba.nacos.core.distributed.id.DistributeIDManager;
import com.alibaba.nacos.core.distributed.raft.jraft.JRaftProtocol;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DataSource4ClusterV2 implements DataSource {

    private BasicDataSource target;

    private static final String RESOURCES_NAME = "XID";

    private final Map<String, ConnectionHolder> connectionHolderMap = new ConcurrentHashMap<>(128);

    private final ThreadLocal<String> xidLocal = new ThreadLocal<>();

    private JRaftProtocol protocol;

    public DataSource4ClusterV2(BasicDataSource target) {
        this.target = target;
    }

    @PostConstruct
    protected void init() {
        DistributeIDManager.register(RESOURCES_NAME);
        protocol = SpringUtils.getBean(JRaftProtocol.class);
    }

    @Override
    public Connection getConnection() throws SQLException {
        String xid;
        if (StringUtils.isBlank((xid = xidLocal.get()))) {
            xid = openDistributeTransaction();
        }
        return connectionHolderMap.get(xid).connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        String xid;
        if (StringUtils.isBlank((xid = xidLocal.get()))) {
            xid = openDistributeTransaction(username, password);
        }
        return connectionHolderMap.get(xid).connection;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return target.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        target.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        target.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return target.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return target.isWrapperFor(iface);
    }

    public String currentXID() {
        return xidLocal.get();
    }

    public String openDistributeTransaction() {
        String xid = String.valueOf(DistributeIDManager.nextId(RESOURCES_NAME));
        return openDistributeTransaction(xid);
    }

    public String openDistributeTransaction(String xid) {
        connectionHolderMap.computeIfAbsent(xid, s -> {
            final ConnectionHolder holder = new ConnectionHolder();
            try {
                holder.connection = target.getConnection();
                holder.connection.setAutoCommit(false);
                holder.savepoint = holder.connection.setSavepoint();

                open(s);

                return holder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        xidLocal.set(xid);
        return xid;
    }

    public String openDistributeTransaction(String username, String password) {
        String xid = String.valueOf(DistributeIDManager.nextId(RESOURCES_NAME));
        connectionHolderMap.computeIfAbsent(xid, s -> {
            final ConnectionHolder holder = new ConnectionHolder();
            try {
                holder.connection = target.getConnection(username, password);
                holder.connection.setAutoCommit(false);
                holder.savepoint = holder.connection.setSavepoint();

                open(xid);

                return holder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        xidLocal.set(xid);
        return xid;
    }

    // COMMIT

    public void commitLocal() {
        try {
            commit(currentXID());
        } finally {
            xidLocal.remove();
        }
    }

    public void commit(String xid) {
        checkXID(xid);
        final String key = LogKeyUtils.build("CONFIG", "commit", xid);
        try {
            final DBRequest dbRequest = DBRequest.builder()
                    .xid(currentXID())
                    .operation("commit")
                    .build();
            commit(key, dbRequest, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
        } catch (Exception e) {
            LogUtil.defaultLog.error("error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
    }

    // ROLLBACK

    public void rollbackLocal() {
        try {
            rollback(currentXID());
        } finally {
            xidLocal.remove();
        }
    }

    public void rollback(String xid) {
        checkXID(xid);
        final String key = LogKeyUtils.build("CONFIG", "rollback", xid);
        try {
            final DBRequest dbRequest = DBRequest.builder()
                    .xid(currentXID())
                    .operation("rollback")
                    .build();
            commit(key, dbRequest, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
        } catch (Exception e) {
            LogUtil.defaultLog.error("error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
    }

    // free connection

    public void freedLocal() {
        freed(currentXID());
    }

    public void freed(String xid) {
        connectionHolderMap.remove(xid);
    }

    public ConnectionHolder getHolderByXID(String xid) {
        return connectionHolderMap.get(xid);
    }

    // to notify nother node open transaction

    private void open(String xid) {
        final DBRequest request = DBRequest.builder()
                .xid(xid)
                .operation("open")
                .build();

        final String key = LogKeyUtils.build("CONFIG", "open", xid);

        commit(key, request, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
    }

    private void checkXID(String xid) {
        if (!connectionHolderMap.containsKey(xid)) {
            throw new NoSuchElementException("The XID is illegal and there is no " +
                    "corresponding distributed transaction record");
        }
    }

    private <T> void commit(String key, T data, String operation) {

        final Map<String, String> extendInfo = new HashMap<>(8);

        extendInfo.put("xid", currentXID());

        final NDatum datum = NDatum.builder()
                .operation(operation)
                .className(data.getClass().getCanonicalName())
                .key(key)
                .data(JSON.toJSONBytes(data))
                .extendInfo(extendInfo)
                .build();

        try {
            protocol.submit(datum);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ConnectionHolder {

        private Connection connection;
        private Savepoint savepoint;

        public void commit() throws SQLException {
            connection.commit();
        }

        public void rollback() throws SQLException {
            connection.rollback(savepoint);
        }

    }
}
