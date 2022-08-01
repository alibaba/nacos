/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.base;

import com.alibaba.nacos.config.server.model.Page;

import java.util.List;

/**
 * All Mapper's top interface.
 *
 * @author hyx
 */

public interface BaseMapper<T> {
    
    /**
     * The tableName of mapper, it can be used in MapperManager.
     * @return The tableName of mapper.
     */
    String tableName();
    
    /**
     * To insert a data to the database.
     * @param var1 The corresponding pojo of the data table
     * @return The number of data inserted
     */
    Integer insert(T var1);
    
    /**
     * Update according to the primary key.
     * @param var1 The pojo which you want update
     * @return The number of updates successfully
     */
    Integer update(T var1);
    
    /**
     * Select data by id.
     * @param id The pojo's id.
     * @return The pojo which id is the param.
     */
    T select(Long id);
    
    /**
     * Select all T data.
     * @return The all data from the T table.
     */
    List<T> selectAll();
    
    /**
     * Pagling search data.
     * @param pageNo   Page number (must be greater than 0)
     * @param pageSize Page size (must be greater than 0)
     * @return {@link Page} with {@link T} generation
     */
    Page<T> selectPage(final int pageNo, final int pageSize);
    
    /**
     * Delete the pojo which id is param id.
     * @param id The id of pojo to be deleted
     * @return The number of delete
     */
    Integer delete(Long id);
    
    /**
     * To get the all count of pojo.
     * @return The number of pojo count.
     */
    Integer selectCount();
}
