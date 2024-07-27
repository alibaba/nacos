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

package com.alibaba.nacos.client.serverlist;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.serverlist.holder.impl.CompositeNacosServerListHolder;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Server list manager.
 *
 * @author xiweng.yy
 */
public class ServerListManager implements ServerListFactory, Closeable {
    private final NacosServerListHolder serverListHolder;

    private final long refreshServerListInternal = TimeUnit.SECONDS.toMillis(30);
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    private volatile List<String> serverList = new ArrayList<>();
    
    private final ScheduledExecutorService refreshServerListExecutor;

    private long lastServerListRefreshTime = 0L;

    private String contentPath = ParamUtil.getDefaultContextPath();

    private String serverListName = ParamUtil.getDefaultNodesPath();

    private String name;

    private final String namespace;

    private String moduleName = "default";

    public ServerListManager(Properties properties) {
        this(NacosClientProperties.PROTOTYPE.derive(properties), null);
    }
    
    public ServerListManager(NacosClientProperties properties, String namespace) {
        this.namespace = namespace;
        this.serverListHolder = new CompositeNacosServerListHolder();
        this.serverList = serverListHolder.initServerList(properties);
        if (getServerList().isEmpty()) {
            throw new NacosLoadException("serverList is empty, please check configuration");
        }
        initParam(properties);
        refreshServerListExecutor = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory(String.format("com.alibaba.nacos.client.%s.server.list.refresher", moduleName)));
        refreshServerListExecutor
                .scheduleWithFixedDelay(this::refreshServerListIfNeed, 500L, refreshServerListInternal,
                        TimeUnit.MILLISECONDS);
        currentIndex.set(new Random().nextInt(getServerList().size()));
    }

    private void initParam(NacosClientProperties properties) {
        String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            this.contentPath = contentPathTmp;
        }
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
        if (!StringUtils.isBlank(serverListNameTmp)) {
            this.serverListName = serverListNameTmp;
        }
        String moduleNameTmp = properties.getProperty(PropertyKeyConst.MODULE_NAME);
        if (!StringUtils.isBlank(moduleNameTmp)) {
            this.moduleName = moduleNameTmp;
        }

        this.name = initServerName();
    }

    private String initServerName() {
        String serverName;
        serverName = moduleName + "-" + String
                .join("_", serverListHolder.getName(), contentPath, serverListName)
                + (StringUtils.isNotBlank(namespace) ? ("_" + StringUtils.trim(namespace)) : "");
        serverName = serverName.replaceAll("\\/", "_");
        serverName = serverName.replaceAll("\\:", "_");

        return serverName;
    }
    
    private void refreshServerListIfNeed() {
        try {
            if (System.currentTimeMillis() - lastServerListRefreshTime < refreshServerListInternal) {
                return;
            }
            List<String> list = serverListHolder.getServerList();
            if (CollectionUtils.isEmpty(list)) {
                throw new Exception("Can not acquire Nacos list");
            }

            if (!CollectionUtils.isEqualCollection(list, serverList)) {
                NAMING_LOGGER.info("[SERVER-LIST] server list is updated: " + list);
                serverList = list;
                lastServerListRefreshTime = System.currentTimeMillis();
                NotifyCenter.publishEvent(new ServerListChangedEvent());
            }
        } catch (Throwable e) {
            NAMING_LOGGER.warn("failed to update server list", e);
        }
    }
    
    @Override
    public List<String> getServerList() {
        return serverList;
    }
    
    @Override
    public String genNextServer() {
        int index = currentIndex.incrementAndGet() % getServerList().size();
        return serverList.get(index);
    }
    
    @Override
    public String getCurrentServer() {
        return serverList.get(currentIndex.get() % serverList.size());
    }

    public void refreshCurrentServerAddr() {
        currentIndex.set(0);
    }

    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        NAMING_LOGGER.info("{} do shutdown begin", className);
        if (null != refreshServerListExecutor) {
            ThreadUtils.shutdownThreadPool(refreshServerListExecutor, NAMING_LOGGER);
        }
        NamingHttpClientManager.getInstance().shutdown();
        NAMING_LOGGER.info("{} do shutdown stop", className);
    }

    public String getName() {
        return this.name;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public String getUrlString() {
        return serverList.toString();
    }

    public String getServerListHolderStrategy() {
        return serverListHolder.getName();
    }

    public String getContentPath() {
        return this.contentPath;
    }

    public boolean hasNext() {
        return currentIndex.get() + 1 < serverList.size();
    }
}
