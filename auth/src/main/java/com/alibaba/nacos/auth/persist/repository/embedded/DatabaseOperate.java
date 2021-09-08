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

package com.alibaba.nacos.auth.persist.repository.embedded;

import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;

/**
 * Derby database operation.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface DatabaseOperate {
    
    /**
     * Data query transaction.
     *
     * @param sql sqk text
     * @param cls target type
     * @param <R> return type
     * @return query result
     */
    <R> R queryOne(String sql, Class<R> cls);
    
    /**
     * Data query transaction.
     *
     * @param sql  sqk text
     * @param args sql parameters
     * @param cls  target type
     * @param <R>  return type
     * @return query result
     */
    <R> R queryOne(String sql, Object[] args, Class<R> cls);
    
    /**
     * Data query transaction.
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param mapper Database query result converter
     * @param <R>    return type
     * @return query result
     */
    <R> R queryOne(String sql, Object[] args, RowMapper<R> mapper);
    
    /**
     * Data query transaction.
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param mapper Database query result converter
     * @param <R>    return type
     * @return query result
     */
    <R> List<R> queryMany(String sql, Object[] args, RowMapper<R> mapper);
    
    /**
     * Data query transaction.
     *
     * @param sql    sqk text
     * @param args   sql parameters
     * @param rClass target type
     * @param <R>    return type
     * @return query result
     */
    <R> List<R> queryMany(String sql, Object[] args, Class<R> rClass);
    
    /**
     * Data query transaction.
     *
     * @param sql  sqk text
     * @param args sql parameters
     * @return query result
     */
    List<Map<String, Object>> queryMany(String sql, Object[] args);
    
}
