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
package com.alibaba.nacos.client.config.impl;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.EventDispatcher.ServerlistChangeEvent;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.config.utils.IOUtils;
import com.alibaba.nacos.client.config.utils.LogUtils;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerHelper;
import com.alibaba.nacos.client.utils.EnvUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.StringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Serverlist Manager
 *
 * @author Nacos
 */
public class ServerListManager {

    final static public Logger log = LogUtils.logger(ServerListManager.class);

    public ServerListManager() {
        isFixed = false;
        isStarted = false;
        name = DEFAULT_NAME;
    }

    public ServerListManager(List<String> fixed) {
        this(fixed, null);
    }

    public ServerListManager(List<String> fixed, String namespace) {
        isFixed = true;
        isStarted = true;
        List<String> serverAddrs = new ArrayList<String>();
        for (String serverAddr : fixed) {
            String[] serverAddrArr = serverAddr.split(":");
            if (serverAddrArr.length == 1) {
                serverAddrs.add(serverAddrArr[0] + ":" + ParamUtil.getDefaultServerPort());
            } else {
                serverAddrs.add(serverAddr);
            }
        }
        serverUrls = new ArrayList<String>(serverAddrs);
        if (StringUtils.isBlank(namespace)) {
            name = FIXED_NAME + "-" + getFixedNameSuffix(serverAddrs.toArray(new String[serverAddrs.size()]));
        } else {
            this.namespace = namespace;
            name = FIXED_NAME + "-" + getFixedNameSuffix(serverAddrs.toArray(new String[serverAddrs.size()])) + "-"
                + namespace;
        }
    }

    public ServerListManager(String host, int port) {
        isFixed = false;
        isStarted = false;
        name = CUSTOM_NAME + "-" + host + "-" + port;
        addressServerUrl = String.format("http://%s:%d/%s/%s", host, port, contentPath, serverListName);
    }

    public ServerListManager(String endpoint) throws NacosException {
        this(endpoint, null);
    }

    public ServerListManager(String endpoint, String namespace) throws NacosException {
        isFixed = false;
        isStarted = false;
        if (StringUtils.isBlank(endpoint)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
        }
        if (StringUtils.isBlank(namespace)) {
            name = endpoint;
            addressServerUrl = String.format("http://%s:%d/%s/%s", endpoint, endpointPort, contentPath,
                serverListName);
        } else {
            if (StringUtils.isBlank(endpoint)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
            }
            name = endpoint + "-" + namespace;
            this.namespace = namespace;
            this.tenant = namespace;
            addressServerUrl = String.format("http://%s:%d/%s/%s?namespace=%s", endpoint, endpointPort, contentPath,
                serverListName, namespace);
        }
    }

    public ServerListManager(Properties properties) throws NacosException {
        isStarted = false;
        String serverAddrsStr = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        initParam(properties);
        if (StringUtils.isNotEmpty(serverAddrsStr)) {
            isFixed = true;
            List<String> serverAddrs = new ArrayList<String>();
            String[] serverAddrsArr = serverAddrsStr.split(",");
            for (String serverAddr : serverAddrsArr) {
                String[] serverAddrArr = serverAddr.split(":");
                if (serverAddrArr.length == 1) {
                    serverAddrs.add(serverAddrArr[0] + ":" + ParamUtil.getDefaultServerPort());
                } else {
                    serverAddrs.add(serverAddr);
                }
            }
            serverUrls = serverAddrs;
            if (StringUtils.isBlank(namespace)) {
                name = FIXED_NAME + "-" + getFixedNameSuffix(serverUrls.toArray(new String[serverUrls.size()]));
            } else {
                this.namespace = namespace;
                this.tenant = namespace;
                name = FIXED_NAME + "-" + getFixedNameSuffix(serverUrls.toArray(new String[serverUrls.size()])) + "-"
                    + namespace;
            }
        } else {
            if (StringUtils.isBlank(endpoint)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
            }
            isFixed = false;
            if (StringUtils.isBlank(namespace)) {
                name = endpoint;
                addressServerUrl = String.format("http://%s:%d/%s/%s", endpoint, endpointPort, contentPath,
                    serverListName);
            } else {
                this.namespace = namespace;
                this.tenant = namespace;
                name = endpoint + "-" + namespace;
                addressServerUrl = String.format("http://%s:%d/%s/%s?namespace=%s", endpoint, endpointPort,
                    contentPath, serverListName, namespace);
            }
        }
    }

