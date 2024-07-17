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

package com.alibaba.nacos.client.address.base;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.impl.ServerListUpdatedEvent;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTPS_PREFIX;
import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;

public abstract class AbstractServerListManager implements ServerListFactory, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractServerListManager.class);

    protected final String name;

    protected final String tenant;

    protected final String namespace;

    private volatile List<String> serverList = new ArrayList<>();

    private final AtomicInteger index = new AtomicInteger();

    public AbstractServerListManager(NacosClientProperties properties) throws NacosException {
        String namespace = initNamespace(properties);
        this.tenant = namespace;
        this.namespace = namespace;
        this.name = initName(properties);
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

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

    }

    protected abstract String initServerName(NacosClientProperties properties);

    protected List<String> readServerList(Reader reader) throws IOException {
        return IoUtils.readLines(reader)
                .stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    protected String repairServerAddr(String serverAddr) {
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

    protected void updateServerList(List<String> serverList) {
        if (serverList == null || serverList.isEmpty() || serverList.equals(this.serverList)) {
            return;
        }
        serverList = serverList.stream()
                .map(this::repairServerAddr)
                .collect(Collectors.toList());
        if (serverList.equals(this.serverList)) {
            return;
        }
        this.serverList = serverList;
        NotifyCenter.publishEvent(new ServerListUpdatedEvent());
        LOGGER.info("the server list has been updated to {}", serverList);
    }

    protected Runnable createUpdateServerListTask(Supplier<List<String>> supplier) {
        return () -> {
            try {
                updateServerList(supplier.get());
            } catch (Exception e) {
                LOGGER.error("update server list error", e);
            }
        };
    }

    private String initNamespace(NacosClientProperties properties) {
        String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        return StringUtils.isNotBlank(namespace) ? namespace : "";
    }

    private String initName(NacosClientProperties properties) throws NacosException {
        String serverName = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SERVER_NAME),
                () -> initServerName(properties));
        if (StringUtils.isBlank(serverName)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "serverName is blank");
        }
        serverName = serverName + (StringUtils.isNotBlank(namespace) ? "-" + namespace : "");
        return serverName.replaceAll("[/\\\\:]", "-");
    }

    private String getServer(int index) {
        index = index % serverList.size();
        return serverList.get(index);
    }
}
