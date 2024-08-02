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

package com.alibaba.nacos.client.address.manager;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.address.common.ServerListChangedEvent;
import com.alibaba.nacos.client.address.provider.ServerListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

/**
 * Abstract Server List Manager.
 *
 * @author misakacoder
 */
public abstract class AbstractServerListManager implements ServerListFactory, Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractServerListManager.class);
    
    private volatile List<String> serverList = new ArrayList<>();
    
    private final AtomicInteger index = new AtomicInteger();
    
    protected ServerListProvider serverListProvider;
    
    private ScheduledExecutorService scheduledExecutorService;
    
    /**
     * Get the type of the module.
     *
     * @return the type of the module
     */
    public abstract ModuleType getModuleType();
    
    @Override
    public String getNextServer() {
        return getServer(index.incrementAndGet());
    }
    
    @Override
    public String getCurrentServer() {
        return getServer(index.get());
    }
    
    @Override
    public List<String> getServerList() {
        return serverList;
    }
    
    @Override
    public void shutdown() throws NacosException {
        if (serverListProvider != null) {
            serverListProvider.shutdown();
        }
        if (scheduledExecutorService != null) {
            ThreadUtils.shutdownThreadPool(scheduledExecutorService, LOGGER);
        }
    }
    
    protected void initServerList(NacosClientProperties properties, String namespace) throws NacosException {
        Collection<ServerListProvider> providers = NacosServiceLoader.load(ServerListProvider.class);
        providers = providers.stream().sorted(Comparator.comparingInt(ServerListProvider::getOrder))
                .collect(Collectors.toList());
        for (ServerListProvider provider : providers) {
            if (provider.isValid()) {
                String providerName = provider.getClass().getSimpleName();
                provider.startup(properties, namespace, getModuleType());
                List<String> serverList = provider.getServerList();
                if (CollectionUtils.isNotEmpty(serverList)) {
                    updateServerList(serverList);
                    if (provider.supportRefresh()) {
                        scheduledExecutorService = new ScheduledThreadPoolExecutor(1,
                                new NameThreadFactory(this.getClass().getName()));
                        Runnable refreshTask = createUpdateServerListTask(provider::getServerList);
                        scheduledExecutorService.scheduleWithFixedDelay(refreshTask, 0L, 30L, TimeUnit.SECONDS);
                    }
                    serverListProvider = provider;
                    LOGGER.info("successfully init server list from {}", providerName);
                    break;
                } else {
                    throw new NacosException(NacosException.CLIENT_INVALID_PARAM,
                            String.format("the provider '%s' is valid, but the server list is empty", providerName));
                }
            } else {
                provider.shutdown();
            }
        }
        if (CollectionUtils.isEmpty(serverList)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "no server available");
        }
    }
    
    private String repairServerAddr(String serverAddr) {
        serverAddr = serverAddr.trim();
        String prefix = HTTP_PREFIX;
        if (serverAddr.startsWith(HTTP_PREFIX) || serverAddr.startsWith(HTTPS_PREFIX)) {
            int index = serverAddr.indexOf("//") + 2;
            prefix = serverAddr.substring(0, index);
            serverAddr = serverAddr.substring(index);
        }
        String[] ipPort = InternetAddressUtil.splitIPPortStr(serverAddr);
        String ip = ipPort[0].trim();
        if (ipPort.length == 1) {
            serverAddr = ip + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort();
        }
        serverAddr = prefix + serverAddr;
        return serverAddr;
    }
    
    private void updateServerList(List<String> serverList) {
        if (serverList == null) {
            return;
        }
        serverList = serverList.stream().filter(StringUtils::isNotBlank).map(this::repairServerAddr)
                .collect(Collectors.toList());
        if (serverList.isEmpty()) {
            return;
        }
        serverList.sort(String::compareTo);
        if (serverList.equals(this.serverList)) {
            return;
        }
        this.serverList = serverList;
        NotifyCenter.publishEvent(new ServerListChangedEvent());
        LOGGER.info("the server list has been updated to {}", serverList);
    }
    
    private Runnable createUpdateServerListTask(Supplier<List<String>> supplier) {
        return () -> {
            try {
                updateServerList(supplier.get());
            } catch (Exception e) {
                LOGGER.error("update server list error", e);
            }
        };
    }
    
    private String getServer(int index) {
        index = index % serverList.size();
        return serverList.get(index);
    }
    
    protected interface Supplier<T> {
        
        /**
         * Gets a result.
         *
         * @return a result
         * @throws Exception Exception
         */
        T get() throws Exception;
    }
}
