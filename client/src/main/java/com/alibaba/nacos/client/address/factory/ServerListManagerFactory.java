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

package com.alibaba.nacos.client.address.factory;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.address.base.Order;
import com.alibaba.nacos.client.address.spi.ServiceLoader;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class ServerListManagerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerListManagerFactory.class);

    private static Comparator<Class<AbstractServerListManager>> defaultComparator = Comparator.comparingInt(ServerListManagerFactory::getOrder);

    public static void setDefaultComparator(Comparator<Class<AbstractServerListManager>> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("comparator is null");
        }
        defaultComparator = comparator;
    }

    public static AbstractServerListManager create(Properties properties) throws NacosException {
        return create(properties, null);
    }

    public static AbstractServerListManager create(NacosClientProperties clientProperties) throws NacosException {
        return create(clientProperties, null);
    }

    public static AbstractServerListManager create(Properties properties,
                                                   Comparator<Class<AbstractServerListManager>> comparator) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        return create(clientProperties, comparator);
    }

    /**
     * Creates and returns an instance of AbstractServerListManager based on the provided NacosClientProperties and comparator.
     *
     * @param clientProperties The Nacos client properties used for initializing the ServerListManager.
     * @param comparator       The comparator for sorting AbstractServerListManager implementations.
     *                         If null, a default comparator is used.
     * @return An instance of AbstractServerListManager that has successfully initialized the server list.
     * @throws NacosException If clientProperties is null, or if any AbstractServerListManager cannot be instantiated,
     *                        or if no server address is available, this exception is thrown.
     */
    public static AbstractServerListManager create(NacosClientProperties clientProperties,
                                                   Comparator<Class<AbstractServerListManager>> comparator) throws NacosException {
        if (clientProperties == null) {
            throw new IllegalArgumentException("clientProperties is null");
        }
        comparator = comparator != null ? comparator : defaultComparator;
        List<Class<AbstractServerListManager>> classes = ServiceLoader.load(AbstractServerListManager.class);
        classes.sort(comparator);
        for (Class<AbstractServerListManager> cls : classes) {
            try {
                String managerName = cls.getSimpleName();
                Constructor<AbstractServerListManager> constructor = cls.getConstructor(NacosClientProperties.class);
                AbstractServerListManager serverListManager = constructor.newInstance(clientProperties);
                if (!serverListManager.getServerList().isEmpty()) {
                    LOGGER.info("successfully init server list from {}", managerName);
                    return serverListManager;
                } else {
                    LOGGER.info("no server list found from {}", managerName);
                    serverListManager.shutdown();
                }
            } catch (Throwable e) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, e);
            }
        }
        throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "no server address is available");
    }

    private static int getOrder(Class<AbstractServerListManager> cls) {
        Order order = cls.getAnnotation(Order.class);
        return order != null ? order.value() : Integer.MAX_VALUE;
    }
}
