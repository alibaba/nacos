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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.EnvUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Serverlist Manager.
 *
 * @author Nacos
 */
public class ServerListManager implements Closeable {
    
    private static final Logger LOGGER = LogUtils.logger(ServerListManager.class);
    
    private static final String HTTPS = "https://";
    
    private static final String HTTP = "http://";
    
    private final NacosRestTemplate nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
    
    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.ServerListManager");
            t.setDaemon(true);
            return t;
        }
    });
    
    /**
     * The name of the different environment.
     */
    private final String name;
    
    private String namespace = "";
    
    private String tenant = "";
    
    public static final String DEFAULT_NAME = "default";
    
    public static final String CUSTOM_NAME = "custom";
    
    public static final String FIXED_NAME = "fixed";
    
    private final int initServerlistRetryTimes = 5;
    
    /**
     * Connection timeout and socket timeout with other servers.
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
    
    private Iterator<String> iterator;
    
    public String serverPort = ParamUtil.getDefaultServerPort();
    
    public String addressServerUrl;
    
    private String serverAddrsStr;
    
    public ServerListManager() {
        this.isFixed = false;
        this.isStarted = false;
        this.name = DEFAULT_NAME;
    }
    
    public ServerListManager(List<String> fixed) {
        this(fixed, null);
    }
    
    public ServerListManager(List<String> fixed, String namespace) {
        this.isFixed = true;
        this.isStarted = true;
        List<String> serverAddrs = new ArrayList<String>();
        for (String serverAddr : fixed) {
            String[] serverAddrArr = InternetAddressUtil.splitIPPortStr(serverAddr);
            if (serverAddrArr.length == 1) {
                serverAddrs.add(serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
            } else {
                serverAddrs.add(serverAddr);
            }
        }
        this.serverUrls = new ArrayList<String>(serverAddrs);
        if (StringUtils.isBlank(namespace)) {
            this.name = FIXED_NAME + "-" + getFixedNameSuffix(serverAddrs.toArray(new String[serverAddrs.size()]));
        } else {
            this.namespace = namespace;
            this.name = FIXED_NAME + "-" + getFixedNameSuffix(serverAddrs.toArray(new String[serverAddrs.size()])) + "-"
                    + namespace;
        }
    }
    
    public ServerListManager(String host, int port) {
        this.isFixed = false;
        this.isStarted = false;
        this.name = CUSTOM_NAME + "-" + host + "-" + port;
        this.addressServerUrl = String
                .format("http://%s:%d%s/%s", host, port, ContextPathUtil.normalizeContextPath(this.contentPath),
                        this.serverListName);
    }
    
    public ServerListManager(String endpoint) throws NacosException {
        this(endpoint, null);
    }
    
    public ServerListManager(String endpoint, String namespace) throws NacosException {
        this.isFixed = false;
        this.isStarted = false;
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        endpoint = initEndpoint(properties);
        
        if (StringUtils.isBlank(endpoint)) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
        }
        if (StringUtils.isBlank(namespace)) {
            this.name = endpoint;
            this.addressServerUrl = String.format("http://%s:%d%s/%s", endpoint, this.endpointPort,
                    ContextPathUtil.normalizeContextPath(this.contentPath), this.serverListName);
        } else {
            if (StringUtils.isBlank(endpoint)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
            }
            this.name = endpoint + "-" + namespace;
            this.namespace = namespace;
            this.tenant = namespace;
            this.addressServerUrl = String.format("http://%s:%d%s/%s?namespace=%s", endpoint, this.endpointPort,
                    ContextPathUtil.normalizeContextPath(this.contentPath), this.serverListName, namespace);
        }
    }
    
    public ServerListManager(Properties properties) throws NacosException {
        this.isStarted = false;
        this.serverAddrsStr = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        initParam(properties);
        
        if (StringUtils.isNotEmpty(serverAddrsStr)) {
            this.isFixed = true;
            List<String> serverAddrs = new ArrayList<String>();
            StringTokenizer serverAddrsTokens = new StringTokenizer(this.serverAddrsStr, ",;");
            while (serverAddrsTokens.hasMoreTokens()) {
                String serverAddr = serverAddrsTokens.nextToken().trim();
                if (serverAddr.startsWith(HTTPS) || serverAddr.startsWith(HTTP)) {
                    serverAddrs.add(serverAddr);
                } else {
                    String[] serverAddrArr = InternetAddressUtil.splitIPPortStr(serverAddr);
                    if (serverAddrArr.length == 1) {
                        serverAddrs.add(HTTP + serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil
                                .getDefaultServerPort());
                    } else {
                        serverAddrs.add(HTTP + serverAddr);
                    }
                }
            }
            this.serverUrls = serverAddrs;
            if (StringUtils.isBlank(namespace)) {
                this.name = FIXED_NAME + "-" + getFixedNameSuffix(
                        this.serverUrls.toArray(new String[this.serverUrls.size()]));
            } else {
                this.namespace = namespace;
                this.tenant = namespace;
                this.name = FIXED_NAME + "-" + getFixedNameSuffix(
                        this.serverUrls.toArray(new String[this.serverUrls.size()])) + "-" + namespace;
            }
        } else {
            if (StringUtils.isBlank(endpoint)) {
                throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "endpoint is blank");
            }
            this.isFixed = false;
            if (StringUtils.isBlank(namespace)) {
                this.name = endpoint;
                this.addressServerUrl = String.format("http://%s:%d%s/%s", this.endpoint, this.endpointPort,
                        ContextPathUtil.normalizeContextPath(this.contentPath), this.serverListName);
            } else {
                this.namespace = namespace;
                this.tenant = namespace;
                this.name = this.endpoint + "-" + namespace;
                this.addressServerUrl = String
                        .format("http://%s:%d%s/%s?namespace=%s", this.endpoint, this.endpointPort,
                                ContextPathUtil.normalizeContextPath(this.contentPath), this.serverListName, namespace);
            }
        }
    }
    
    private void initParam(Properties properties) {
        this.endpoint = initEndpoint(properties);
        
        String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
        if (!StringUtils.isBlank(contentPathTmp)) {
            this.contentPath = contentPathTmp;
        }
        String serverListNameTmp = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
        if (!StringUtils.isBlank(serverListNameTmp)) {
            this.serverListName = serverListNameTmp;
        }
    }
    
    private String initEndpoint(final Properties properties) {
        
        String endpointPortTmp = TemplateUtils
                .stringEmptyAndThenExecute(System.getenv(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                        new Callable<String>() {
                            @Override
                            public String call() {
                                return properties.getProperty(PropertyKeyConst.ENDPOINT_PORT);
                            }
                        });
        
        if (StringUtils.isNotBlank(endpointPortTmp)) {
            this.endpointPort = Integer.parseInt(endpointPortTmp);
        }
        
        String endpointTmp = properties.getProperty(PropertyKeyConst.ENDPOINT);
        
        // Whether to enable domain name resolution rules
        String isUseEndpointRuleParsing = properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                System.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        if (Boolean.parseBoolean(isUseEndpointRuleParsing)) {
            String endpointUrl = ParamUtil.parsingEndpointRule(endpointTmp);
            if (StringUtils.isNotBlank(endpointUrl)) {
                this.serverAddrsStr = "";
            }
            return endpointUrl;
        }
        
        return StringUtils.isNotBlank(endpointTmp) ? endpointTmp : "";
    }
    
    /**
     * Start.
     *
     * @throws NacosException nacos exception
     */
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
                LOGGER.warn("get serverlist fail,url: {}", addressServerUrl);
            }
        }
        
        if (serverUrls.isEmpty()) {
            LOGGER.error("[init-serverlist] fail to get NACOS-server serverlist! env: {}, url: {}", name,
                    addressServerUrl);
            throw new NacosException(NacosException.SERVER_ERROR,
                    "fail to get NACOS-server serverlist! env:" + name + ", not connnect url:" + addressServerUrl);
        }
        
        // executor schedules the timer task
        this.executorService.scheduleWithFixedDelay(getServersTask, 0L, 30L, TimeUnit.SECONDS);
        isStarted = true;
    }
    
    public List<String> getServerUrls() {
        return serverUrls;
    }
    
    Iterator<String> iterator() {
        if (serverUrls.isEmpty()) {
            LOGGER.error("[{}] [iterator-serverlist] No server address defined!", name);
        }
        return new ServerAddressIterator(serverUrls);
    }
    
    @Override
    public void shutdown() throws NacosException {
        String className = this.getClass().getName();
        LOGGER.info("{} do shutdown begin", className);
        ThreadUtils.shutdownThreadPool(executorService, LOGGER);
        LOGGER.info("{} do shutdown stop", className);
    }
    
    class GetServerListTask implements Runnable {
        
        final String url;
        
        GetServerListTask(String url) {
            this.url = url;
        }
        
        @Override
        public void run() {
            /*
             get serverlist from nameserver
             */
            try {
                updateIfChanged(getApacheServerList(url, name));
            } catch (Exception e) {
                LOGGER.error("[" + name + "][update-serverlist] failed to update serverlist from address server!", e);
            }
        }
    }
    
    private void updateIfChanged(List<String> newList) {
        if (null == newList || newList.isEmpty()) {
            LOGGER.warn("[update-serverlist] current serverlist from address server is empty!!!");
            return;
        }
        
        List<String> newServerAddrList = new ArrayList<String>();
        for (String server : newList) {
            if (server.startsWith(HTTP) || server.startsWith(HTTPS)) {
                newServerAddrList.add(server);
            } else {
                newServerAddrList.add(HTTP + server);
            }
        }
        
        /*
         no change
         */
        if (newServerAddrList.equals(serverUrls)) {
            return;
        }
        serverUrls = new ArrayList<String>(newServerAddrList);
        iterator = iterator();
        currentServerAddr = iterator.next();
        
        // Using unified event processor, NotifyCenter
        NotifyCenter.publishEvent(new ServerlistChangeEvent());
        LOGGER.info("[{}] [update-serverlist] serverlist updated to {}", name, serverUrls);
    }
    
    private List<String> getApacheServerList(String url, String name) {
        try {
            HttpRestResult<String> httpResult = nacosRestTemplate.get(url, Header.EMPTY, Query.EMPTY, String.class);
            
            if (httpResult.ok()) {
                if (DEFAULT_NAME.equals(name)) {
                    EnvUtil.setSelfEnv(httpResult.getHeader().getOriginalResponseHeader());
                }
                List<String> lines = IoUtils.readLines(new StringReader(httpResult.getData()));
                List<String> result = new ArrayList<String>(lines.size());
                for (String serverAddr : lines) {
                    if (StringUtils.isNotBlank(serverAddr)) {
                        String[] ipPort = InternetAddressUtil.splitIPPortStr(serverAddr.trim());
                        String ip = ipPort[0].trim();
                        if (ipPort.length == 1) {
                            result.add(ip + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
                        } else {
                            result.add(serverAddr);
                        }
                    }
                }
                return result;
            } else {
                LOGGER.error("[check-serverlist] error. addressServerUrl: {}, code: {}", addressServerUrl,
                        httpResult.getCode());
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("[check-serverlist] exception. url: " + url, e);
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
            serverIp = serverIp.replaceAll("http(s)?://", "");
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
        iterator = iterator();
        currentServerAddr = iterator.next();
    }
    
    public String getNextServerAddr() {
        if (iterator == null || !iterator.hasNext()) {
            refreshCurrentServerAddr();
            return currentServerAddr;
        }
        try {
            return iterator.next();
        } catch (Exception e) {
            //No nothing.
        }
        refreshCurrentServerAddr();
        return currentServerAddr;
        
    }
    
    public String getCurrentServerAddr() {
        if (StringUtils.isBlank(currentServerAddr)) {
            iterator = iterator();
            currentServerAddr = iterator.next();
        }
        return currentServerAddr;
    }
    
    public void updateCurrentServerAddr(String currentServerAddr) {
        this.currentServerAddr = currentServerAddr;
    }
    
    public Iterator<String> getIterator() {
        return iterator;
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
     * Sort the address list, with the same room priority.
     */
    private static class ServerAddressIterator implements Iterator<String> {
        
        static class RandomizedServerAddress implements Comparable<RandomizedServerAddress> {
            
            static Random random = new Random();
            
            String serverIp;
            
            int priority = 0;
            
            int seed;
            
            public RandomizedServerAddress(String ip) {
                try {
                    this.serverIp = ip;
                    /*
                     change random scope from 32 to Integer.MAX_VALUE to fix load balance issue
                     */
                    this.seed = random.nextInt(Integer.MAX_VALUE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            @Override
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
        
        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }
        
        @Override
        public String next() {
            return iter.next().serverIp;
        }
        
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        final List<RandomizedServerAddress> sorted;
        
        final Iterator<RandomizedServerAddress> iter;
    }
}
