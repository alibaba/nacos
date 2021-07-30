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

package com.alibaba.nacos.common.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos SPI Service Loader.
 *
 * @author xiweng.yy
 */
public class NacosServiceLoader {
    
    private static final Map<Class<?>, Collection<Class<?>>> SERVICES = new ConcurrentHashMap<Class<?>, Collection<Class<?>>>();
    
    /**
     * Load service.
     *
     * <p>Load service by SPI and cache the classes for reducing cost when load second time.
     *
     * @param service service class
     * @param <T> type of service
     * @return service instances
     */
    public static <T> Collection<T> load(final Class<T> service) {
        if (SERVICES.containsKey(service)) {
            return newServiceInstances(service);
        }
        Collection<T> result = new LinkedHashSet<T>();
        for (T each : ServiceLoader.load(service)) {
            result.add(each);
            cacheServiceClass(service, each);
        }
        return result;
    }
    
    private static <T> void cacheServiceClass(final Class<T> service, final T instance) {
        if (!SERVICES.containsKey(service)) {
            SERVICES.put(service, new LinkedHashSet<Class<?>>());
        }
        SERVICES.get(service).add(instance.getClass());
    }
    
    /**
     * New service instances.
     *
     * @param service service class
     * @param <T> type of service
     * @return service instances
     */
    public static <T> Collection<T> newServiceInstances(final Class<T> service) {
        return SERVICES.containsKey(service) ? newServiceInstancesFromCache(service) : Collections.<T>emptyList();
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Collection<T> newServiceInstancesFromCache(Class<T> service) {
        Collection<T> result = new LinkedHashSet<T>();
        for (Class<?> each : SERVICES.get(service)) {
            result.add((T) newServiceInstance(each));
        }
        return result;
    }
    
    private static Object newServiceInstance(final Class<?> clazz) {
        try {
            return clazz.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new ServiceLoaderException(clazz, e);
        }
    }
}
