package com.alibaba.nacos.plugin.datasource.mapper;

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
     * @return The var1
     */
    T insert(T var1);
    
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
