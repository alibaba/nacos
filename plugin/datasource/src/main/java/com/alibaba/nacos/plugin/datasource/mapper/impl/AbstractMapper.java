package com.alibaba.nacos.plugin.datasource.mapper.impl;

import com.alibaba.nacos.plugin.datasource.manager.DataSourceManager;
import com.alibaba.nacos.plugin.datasource.mapper.base.BaseMapper;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An abstract class to contains some methods.
 *
 * @author hyx
 **/

public abstract class AbstractMapper<T> implements BaseMapper<T> {
    
    private JdbcTemplate jdbcTemplate;
    
    public JdbcTemplate getGetJdbcTemplate() {
        return DataSourceManager.instance().getJdbcTemplate();
    }
    
    @Override
    public Integer selectCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName() + ";";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
    
    @Override
    public Integer delete(Long id) {
        String sql = "DELETE FROM " + tableName() + " WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }
    
}
