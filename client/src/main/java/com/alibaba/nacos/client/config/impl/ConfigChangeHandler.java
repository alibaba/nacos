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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.config.listener.ConfigChangeParser;
import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * ConfigChangeHandler.
 *
 * @author rushsky518
 */
public class ConfigChangeHandler {
    
    private static class ConfigChangeHandlerHolder {
        
        private static final ConfigChangeHandler INSTANCE = new ConfigChangeHandler();
    }
    
    private ConfigChangeHandler() {
        this.parserList = new LinkedList<ConfigChangeParser>();
        
        Collection<ConfigChangeParser> loader = NacosServiceLoader.load(ConfigChangeParser.class);
        Iterator<ConfigChangeParser> itr = loader.iterator();
        while (itr.hasNext()) {
            this.parserList.add(itr.next());
        }
        
        this.parserList.add(new PropertiesChangeParser());
        this.parserList.add(new YmlChangeParser());
    }
    
    public static ConfigChangeHandler getInstance() {
        return ConfigChangeHandlerHolder.INSTANCE;
    }
    
    /**
     * Parse changed data.
     *
     * @param oldContent old data
     * @param newContent new data
     * @param type       data type
     * @return change data map
     * @throws IOException io exception
     */
    public Map parseChangeData(String oldContent, String newContent, String type) throws IOException {
        for (ConfigChangeParser changeParser : this.parserList) {
            if (changeParser.isResponsibleFor(type)) {
                return changeParser.doParse(oldContent, newContent, type);
            }
        }
        
        return Collections.emptyMap();
    }
    
    private final List<ConfigChangeParser> parserList;
    
}
