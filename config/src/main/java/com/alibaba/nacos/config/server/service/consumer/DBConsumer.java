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

package com.alibaba.nacos.config.server.service.consumer;

import com.alibaba.nacos.config.server.configuration.DataSource4ClusterV2;
import com.alibaba.nacos.config.server.enums.ConfigOperationEnum;
import com.alibaba.nacos.config.server.model.log.DBRequest;
import com.alibaba.nacos.config.server.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class DBConsumer extends BaseConsumer<DBRequest> {

    private static final String TRANSACTION_OPEN = "open";
    private static final String TRANSACTION_COMMIT = "commit";
    private static final String TRANSACTION_ROLLBACK = "rollback";

    @Autowired
    private DataSource4ClusterV2 connectionManager;

    @Override
    protected void process(DBRequest dbRequest) {
        final String operation = dbRequest.getOperation();
        final String xid = dbRequest.getXid();

        LogUtil.defaultLog.info("distribute transaction receive request : {}", dbRequest);

        if (StringUtils.equalsIgnoreCase(TRANSACTION_OPEN, operation)) {
            connectionManager.openDistributeTransaction(xid);
            return;
        }

        final DataSource4ClusterV2.ConnectionHolder holder = connectionManager.getHolderByXID(xid);
        if (StringUtils.equalsIgnoreCase(TRANSACTION_COMMIT, operation)) {
            try {
                holder.commit();
            } catch (Exception e) {
                LogUtil.defaultLog.error("commit has error, xid : {}, error : {}", xid, e);
            } finally {
                connectionManager.freed(xid);
            }
            return;
        }
        if (StringUtils.equalsIgnoreCase(TRANSACTION_ROLLBACK, operation)) {
            try {
                holder.rollback();
            } catch (Exception e) {
                LogUtil.defaultLog.error("rollback has error, xid : {}, error : {}", xid, e);
            } finally {
                connectionManager.freed(xid);
            }
        }
    }

    @Override
    public String operation() {
        return ConfigOperationEnum.DB_TRANSACTION_CTRL.getOperation();
    }
}
