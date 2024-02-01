/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.persistence.handler;

import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DefaultPageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.DerbyPageHandlerAdapter;
import com.alibaba.nacos.plugin.auth.impl.persistence.handler.support.MysqlPageHandlerAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * pagination factory.
 *
 * @author huangKeMing
 */
public class PageHandlerAdapterFactory {
    
    private static PageHandlerAdapterFactory instance;
    
    private List<PageHandlerAdapter> handlerAdapters;
    
    private Map<String, PageHandlerAdapter> handlerAdapterMap;
    
    public List<PageHandlerAdapter> getHandlerAdapters() {
        return handlerAdapters;
    }
    
    public Map<String, PageHandlerAdapter> getHandlerAdapterMap() {
        return handlerAdapterMap;
    }
    
    private PageHandlerAdapterFactory() {
        handlerAdapters = new ArrayList<>(3);
        handlerAdapterMap = new HashMap<>(3);
        initHandlerAdapters();
    }
    
    public static PageHandlerAdapterFactory getInstance() {
        if (instance == null) {
            synchronized (PageHandlerAdapterFactory.class) {
                if (instance == null) {
                    instance = new PageHandlerAdapterFactory();
                }
            }
        }
        return instance;
    }
    
    /**
     * init handler adapters.
     */
    private void initHandlerAdapters() {
        // MysqlPageHandlerAdapter
        addHandlerAdapter(new MysqlPageHandlerAdapter());
        // DerbyPageHandlerAdapter
        addHandlerAdapter(new DerbyPageHandlerAdapter());
        // DefaultPageHandlerAdapter
        addHandlerAdapter(new DefaultPageHandlerAdapter());
    }
    
    private void addHandlerAdapter(PageHandlerAdapter handlerAdapter) {
        handlerAdapters.add(handlerAdapter);
        handlerAdapterMap.put(handlerAdapter.getClass().getName(), handlerAdapter);
    }
    
}

