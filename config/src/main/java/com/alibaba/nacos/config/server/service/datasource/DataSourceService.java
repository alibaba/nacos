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

package com.alibaba.nacos.config.server.service.datasource;

import java.io.IOException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Datasource interface.
 *
 * @author Nacos
 */
public interface DataSourceService {
    
    /**
     * Initialize the relevant resource information.
     *
     * @throws Exception exception.
     */
    void init() throws Exception;
    
    /**
     * Reload.
     *
     * @throws IOException exception.
     */
    void reload() throws IOException;
    
    /**
     * Check master db.
     *
     * @return is master.
     */
    boolean checkMasterWritable();
    
    /**
     * Get jdbc template.
     *
     * @return JdbcTemplate.
     */
    JdbcTemplate getJdbcTemplate();
    
    /**
     * Get transaction template.
     *
     * @return TransactionTemplate.
     */
    TransactionTemplate getTransactionTemplate();
    
    /**
     * Get current db url.
     *
     * @return database url
     */
    String getCurrentDbUrl();
    
    /**
     * Get heath information.
     *
     * @return heath info.
     */
    String getHealth();
    
}
