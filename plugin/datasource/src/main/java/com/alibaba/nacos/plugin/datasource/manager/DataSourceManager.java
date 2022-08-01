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

package com.alibaba.nacos.plugin.datasource.manager;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Data Source Manager.
 *
 * @author hyx
 **/

public class DataSourceManager implements Manager {
    
    /**
     * The single DataSourceManager instance.
     */
    private static final DataSourceManager INSTANCE = new DataSourceManager();
    
    /**
     * The JdbcTemplate DataSource.
     */
    private JdbcTemplate jdbcTemplate;
    
    /**
     * The Private constructor method.
     */
    private DataSourceManager() {
        init();
    }
    
    /**
     * Load initial.
     */
    public void init() {
    }
    
    /**
     * Get DataSourceManager instance.
     * @return DataSourceManager
     */
    public static DataSourceManager instance() {
        return INSTANCE;
    }
    
    /**
     * Get DataSource(JdbcTemplate).
     * @return JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
    
    /**
     * Get single MapperManager.
     * @return The single MapperManager.
     */
    public static MapperManager getMapperManager() {
        return null;
    }
    
    @Override
    public boolean open() {
        return false;
    }
    
    @Override
    public boolean close() {
        return false;
    }
}
