package com.alibaba.nacos.plugin.datasource.manager;

import com.alibaba.nacos.plugin.datasource.mapper.BaseMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper Manager.
 *
 * @author hyx
 **/

public class MapperManager {
    
    /**
     * The MapperManager instance.
     */
    private static final MapperManager INSTANCE = new MapperManager();
    
    /**
     * The mapping of strings to BaseMapper.
     */
    private static final Map<String, BaseMapper> MAPPER_SPI_MAP = new HashMap<>();
    
    /**
     * The Private constructor method.
     */
    private MapperManager() {
        init();
    }
    
    /**
     * The instance of MapperManager.
     * @return A single MapperManage instance
     */
    public MapperManager instance() {
        return INSTANCE;
    }
    
    /**
     * Load initial.
     */
    private void init() {
        return;
    }
    
    /**
     * Get the implementation class of BaseMapper.
     * @return The implementation class of BaseMapper.
     */
    private BaseMapper getMapper(Class<? extends BaseMapper> mapper) {
        return null;
    }
    
    /**
     * Add mapper to the manager.
     * @param mapper The implementation class of BaseMapper
     * @return The implementation class of BaseMapper
     */
    private BaseMapper addMapper(BaseMapper mapper) {
        return null;
    }
}
