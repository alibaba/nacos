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

import com.alibaba.nacos.config.server.enums.ConfigOperationEnum;
import com.alibaba.nacos.config.server.model.log.DBRequest;
import com.alibaba.nacos.config.server.service.DistributeProtocolAware;
import com.alibaba.nacos.config.server.utils.LogKeyUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.NodeManager;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class ClusterDataSourceV2 extends DistributeProtocolAware implements DataSource {

    private BasicDataSource target;

    private final Map<String, ConnectionHolder> connectionHolderMap = new ConcurrentHashMap<>(128);

    private final ThreadLocal<String> xidLocal = new ThreadLocal<>();

    private JRaftProtocol protocol;
    private NodeManager nodeManager;

    private String self;

    public ClusterDataSourceV2(BasicDataSource target) {
        super();
        this.target = target;
    }

    @PostConstruct
    protected void init() {
        protocol = SpringUtils.getBean(JRaftProtocol.class);
        nodeManager = SpringUtils.getBean(NodeManager.class);
        self = nodeManager.self().address();
    }

    @Override
    public Connection getConnection() throws SQLException {
        String xid;

        // TODO 直接调用 getConnection 方法，需不需要调用submit通知其他节点执行open操作？
        // TODO 如果需要，那么这个时候的Connection应该需要进行一次Wrap封装，

        if (StringUtils.isBlank((xid = xidLocal.get()))) {
            return target.getConnection();
        }
        return connectionHolderMap.get(xid).connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by
        // the createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
        // return createDataSource().getConnection(username, password);
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
        String xid = createXID();
        xid = openDistributeTransaction(xid);

        // notify all nacos-server node to open xid-transaction

        notifyOpenXID(xid);

        return xid;
    }

    public String openDistributeTransaction(String xid) {
        connectionHolderMap.computeIfAbsent(xid, s -> {
            final ConnectionHolder holder = new ConnectionHolder();
            try {
                holder.xid = s;
                holder.connection = target.getConnection();
                holder.connection.setAutoCommit(false);
                holder.savepoint = holder.connection.setSavepoint();

                return holder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        xidLocal.set(xid);
        return xid;
    }

    // commit

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
            submit(key, dbRequest, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
        } catch (Exception e) {
            LogUtil.defaultLog.error("error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
    }

    // rollback

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
            submit(key, dbRequest, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
        } catch (Exception e) {
            LogUtil.defaultLog.error("error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
    }

    // free connection

    public void freed(String xid) {
        ConnectionHolder holder = connectionHolderMap.get(xid);
        if (holder != null) {
            holder.close();
        }
        connectionHolderMap.remove(xid);
    }

    public ConnectionHolder getHolderByXID(String xid) {
        return connectionHolderMap.get(xid);
    }

    // to notify nother node open transaction

    private void notifyOpenXID(String xid) {
        final DBRequest request = DBRequest.builder()
                .xid(xid)
                .operation("open")
                .build();

        final String key = LogKeyUtils.build("CONFIG", "open", xid);

        submit(key, request, ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation());
    }

    // ip:port@@snakeflowerId

    // TODO XID 是否真的需要 SnakeflowerID，直接本机的UUID是否就可以了

    private String createXID() {
        if (StringUtils.isNotBlank(currentXID())) {
            return currentXID();
        }
        return self + "-" + UUID.randomUUID();
    }

    private void checkXID(String xid) {
        if (!connectionHolderMap.containsKey(xid)) {
            throw new NoSuchElementException("The XID is illegal and there is no " +
                    "corresponding distributed transaction record");
        }
    }

    private <T> void submit(String key, T data, String operation) {

        final Map<String, String> extendInfo = new HashMap<>(8);

        extendInfo.put("xid", currentXID());

        submit(key, data, operation, extendInfo);
    }

    // The holder of the Connection under the current XID carries the savepoint at
    // the beginning of the distributed transaction, the XID, and the commit and
    // rollback operations of the Connection.

    public static class ConnectionHolder {

        private String xid;
        private Connection connection;
        private Savepoint savepoint;

        public String getXid() {
            return xid;
        }

        public Connection getConnection() {
            return connection;
        }

        public Savepoint getSavepoint() {
            return savepoint;
        }

        public void commit() throws SQLException {
            connection.commit();
        }

        public void rollback() throws SQLException {
            if (Objects.isNull(savepoint)) {
                connection.rollback();
            } else {
                connection.rollback(savepoint);
            }
        }

        public void close() {
            releaseSavepoint();
            try {
                connection.close();
            } catch (Exception e) {
                LogUtil.defaultLog.error("xid : [{}], this connection close has error : {}", xid, ExceptionUtil.getAllExceptionMsg(e));
            }
        }

        private void releaseSavepoint() {
            try {
                if (Objects.nonNull(savepoint)) {
                    connection.releaseSavepoint(savepoint);
                }
            } catch (Exception e) {
                LogUtil.defaultLog.debug("xid : [{}], Could not explicitly release JDBC savepoint : {}", xid, ExceptionUtil.getAllExceptionMsg(e));
            }
        }

    }
}
