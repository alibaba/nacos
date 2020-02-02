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

package com.alibaba.nacos.config.server.model.log;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DBRequest {

    private String xid;
    private String operation;

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String toString() {
        return "DBRequest{" +
                "xid='" + xid + '\'' +
                ", operation='" + operation + '\'' +
                '}';
    }

    public static DBRequestBuilder builder() {
        return new DBRequestBuilder();
    }

    public static final class DBRequestBuilder {
        private String xid;
        private String operation;

        private DBRequestBuilder() {
        }

        public DBRequestBuilder xid(String xid) {
            this.xid = xid;
            return this;
        }

        public DBRequestBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public DBRequest build() {
            DBRequest dBRequest = new DBRequest();
            dBRequest.setXid(xid);
            dBRequest.setOperation(operation);
            return dBRequest;
        }
    }
}
