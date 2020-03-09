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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.service.notify.NotifyService;
import com.alibaba.nacos.config.server.service.notify.NotifyService.HttpResult;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.RunningConfigUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;
import static com.alibaba.nacos.core.utils.SystemUtils.*;

/**
 * Serverlist service
 *
 * @author Nacos
 */
@Service
public class ServerListService implements ApplicationListener<WebServerInitializedEvent> {


    private final ServletContext servletContext;

    @Value("${server.port:8848}")
    private int port;

    @Value("${useAddressServer}")
    private Boolean isUseAddressServer = true;

    public ServerListService(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @PostConstruct
    public void init() {
        String envDomainName = System.getenv("address_server_domain");
        if (StringUtils.isBlank(envDomainName)) {
            domainName = System.getProperty("address.server.domain", "jmenv.tbsite.net");
        } else {
            domainName = envDomainName;
        }
        String envAddressPort = System.getenv("address_server_port");
        if (StringUtils.isBlank(envAddressPort)) {
            addressPort = System.getProperty("address.server.port", "8080");
        } else {
            addressPort = envAddressPort;
        }
        addressUrl = System.getProperty("address.server.url",
            servletContext.getContextPath() + "/" + RunningConfigUtils.getClusterName());
        addressServerUrl = "http://" + domainName + ":" + addressPort + addressUrl;
        envIdUrl = "http://" + domainName + ":" + addressPort + "/env";

        defaultLog.info("ServerListService address-server port:" + addressPort);
        defaultLog.info("ADDRESS_SERVER_URL:" + addressServerUrl);
        isHealthCheck = PropertyUtil.isHealthCheck();
        maxFailCount = PropertyUtil.getMaxHealthCheckFailCount();
        fatalLog.warn("useAddressServer:{}", isUseAddressServer);
        GetServerListTask task = new GetServerListTask();
        task.run();
        if (CollectionUtils.isEmpty(serverList)) {
            fatalLog.error("########## cannot get serverlist, so exit.");
            throw new RuntimeException("cannot get serverlist, so exit.");
        } else {
            TimerTaskService.scheduleWithFixedDelay(task, 0L, 5L, TimeUnit.SECONDS);
        }

    }



    public List<String> getServerList() {
        return new ArrayList<String>(serverList);
    }

    public static void setServerList(List<String> serverList) {
        ServerListService.serverList = serverList;
    }

    public static List<String> getServerListUnhealth() {
        return new ArrayList<String>(serverListUnhealth);
    }

    public static Boolean isFirstIp() {
        return serverList.get(0).contains(LOCAL_IP);
    }

    public boolean isHealthCheck() {
        return isHealthCheck;
    }

    /**
     * serverList has changed
     */
    static public class ServerListChangeEvent implements EventDispatcher.Event {
    }

    private void updateIfChanged(List<String> newList) {
        if (CollectionUtils.isEmpty(newList)||newList.equals(serverList)) {
            return;
        }

        boolean isContainSelfIp = newList.stream().anyMatch(ipPortTmp -> ipPortTmp.contains(LOCAL_IP));

        if (isContainSelfIp) {
            isInIpList = true;
        } else {
            isInIpList = false;
            String selfAddr = getFormatServerAddr(LOCAL_IP);
            newList.add(selfAddr);
            fatalLog.error("########## [serverlist] self ip {} not in serverlist {}", selfAddr, newList);
        }

        serverList = new ArrayList<String>(newList);

        if(!serverListUnhealth.isEmpty()){

            List<String> unhealthyRemoved = serverListUnhealth.stream()
                .filter(unhealthyIp -> !newList.contains(unhealthyIp)).collect(Collectors.toList());

            serverListUnhealth.removeAll(unhealthyRemoved);

            List<String> unhealthyCountRemoved = serverIp2unhealthCount.keySet().stream()
                .filter(key -> !newList.contains(key)).collect(Collectors.toList());

            for (String unhealthyCountTmp : unhealthyCountRemoved) {
                serverIp2unhealthCount.remove(unhealthyCountTmp);
            }


        }

        defaultLog.warn("[serverlist] updated to {}", serverList);

        /**
         * 非并发fireEvent
         */
        EventDispatcher.fireEvent(new ServerListChangeEvent());
    }

    /**
     * 保证不返回NULL
     *
     * @return serverlist
     */
    private List<String> getApacheServerList() {
        if (STANDALONE_MODE) {
            List<String> serverIps = new ArrayList<String>();
            serverIps.add(getFormatServerAddr(LOCAL_IP));
            return serverIps;
        }

        // 优先从文件读取服务列表
        try {
            List<String> serverIps = new ArrayList<String>();
            List<String> serverAddrLines = readClusterConf();
            if (!CollectionUtils.isEmpty(serverAddrLines)) {
                for (String serverAddr : serverAddrLines) {
                    if (StringUtils.isNotBlank(serverAddr.trim())) {
                        serverIps.add(getFormatServerAddr(serverAddr));
                    }
                }
            }
            if (serverIps.size() > 0) {
                return serverIps;
            }
        } catch (Exception e) {
            defaultLog.error("nacos-XXXX", "[serverlist] failed to get serverlist from disk!", e);
        }

        if (isUseAddressServer()) {
            try {
                HttpResult result = NotifyService.invokeURL(addressServerUrl, null, null);

                if (HttpServletResponse.SC_OK == result.code) {
                    isAddressServerHealth = true;
                    addressServerFailCount = 0;
                    List<String> lines = IoUtils.readLines(new StringReader(result.content));
                    List<String> ips = new ArrayList<String>(lines.size());
                    for (String serverAddr : lines) {
                        if (StringUtils.isNotBlank(serverAddr)) {
                            ips.add(getFormatServerAddr(serverAddr));
                        }
                    }
                    return ips;
                } else {
                    addressServerFailCount++;
                    if (addressServerFailCount >= maxFailCount) {
                        isAddressServerHealth = false;
                    }
                    defaultLog.error("[serverlist] failed to get serverlist, error code {}", result.code);
                    return Collections.emptyList();
                }
            } catch (IOException e) {
                addressServerFailCount++;
                if (addressServerFailCount >= maxFailCount) {
                    isAddressServerHealth = false;
                }
                defaultLog.error("[serverlist] exception, " + e.toString(), e);
                return Collections.emptyList();
            }

        } else {
            List<String> serverIps = new ArrayList<String>();
            serverIps.add(getFormatServerAddr(LOCAL_IP));
            return serverIps;
        }
    }

    private String getFormatServerAddr(String serverAddr) {
        if (StringUtils.isBlank(serverAddr)) {
            throw new IllegalArgumentException("invalid serverlist");
        }
        String[] ipPort = serverAddr.trim().split(":");
        String ip = ipPort[0].trim();
        if (ipPort.length == 1 && port != 0) {
            return (ip + ":" + port);
        } else {
            return serverAddr;
        }
    }



    class GetServerListTask implements Runnable {
        @Override
        public void run() {
            try {
                updateIfChanged(getApacheServerList());
            } catch (Exception e) {
                defaultLog.error("[serverlist] failed to get serverlist, " + e.toString(), e);
            }
        }
    }

    private void checkServerHealth() {
        long startCheckTime = System.currentTimeMillis();
        for (String serverIp : serverList) {
            // Compatible with old codes,use status.taobao
            String url = "http://" + serverIp + servletContext.getContextPath() + Constants.HEALTH_CONTROLLER_PATH;
            // "/nacos/health";
            HttpGet request = new HttpGet(url);
            httpclient.execute(request, new AsyncCheckServerHealthCallBack(serverIp));
        }
        long endCheckTime = System.currentTimeMillis();
        long cost = endCheckTime - startCheckTime;
        defaultLog.debug("checkServerHealth cost: {}", cost);
    }

    class AsyncCheckServerHealthCallBack implements FutureCallback<HttpResponse> {

        private String serverIp;

        public AsyncCheckServerHealthCallBack(String serverIp) {
            this.serverIp = serverIp;
        }

        @Override
        public void completed(HttpResponse response) {
            if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
                serverListUnhealth.remove(serverIp);
                HttpClientUtils.closeQuietly(response);
            }
        }

        @Override
        public void failed(Exception ex) {
            computeFailCount();
        }

        @Override
        public void cancelled() {
            computeFailCount();
                }

        private void computeFailCount() {
            int failCount = serverIp2unhealthCount.compute(serverIp,(key,oldValue)->oldValue == null?1:oldValue+1);
            if (failCount > maxFailCount) {
                if (!serverListUnhealth.contains(serverIp)) {
                    serverListUnhealth.add(serverIp);
                }
                defaultLog.error("unhealthIp:{}, unhealthCount:{}", serverIp, failCount);
                MetricsMonitor.getUnhealthException().increment();
            }
        }
    }

