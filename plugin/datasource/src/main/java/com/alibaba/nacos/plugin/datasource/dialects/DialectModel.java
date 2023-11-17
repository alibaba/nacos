/*
 * Copyright (c) 2011-2023, baomidou (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.datasource.dialects;

/**
 * The model required for the pagination parameter to be dynamic.
 *
 * @author huangKeMing
 */
public class DialectModel {
    
    private String sqlFetchRows;
    
    private Object[] args;
    
    public DialectModel() {
    }
    
    public DialectModel(String sqlFetchRows, Object[] args) {
        this.sqlFetchRows = sqlFetchRows;
        this.args = args;
    }
    
    public String getSqlFetchRows() {
        return sqlFetchRows;
    }
    
    public Object[] getArgs() {
        return args;
    }
}
