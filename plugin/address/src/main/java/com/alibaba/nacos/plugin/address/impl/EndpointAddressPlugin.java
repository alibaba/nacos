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

package com.alibaba.nacos.plugin.address.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.address.common.AddressProperties;
import com.alibaba.nacos.plugin.address.exception.AddressException;
import com.alibaba.nacos.plugin.address.spi.AbstractAddressPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Get nacos server list by address server
 * Date 2022/7/30.
 *
 * @author GuoJiangFu
 */
public class EndpointAddressPlugin extends AbstractAddressPlugin {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointAddressPlugin.class);
    
    private String addressServerUrl;
    
    private int addressServerFailCount = 0;
    
    private String envIdUrl;
    
    private int maxFailCount = 12;
    
    private volatile boolean isAddressServerHealth = true;
    
    private final long refreshServerListInternal = TimeUnit.SECONDS.toMillis(30);
    
    private long lastServerListRefreshTime = 0L;
    
    private final NacosRestTemplate restTemplate = HttpClientBeanHolder.getNacosRestTemplate(LOGGER);
    
    private ScheduledExecutorService refreshServerListExecutor;
    
    private static final String ENV_ID_URL = "envIdUrl";
    
    private static final String HEALTH_CHECK_FAIL_COUNT_PROPERTY = "maxHealthCheckFailCount";
    
    private static final String ADDRESS_SERVER_URL = "addressServerUrl";
    
    private static final String PLUGIN_NAME = "address-server";
    
    @Override
    public void start() throws NacosException {
        initParams();
        if (StringUtils.isEmpty(addressServerUrl)) {
            throw new AddressException("Address server url is empty");
        }
        this.serverList = getServerListFromEndpoint();
        if (CollectionUtils.isEmpty(serverList)) {
            LOGGER.error("[endpoint-address-plugin] fail to get NACOS-server serverlist! addressServerUrl: {}",
                    addressServerUrl);
            throw new AddressException("fail to get NACOS-server serverlist! , not connnect url:" + addressServerUrl);
        }
        refreshServerListExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r);
            t.setName("EndpointAddressPlugin");
            t.setDaemon(true);
            return t;
        });
        refreshServerListExecutor
                .scheduleWithFixedDelay(this::refreshServerListIfNeed, 0, refreshServerListInternal,
                        TimeUnit.MICROSECONDS);
    }
    
    private void initParams() {
        envIdUrl = AddressProperties.getProperty(ENV_ID_URL);
        String maxFailCountTemp = AddressProperties.getProperty(HEALTH_CHECK_FAIL_COUNT_PROPERTY);
        if (maxFailCountTemp != null) {
            maxFailCount = Integer.parseInt(maxFailCountTemp);
        }
        addressServerUrl = AddressProperties.getProperty(ADDRESS_SERVER_URL);
    }
    
    /**
     * Get server list from address server.
     *
     *@return: nacos server list.
     */
    private List<String> getServerListFromEndpoint() {
        
        try {
            RestResult<String> restResult = restTemplate
                    .get(addressServerUrl, Header.EMPTY, Query.EMPTY, String.class);
            if (!restResult.ok()) {
                addressServerFailCount++;
                if (addressServerFailCount >= maxFailCount) {
                    isAddressServerHealth = false;
                }
                throw new IOException(
                        "Error while requesting: " + addressServerUrl + "'. Server returned: " + restResult.getCode());
            }
            isAddressServerHealth = true;
            addressServerFailCount = 0;
            String content = restResult.getData();
            List<String> list = new ArrayList<>();
            for (String line : IoUtils.readLines(new StringReader(content))) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }
            return list;
        } catch (Exception e) {
            LOGGER.error("[SERVER-LIST] failed to update server list.", e);
        }
        return null;
    }
    
    /**
     * Get and update nacos server list from address server regularly.
     */
    private void refreshServerListIfNeed() {
        try {
            if (System.currentTimeMillis() - lastServerListRefreshTime < refreshServerListInternal) {
                return;
            }
            List<String> serverListTemp = getServerListFromEndpoint();
            if (CollectionUtils.isEmpty(serverListTemp)) {
                throw new Exception("Can not acquire Nacos list");
            }
            if (null == serverList || !CollectionUtils.isEqualCollection(serverListTemp, serverList)) {
                LOGGER.info("[SERVER-LIST] server list is updated: " + serverListTemp);
                this.serverList = serverListTemp;
                lastServerListRefreshTime = System.currentTimeMillis();
                this.addressListener.accept(serverList);
            }
        } catch (Throwable e) {
            LOGGER.warn("failed to update server list", e);
        }
    }
    
    @Override
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>(4);
        info.put("addressServerHealth", isAddressServerHealth);
        info.put("addressServerUrl", addressServerUrl);
        info.put("envIdUrl", envIdUrl);
        info.put("addressServerFailCount", addressServerFailCount);
        return info;
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
    @Override
    public void shutdown() {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        if (null != refreshServerListExecutor) {
            ThreadUtils.shutdownThreadPool(refreshServerListExecutor, LOGGER);
        }
        LOGGER.info("{} do shutdown stop", className);
    }
}