    class CheckServerHealthTask implements Runnable {

        @Override
        public void run() {
            checkServerHealth();
        }

    }

    private Boolean isUseAddressServer() {
        return isUseAddressServer;
    }

    static class CheckServerThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "com.alibaba.nacos.CheckServerThreadFactory");
            thread.setDaemon(true);
            return thread;
        }
    }

    public static boolean isAddressServerHealth() {
        return isAddressServerHealth;
    }

    public static boolean isInIpList() {
        return isInIpList;
    }

    // ==========================

    /**
     * 和其他server的连接超时和socket超时
     */
    static final int TIMEOUT = 5000;
    private int maxFailCount = 12;
    private static volatile List<String> serverList = new ArrayList<String>();
    private static volatile List<String> serverListUnhealth = Collections.synchronizedList(new ArrayList<String>());;
    private static volatile boolean isAddressServerHealth = true;
    private static volatile int addressServerFailCount = 0;
    private static volatile boolean isInIpList = true;

    /**
     * ip unhealth count
     */
    private static  Map<String, Integer> serverIp2unhealthCount = new ConcurrentHashMap<>();
    private RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(PropertyUtil.getNotifyConnectTimeout())
        .setSocketTimeout(PropertyUtil.getNotifySocketTimeout()).build();

    private CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
        .build();


    public String domainName;
    public String addressPort;
    public String addressUrl;
    public String envIdUrl;
    public String addressServerUrl;
    private boolean isHealthCheck = true;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (port == 0) {
            port = event.getWebServer().getPort();
            List<String> newList = new ArrayList<String>();
            for (String serverAddrTmp : serverList) {
                newList.add(getFormatServerAddr(serverAddrTmp));
            }
            setServerList(new ArrayList<String>(newList));
        }
        httpclient.start();
        CheckServerHealthTask checkServerHealthTask = new CheckServerHealthTask();
        TimerTaskService.scheduleWithFixedDelay(checkServerHealthTask, 0L, 5L, TimeUnit.SECONDS);

    }

}
