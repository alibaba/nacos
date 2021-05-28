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
 * Represents a database UPDATE or INSERT or DELETE statement.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class ModifyRequest implements Serializable {
    
    private static final long serialVersionUID = 4548851816596520564L;
    
    private int executeNo;
    
    private String sql;
    
    private Object[] args;
    
    public ModifyRequest() {
    }
    
    public ModifyRequest(String sql) {
        this.sql = sql;
    }
    
    public int getExecuteNo() {
        return executeNo;
    }
    
    public void setExecuteNo(int executeNo) {
        this.executeNo = executeNo;
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
    
    @Override
    public String toString() {
        return "SQL{" + "executeNo=" + executeNo + ", sql='" + sql + '\'' + ", args=" + Arrays.toString(args) + '}';
    }
}
