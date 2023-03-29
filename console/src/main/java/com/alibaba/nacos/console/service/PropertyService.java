/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.service;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.PropertiesConstant;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.console.model.button.AbstractPropertyNode;
import com.alibaba.nacos.console.model.button.SwitchResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.core.listener.StartingApplicationListener.MODE_PROPERTY_KEY_STAND_MODE;

/**
 * property service.
 * @author 985492783@qq.com
 * @date 2023/3/27 15:17
 */
@Service("propertyService")
public class PropertyService {
    
    private final Map<String, PropertyNodeMap> propertyMap = new LinkedHashMap<>(16);
    
    @PostConstruct
    public void init() {
        initBase();
        initDatabase();
    }
    
    private void initBase() {
        PropertyNodeMap dataMap = propertyMap.computeIfAbsent("Basic",
                (k) -> new PropertyNodeMap());
        
        AbstractPropertyNode<String> mode = AbstractPropertyNode.valueOf(MODE_PROPERTY_KEY_STAND_MODE,
                System.getProperty(MODE_PROPERTY_KEY_STAND_MODE), "启动模式");
        
        dataMap.put(mode);
    }
    
    /**
     * init module Database-config.
     */
    private void initDatabase() {
        PropertyNodeMap dataMap = propertyMap.computeIfAbsent("Database",
                (k) -> new PropertyNodeMap());
        DataSourceService dataSource = DynamicDataSource.getInstance().getDataSource();
        AbstractPropertyNode<String> dataPlatform = AbstractPropertyNode.valueOf(PropertiesConstant.DATASOURCE_PLATFORM_PROPERTY,
                dataSource.getDataSourceType(), "数据源");
        AbstractPropertyNode<Boolean> dataLogEnabled = AbstractPropertyNode.valueOf(Constants.NACOS_PLUGIN_DATASOURCE_LOG,
                EnvUtil.getProperty(Constants.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false), "");
        
        dataMap.put(dataPlatform);
        dataMap.put(dataLogEnabled);
    }
    
    public SwitchResult changeProperty(String type, String property, Object value) {
        return propertyMap.get(type).get(property).changeProperty(value);
    }
    
    public Map<String, List<AbstractPropertyNode<?>>> getPropertyMap() {
        Map<String, List<AbstractPropertyNode<?>>> map = new LinkedHashMap<>();
        propertyMap.forEach((k, v) -> {
            map.put(k, new LinkedList<>(v.values()));
        });
        return map;
    }
    
    public static class PropertyNodeMap extends LinkedHashMap<String, AbstractPropertyNode<?>> {
        
        public void put(AbstractPropertyNode<?> node) {
            this.put(node.getProperty(), node);
        }
    }
}