    private void initParam(Properties properties) {
        String endpointTmp = properties.getProperty(PropertyKeyConst.ENDPOINT);
        if (!StringUtils.isBlank(endpointTmp)) {
            endpoint = endpointTmp;
        }
        String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            contentPath = contentPathTmp;
        }
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
        if (!StringUtils.isBlank(serverListNameTmp)) {
            serverListName = serverListNameTmp;
        }
    }

    public synchronized void start() throws NacosException {

        if (isStarted || isFixed) {
            return;
        }

        GetServerListTask getServersTask = new GetServerListTask(addressServerUrl);
        for (int i = 0; i < initServerlistRetryTimes && serverUrls.isEmpty(); ++i) {
            getServersTask.run();
            try {
                this.wait((i + 1) * 100L);
            } catch (Exception e) {
                log.warn("get serverlist fail,url: " + addressServerUrl);
            }
        }

        if (serverUrls.isEmpty()) {
            log.error("NACOS-0008", LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0008", "环境问题",
                "fail to get NACOS-server serverlist! env:" + name + ", not connnect url:" + addressServerUrl));
            log.error(name, "NACOS-XXXX", "[init-serverlist] fail to get NACOS-server serverlist!");
            throw new NacosException(NacosException.SERVER_ERROR,
                "fail to get NACOS-server serverlist! env:" + name + ", not connnect url:" + addressServerUrl);
        }

        TimerService.scheduleWithFixedDelay(getServersTask, 0L, 30L, TimeUnit.SECONDS);
        isStarted = true;
    }

    Iterator<String> iterator() {
        if (serverUrls.isEmpty()) {
            log.error(name, "NACOS-XXXX", "[iterator-serverlist] No server address defined!");
        }
        return new ServerAddressIterator(serverUrls);
    }

    class GetServerListTask implements Runnable {
        final String url;

        GetServerListTask(String url) {
            this.url = url;
        }

        @Override
        public void run() {
            /**
             * get serverlist from nameserver
             */
            try {
                updateIfChanged(getApacheServerList(url, name));
            } catch (Exception e) {
                log.error(name, "NACOS-XXXX", "[update-serverlist] failed to update serverlist from address server!",
                    e);
            }
        }
    }

    private void updateIfChanged(List<String> newList) {
        if (null == newList || newList.isEmpty()) {

            log.warn("NACOS-0001", LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0001", "环境问题",
                "[update-serverlist] current serverlist from address server is empty!!!"));
            log.warn(name, "[update-serverlist] current serverlist from address server is empty!!!");
            return;
        }
        /**
         * no change
         */
        if (newList.equals(serverUrls)) {
            return;
        }
        serverUrls = new ArrayList<String>(newList);
        currentServerAddr = iterator().next();

        EventDispatcher.fireEvent(new ServerlistChangeEvent());
        log.info(name, "[update-serverlist] serverlist updated to {}", serverUrls);
    }

    private List<String> getApacheServerList(String url, String name) {
        try {
            HttpResult httpResult = HttpSimpleClient.httpGet(url, null, null, null, 3000);

            if (HttpURLConnection.HTTP_OK == httpResult.code) {
                if (DEFAULT_NAME.equals(name)) {
                    EnvUtil.setSelfEnv(httpResult.headers);
                }
                List<String> lines = IOUtils.readLines(new StringReader(httpResult.content));
                List<String> result = new ArrayList<String>(lines.size());
                for (String serverAddr : lines) {
                    if (null == serverAddr || serverAddr.trim().isEmpty()) {
                        continue;
                    } else {
                        String[] ipPort = serverAddr.trim().split(":");
                        String ip = ipPort[0].trim();
                        if (ipPort.length == 1) {
                            result.add(ip + ":" + ParamUtil.getDefaultServerPort());
                        } else {
                            result.add(serverAddr);
                        }
                    }
                }
                return result;
            } else {
                log.error(addressServerUrl, "NACOS-XXXX", "[check-serverlist] error. code={}", httpResult.code);
                return null;
            }
        } catch (IOException e) {
            log.error("NACOS-0001", LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0001", "环境问题", e.toString()));
            log.error(addressServerUrl, "NACOS-XXXX", "[check-serverlist] exception. msg={}", e.toString(), e);
            return null;
        }
    }

    String getUrlString() {
        return serverUrls.toString();
    }

    String getFixedNameSuffix(String... serverIps) {
        StringBuilder sb = new StringBuilder();
        String split = "";
        for (String serverIp : serverIps) {
            sb.append(split);
            sb.append(serverIp.replaceAll(":", "_"));
            split = "-";
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ServerManager-" + name + "-" + getUrlString();
    }

    public boolean contain(String ip) {

        return serverUrls.contains(ip);
    }

    public void refreshCurrentServerAddr() {
        currentServerAddr = iterator().next();
    }

    public String getCurrentServerAddr() {
        if (StringUtils.isBlank(currentServerAddr)) {
            currentServerAddr = iterator().next();
        }
        return currentServerAddr;
    }

    public String getContentPath() {
        return contentPath;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getTenant() {
        return tenant;
    }

    /**
     * 不同环境的名称
     */
    private String name;
    private String namespace = "";
    private String tenant = "";
    static public final String DEFAULT_NAME = "default";
    static public final String CUSTOM_NAME = "custom";
    static public final String FIXED_NAME = "fixed";
    private int initServerlistRetryTimes = 5;
    /**
     * 和其他server的连接超时和socket超时
     */
    static final int TIMEOUT = 5000;

    final boolean isFixed;
    boolean isStarted = false;
    private String endpoint;
    private int endpointPort = 8080;
    private String contentPath = ParamUtil.getDefaultContextPath();
    private String serverListName = ParamUtil.getDefaultNodesPath();
    volatile List<String> serverUrls = new ArrayList<String>();

    private volatile String currentServerAddr;

    public String serverPort = ParamUtil.getDefaultServerPort();

    public String addressServerUrl;

}

/**
 * 对地址列表排序，同机房优先。
 */
class ServerAddressIterator implements Iterator<String> {

    static class RandomizedServerAddress implements Comparable<RandomizedServerAddress> {
        static Random random = new Random();

        String serverIp;
        int priority = 0;
        int seed;

        public RandomizedServerAddress(String ip) {
            try {
                this.serverIp = ip;
                /**
                 * change random scope from 32 to Integer.MAX_VALUE to fix load balance issue
                 */
                this.seed = random.nextInt(Integer.MAX_VALUE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        @SuppressFBWarnings("EQ_COMPARETO_USE_OBJECT_EQUALS")
        public int compareTo(RandomizedServerAddress other) {
            if (this.priority != other.priority) {
                return other.priority - this.priority;
            } else {
                return other.seed - this.seed;
            }
        }
    }

    public ServerAddressIterator(List<String> source) {
        sorted = new ArrayList<RandomizedServerAddress>();
        for (String address : source) {
            sorted.add(new RandomizedServerAddress(address));
        }
        Collections.sort(sorted);
        iter = sorted.iterator();
    }

    public boolean hasNext() {
        return iter.hasNext();
    }

    public String next() {
        return iter.next().serverIp;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    final List<RandomizedServerAddress> sorted;
    final Iterator<RandomizedServerAddress> iter;
}

