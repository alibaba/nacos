/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper;

import java.util.List;

/**
 * The parent class of the all mappers.
 *
 * @author hyx
 **/

public interface Mapper {
    
    /**
     * The select method contains columns and where params.
     * @param columns The columns
     * @param where The where params
     * @return The sql of select
     */
    String select(List<String> columns, List<String> where);
    
    /**
     * The insert method contains columns.
     * @param columns The columns
     * @return The sql of insert
     */
    String insert(List<String> columns);
    
    /**
     * The update method contains columns and where params.
     * @param columns The columns
     * @param where The where params
     * @return The sql of update
     */
    String update(List<String> columns, List<String> where);
    
    /**
     * The delete method contains.
     * @param params The params
     * @return The sql of delete
     */
    String delete(List<String> params);
    
    /**
     * The count method contains where params.
     *
     * @param where The where params
     * @return The sql of count
     */
    String count(List<String> where);
    
    /**
     * Get the name of table.
     * @return The name of table.
     */
    String getTableName();
    
    /**
     * Get the datasource name.
     * @return The name of datasource.
     */
    String getDataSource();

    /**
     * Get config_info table primary keys name.
     * The old default value: Statement.RETURN_GENERATED_KEYS
     * The new default value: new String[]{"id"}
     * @return an array of column names indicating the columns
     */
    String[] getPrimaryKeyGeneratedKeys();

    /**
     * Get function by functionName.
     *
     * @param functionName functionName
     * @return function
     */
    String getFunction(String functionName);
}