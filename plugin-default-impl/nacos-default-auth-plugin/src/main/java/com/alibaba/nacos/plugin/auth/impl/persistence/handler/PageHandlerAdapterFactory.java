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
import java.util.Collections;
import java.util.function.Consumer;

/**
 * pagination factory.
 *
 * @author huangKeMing
 */
public class PageHandlerAdapterFactory {

    private final List<PageHandlerAdapter> handlerAdapters;

    private final Map<String, PageHandlerAdapter> handlerAdapterMap;

    public List<PageHandlerAdapter> getHandlerAdapters() {
        return handlerAdapters;
    }

    public Map<String, PageHandlerAdapter> getHandlerAdapterMap() {
        return handlerAdapterMap;
    }

    private PageHandlerAdapterFactory() {
        List<PageHandlerAdapter> handlerAdapters = new ArrayList<>(3);
        Map<String, PageHandlerAdapter> handlerAdapterMap = new HashMap<>(3);
        Consumer<PageHandlerAdapter> addHandlerAdapter = handlerAdapter -> {
            handlerAdapters.add(handlerAdapter);
            handlerAdapterMap.put(handlerAdapter.getClass().getName(), handlerAdapter);
        };
        // MysqlPageHandlerAdapter
        addHandlerAdapter.accept(new MysqlPageHandlerAdapter());
        // DerbyPageHandlerAdapter
        addHandlerAdapter.accept(new DerbyPageHandlerAdapter());
        // DefaultPageHandlerAdapter
        addHandlerAdapter.accept(new DefaultPageHandlerAdapter());
        this.handlerAdapters = Collections.unmodifiableList(handlerAdapters);
        this.handlerAdapterMap = Collections.unmodifiableMap(handlerAdapterMap);
    }

    private static final class InstanceHolder {
        static final PageHandlerAdapterFactory INSTANCE = new PageHandlerAdapterFactory();
    }

    public static PageHandlerAdapterFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }

}

