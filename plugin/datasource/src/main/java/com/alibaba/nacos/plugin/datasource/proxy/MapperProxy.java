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

package com.alibaba.nacos.plugin.datasource.proxy;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * DataSource plugin Mapper sql proxy.
 *
 * @author hyx
 **/
public class MapperProxy implements InvocationHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapperProxy.class);
    
    private Mapper mapper;
    
    private static final Map<String, MapperProxy> SINGLE_MAPPER_PROXY_MAP = new HashMap<>(16);
    
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock(true);
    
    public <R> R createProxy(Mapper mapper) {
        this.mapper = mapper;
        return (R) Proxy.newProxyInstance(MapperProxy.class.getClassLoader(), mapper.getClass().getInterfaces(), this);
    }
    
    /**
     * create proxy-mapper single instead of using method createProxy.
     */
    public static <R> R createSingleProxy(Mapper mapper) {
        String key = mapper.getClass().getSimpleName();
        if (!SINGLE_MAPPER_PROXY_MAP.containsKey(key)) {
            try {
                LOCK.writeLock().lock();
                if (!SINGLE_MAPPER_PROXY_MAP.containsKey(key)) {
                    MapperProxy mapperProxy = new MapperProxy();
                    SINGLE_MAPPER_PROXY_MAP.put(key, mapperProxy.createProxy(mapper));
                }
            } finally {
                LOCK.writeLock().unlock();
            }
        }
        try {
            LOCK.readLock().lock();
            return (R) SINGLE_MAPPER_PROXY_MAP.get(key);
        } finally {
            LOCK.readLock().unlock();
        }
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object invoke = method.invoke(mapper, args);
        
        String className = mapper.getClass().getSimpleName();
        String methodName = method.getName();
        String sql = invoke.toString();
        
        LOGGER.info("[{}] METHOD : {}, SQL : {}, ARGS : {}", className, methodName, sql, JacksonUtils.toJson(args));
        return invoke;
    }
}
