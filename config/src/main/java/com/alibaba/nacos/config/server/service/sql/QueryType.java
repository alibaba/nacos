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

import org.springframework.jdbc.core.RowMapper;

/**
 * Associated with the method correspondence of the {@link org.springframework.jdbc.core.JdbcTemplate}.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class QueryType {
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#queryForObject(String, RowMapper)}.
     */
    public static final byte QUERY_ONE_WITH_MAPPER_WITH_ARGS = 0;
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#queryForObject(String, Class)}.
     */
    public static final byte QUERY_ONE_NO_MAPPER_NO_ARGS = 1;
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#queryForObject(String, Object[], Class)}.
     */
    public static final byte QUERY_ONE_NO_MAPPER_WITH_ARGS = 2;
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#query(String, Object[], RowMapper)}.
     */
    public static final byte QUERY_MANY_WITH_MAPPER_WITH_ARGS = 3;
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#queryForList(String, Object...)}.
     */
    public static final byte QUERY_MANY_WITH_LIST_WITH_ARGS = 4;
    
    /**
     * {@link org.springframework.jdbc.core.JdbcTemplate#queryForList(String, Object[], Class)}.
     */
    public static final byte QUERY_MANY_NO_MAPPER_WITH_ARGS = 5;
    
}
