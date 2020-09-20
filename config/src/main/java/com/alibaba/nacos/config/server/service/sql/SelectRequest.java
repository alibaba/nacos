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

package com.alibaba.nacos.config.server.service.sql;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Represents a database SELECT statement.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class SelectRequest implements Serializable {
    
    private static final long serialVersionUID = 2212052574976898602L;
    
    private byte queryType;
    
    private String sql;
    
    private Object[] args;
    
    private String className;
    
    public byte getQueryType() {
        return queryType;
    }
    
    public void setQueryType(byte queryType) {
        this.queryType = queryType;
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
        return "SelectRequest{" + "queryType=" + queryType + ", sql='" + sql + '\'' + ", args=" + Arrays.toString(args)
                + ", className='" + className + '\'' + '}';
    }
    
    public static SelectRequestBuilder builder() {
        return new SelectRequestBuilder();
    }
    
    public static final class SelectRequestBuilder {
        
        private byte queryType;
        
        private String sql;
        
        private Object[] args;
        
        private String className = null;
        
        private SelectRequestBuilder() {
        }
        
        public SelectRequestBuilder queryType(byte queryType) {
            this.queryType = queryType;
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
        
        /**
         * build select request.
         *
         * @return {@link SelectRequest}
         */
        public SelectRequest build() {
            SelectRequest request = new SelectRequest();
            request.setQueryType(queryType);
            request.setSql(sql);
            request.setArgs(args);
            request.setClassName(className);
            return request;
        }
    }
}
