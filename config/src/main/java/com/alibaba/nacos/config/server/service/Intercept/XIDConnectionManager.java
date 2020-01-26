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

package com.alibaba.nacos.config.server.service.Intercept;

import com.alibaba.nacos.core.distributed.id.DistributeIDManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomicity guarantee of database transactions under asynchronous,
 * corresponding transaction asynchronous database concatenation
 * through transaction ID XID
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
@DependsOn(value = "serverNodeManager")
public class XIDConnectionManager {

    private static final String RESOURCES_NAME = "XID";

    private final Map<String, ConnectionHolder> connectionHolderMap = new ConcurrentHashMap<>(128);

    @Autowired
    private DataSource dataSource;

    protected void init() {
        DistributeIDManager.register(RESOURCES_NAME);
    }

    public String openDistributeTransaction() {
        String xid = String.valueOf(DistributeIDManager.nextId(RESOURCES_NAME));
        connectionHolderMap.computeIfAbsent(xid, s -> {
            final ConnectionHolder holder = new ConnectionHolder();
            try {
                holder.connection = dataSource.getConnection();
                holder.connection.setAutoCommit(false);
                holder.savepoint = holder.connection.setSavepoint();
                return holder;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return xid;
    }

    public void commit(String xid) {
        checkXID(xid);
        try {
            final ConnectionHolder holder = connectionHolderMap.get(xid);
            holder.connection.commit();
        } catch (Exception e) {

        }
    }

    public void rollback(String xid) {
        checkXID(xid);
        try {
            final ConnectionHolder holder = connectionHolderMap.get(xid);
            holder.connection.rollback(holder.savepoint);
        } catch (Exception e) {

        }
    }

    private void checkXID(String xid) {
        if (!connectionHolderMap.containsKey(xid)) {
            throw new NoSuchElementException("The XID is illegal and there is no " +
                    "corresponding distributed transaction record");
        }
    }

    private class ConnectionHolder {

        private Connection connection;
        private Savepoint savepoint;

    }

}
