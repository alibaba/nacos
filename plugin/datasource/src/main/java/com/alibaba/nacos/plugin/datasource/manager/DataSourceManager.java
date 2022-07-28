package com.alibaba.nacos.plugin.datasource.manager;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Data Source Manager.
 *
 * @author hyx
 **/

public class DataSourceManager {
    
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
}
