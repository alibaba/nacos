/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.address.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ServiceLoader {

    private static final String PREFIX = "META-INF/services/";

    private static final Map<Class<?>, List<Class<?>>> SERVICES = new ConcurrentHashMap<>();

    /**
     * Loads all implementation classes of the specified service interface.
     *
     * @param service The service interface class to query for implementations.
     * @return A list containing all the implementation classes.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<T>> load(Class<T> service) {
        List<Class<?>> services = SERVICES.get(service);
        if (services != null) {
            return services.stream().map(p -> (Class<T>) p).collect(Collectors.toList());
        }
        ClassLoader classLoader = ServiceLoader.class.getClassLoader();
        List<Class<T>> classes = new ArrayList<>();
        String name = PREFIX + service.getName();
        try {
            Enumeration<URL> urls = classLoader.getResources(name);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (
                        InputStream in = url.openStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                ) {
                    String className;
                    while ((className = reader.readLine()) != null) {
                        Class<?> cls = null;
                        try {
                            cls = Class.forName(className, false, classLoader);
                        } catch (ClassNotFoundException e) {
                            error(service, String.format("Provider %s not found", className));
                        }
                        if (cls != service && service.isAssignableFrom(cls)) {
                            SERVICES.computeIfAbsent(service, key -> new ArrayList<>()).add(cls);
                            classes.add((Class<T>) cls);
                        } else {
                            error(service, String.format("Provider %s not a subclass", className));
                        }
                    }
                }
            }
        } catch (IOException e) {
            error(service, "Error locating configuration files");
        }
        return classes;
    }

    /**
     * Loads and initializes a list of service instances of the specified type.
     *
     * @param service The class type of the service interface or abstract class.
     * @param init    The initialization function used to create an instance of a class. If null, a default initialization method is used.
     * @return A list containing all initialized service instances.
     */
    public static <T> List<T> load(Class<T> service, Function<Class<T>, T> init) {
        if (init == null) {
            init = cls -> {
                try {
                    return cls.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Error creating instance from %s", service.getName()), e);
                }
            };
        }
        List<Class<T>> classList = load(service);
        List<T> serviceList = new ArrayList<>();
        for (Class<T> cls : classList) {
            serviceList.add(init.apply(cls));
        }
        return serviceList;
    }

    private static void error(Class<?> service, String message) throws ServiceConfigurationError {
        throw new ServiceConfigurationError(String.format("%s: %s", service, message));
    }
}
