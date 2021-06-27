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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.naming.utils.NamingHttpUtil;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
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
    
    private final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();
    
    private final long refreshServerListInternal = TimeUnit.SECONDS.toMillis(30);
    
    private final String namespace;
    
    private final AtomicInteger currentIndex = new AtomicInteger();
    
    private final List<String> serverList = new ArrayList<>();
    
    private List<String> serversFromEndpoint = new ArrayList<>();
    
    private ScheduledExecutorService refreshServerListExecutor;
    
    private String endpoint;
    
    private String nacosDomain;
    
    private long lastServerListRefreshTime = 0L;
    
    public ServerListManager(Properties properties) {
        this(properties, null);
    }
    
    public ServerListManager(Properties properties, String namespace) {
        this.namespace = namespace;
        initServerAddr(properties);
        if (!serverList.isEmpty()) {
            currentIndex.set(new Random().nextInt(serverList.size()));
        }
    }
    
    private void initServerAddr(Properties properties) {
        this.endpoint = InitUtils.initEndpoint(properties);
        if (StringUtils.isNotEmpty(endpoint)) {
            this.serversFromEndpoint = getServerListFromEndpoint();
            refreshServerListExecutor = new ScheduledThreadPoolExecutor(1,
                    new NameThreadFactory("com.alibaba.nacos.client.naming.server.list.refresher"));
            refreshServerListExecutor
                    .scheduleWithFixedDelay(this::refreshServerListIfNeed, 0, refreshServerListInternal,
                            TimeUnit.MILLISECONDS);
        } else {
            String serverListFromProps = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
            if (StringUtils.isNotEmpty(serverListFromProps)) {
                this.serverList.addAll(Arrays.asList(serverListFromProps.split(",")));
                if (this.serverList.size() == 1) {
                    this.nacosDomain = serverListFromProps;
                }
            }
        }
    }
    
    private List<String> getServerListFromEndpoint() {
        try {
            String urlString = "http://" + endpoint + "/nacos/serverlist";
            Header header = NamingHttpUtil.builderHeader();
            Query query = StringUtils.isNotBlank(namespace)
                    ? Query.newInstance().addParam("namespace", namespace)
                    : Query.EMPTY;
            HttpRestResult<String> restResult = nacosRestTemplate.get(urlString, header, query, String.class);
            if (!restResult.ok()) {
                throw new IOException(
                        "Error while requesting: " + urlString + "'. Server returned: " + restResult.getCode());
            }
            String content = restResult.getData();
            List<String> list = new ArrayList<String>();
            for (String line : IoUtils.readLines(new StringReader(content))) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }
            return list;
        } catch (Exception e) {
            NAMING_LOGGER.error("[SERVER-LIST] failed to update server list.", e);
        }
        return null;
    }
    
    private void refreshServerListIfNeed() {
        try {
            if (!CollectionUtils.isEmpty(serverList)) {
                NAMING_LOGGER.debug("server list provided by user: " + serverList);
                return;
            }
            if (System.currentTimeMillis() - lastServerListRefreshTime < refreshServerListInternal) {
                return;
            }
            List<String> list = getServerListFromEndpoint();
            if (CollectionUtils.isEmpty(list)) {
                throw new Exception("Can not acquire Nacos list");
            }
            if (null == serversFromEndpoint || !CollectionUtils.isEqualCollection(list, serversFromEndpoint)) {
                NAMING_LOGGER.info("[SERVER-LIST] server list is updated: " + list);
            }
            serversFromEndpoint = list;
            lastServerListRefreshTime = System.currentTimeMillis();
            NotifyCenter.publishEvent(new ServerListChangedEvent());
        } catch (Throwable e) {
            NAMING_LOGGER.warn("failed to update server list", e);
        }
    }
    
    public boolean isDomain() {
        return StringUtils.isNotBlank(nacosDomain);
    }
    
    public String getNacosDomain() {
        return nacosDomain;
    }
    
    @Override
    public List<String> getServerList() {
        return serverList.isEmpty() ? serversFromEndpoint : serverList;
    }
    
    @Override
    public String genNextServer() {
        int index = currentIndex.incrementAndGet() % getServerList().size();
        return getServerList().get(index);
    }
    
    @Override
    public String getCurrentServer() {
        return getServerList().get(currentIndex.get() % getServerList().size());
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
}
