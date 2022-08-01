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

import com.alibaba.nacos.plugin.datasource.mapper.base.BaseMapper;
import jdk.nashorn.internal.runtime.options.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper Manager.
 *
 * @author hyx
 **/

public class MapperManager implements Manager {
    
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
    }
    
    /**
     * Get the implementation class of BaseMapper.
     * @return The implementation class of BaseMapper.
     */
    private Option<? extends BaseMapper> findMapper(Class<? extends BaseMapper> mapper) {
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
    
    @Override
    public boolean open() {
        return false;
    }
    
    @Override
    public boolean close() {
        return false;
    }
}
