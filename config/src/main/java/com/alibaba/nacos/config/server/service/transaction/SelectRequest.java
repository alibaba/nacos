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

package com.alibaba.nacos.config.server.service.transaction;

import java.util.Arrays;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SelectRequest {

    private boolean queryOne = true;
    private boolean useMapper = true;
    private String sql;
    private Object[] args;
    private String className;

    public static SelectRequestBuilder builder() {
        return new SelectRequestBuilder();
    }

    public boolean isQueryOne() {
        return queryOne;
    }

    public void setQueryOne(boolean queryOne) {
        this.queryOne = queryOne;
    }

    public boolean isUseMapper() {
        return useMapper;
    }

    public void setUseMapper(boolean useMapper) {
        this.useMapper = useMapper;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public String toString() {
        return "SelectRequest{" +
                "queryOne=" + queryOne +
                ", sql='" + sql + '\'' +
                ", args=" + Arrays.toString(args) +
                ", mapperName='" + className + '\'' +
                '}';
    }

    public static final class SelectRequestBuilder {
        private boolean queryOne = true;
        private boolean useMapper = true;
        private String sql;
        private Object[] args;
        private String className;

        private SelectRequestBuilder() {
        }

        public SelectRequestBuilder queryOne(boolean queryOne) {
            this.queryOne = queryOne;
            return this;
        }

        public SelectRequestBuilder useMapper(boolean useMapper) {
            this.useMapper = useMapper;
            return this;
        }

        public SelectRequestBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public SelectRequestBuilder args(Object[] args) {
            this.args = args;
            return this;
        }

        public SelectRequestBuilder className(String className) {
            this.className = className;
            return this;
        }

        public SelectRequest build() {
            SelectRequest selectRequest = new SelectRequest();
            selectRequest.setQueryOne(queryOne);
            selectRequest.setUseMapper(useMapper);
            selectRequest.setSql(sql);
            selectRequest.setArgs(args);
            selectRequest.setClassName(className);
            return selectRequest;
        }
    }
}
