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
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DataSource plugin Mapper sql proxy.
 *
 * @author hyx
 **/
public class MapperProxy implements InvocationHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapperProxy.class);
    
    private Mapper mapper;
    
    private static final Map<String, Mapper> SINGLE_MAPPER_PROXY_MAP = new ConcurrentHashMap<>(16);

    /**
     * Creates a proxy instance for the sub-interfaces of Mapper.class implemented by the given object.
     */
    public <R> R createProxy(Mapper mapper) {
        this.mapper = mapper;
        Class<?> clazz = mapper.getClass();
        Set<Class<?>> interfacesSet = new HashSet<>();
        while (!clazz.equals(Object.class)) {
            interfacesSet.addAll(Arrays.stream(clazz.getInterfaces())
                    .filter(Mapper.class::isAssignableFrom)
                    .collect(Collectors.toSet()));
            clazz = clazz.getSuperclass();
        }
        return (R) Proxy.newProxyInstance(MapperProxy.class.getClassLoader(), interfacesSet.toArray(new Class<?>[interfacesSet.size()]), this);
    }
    
    /**
     * create proxy-mapper single instead of using method createProxy.
     */
    public static <R> R createSingleProxy(Mapper mapper) {
        return (R) SINGLE_MAPPER_PROXY_MAP.computeIfAbsent(mapper.getClass().getSimpleName(), key ->
                new MapperProxy().createProxy(mapper));
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object invoke = method.invoke(mapper, args);
        
        String className = mapper.getClass().getSimpleName();
        String methodName = method.getName();
        String sql;
        if (invoke instanceof MapperResult) {
            sql = ((MapperResult) invoke).getSql();
        } else {
            sql = invoke.toString();
        }
        LOGGER.info("[{}] METHOD : {}, SQL : {}, ARGS : {}", className, methodName, sql, JacksonUtils.toJson(args));
        return invoke;
    }
}
