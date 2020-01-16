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
import com.alibaba.nacos.config.server.utils.LogUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private Environment env;

    @Autowired
    private ServletContext servletContext;

    private int port;

    @PostConstruct
    public void init() {
        serverPort = System.getProperty("nacos.server.port", "8848");
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

        defaultLog.info("ServerListService address-server port:" + serverPort);
        defaultLog.info("ADDRESS_SERVER_URL:" + addressServerUrl);
        isHealthCheck = PropertyUtil.isHealthCheck();
        maxFailCount = PropertyUtil.getMaxHealthCheckFailCount();

        try {
            String val = null;
            val = env.getProperty("useAddressServer");
            if (val != null && FALSE_STR.equals(val)) {
                isUseAddressServer = false;
            }
            fatalLog.warn("useAddressServer:{}", isUseAddressServer);
        } catch (Exception e) {
            fatalLog.error("read application.properties wrong", e);
        }
        GetServerListTask task = new GetServerListTask();
        task.run();
        if (null == serverList || serverList.isEmpty()) {
            fatalLog.error("########## cannot get serverlist, so exit.");
            throw new RuntimeException("cannot get serverlist, so exit.");
        } else {
            TimerTaskService.scheduleWithFixedDelay(task, 0L, 5L, TimeUnit.SECONDS);
        }
        httpclient.start();
        CheckServerHealthTask checkServerHealthTask = new CheckServerHealthTask();
        TimerTaskService.scheduleWithFixedDelay(checkServerHealthTask, 0L, 5L, TimeUnit.SECONDS);
    }

    public String getEnvId() {
        String envId = "";
        int i = 0;
        do {
            envId = getEnvIdHttp();
            if (StringUtils.isBlank(envId)) {
                i++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    LogUtil.defaultLog.error("sleep interrupt");
                }
            }
        } while (StringUtils.isBlank(envId) && i < 5);

        if (!StringUtils.isBlank(envId)) {
        } else {
            LogUtil.defaultLog.error("envId is blank");
        }
        return envId;
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
    static public class ServerlistChangeEvent implements EventDispatcher.Event {
    }

    private void updateIfChanged(List<String> newList) {
        if (newList.isEmpty()) {
            return;
        }

        boolean isContainSelfIp = false;
        for (String ipPortTmp : newList) {
            if (ipPortTmp.contains(LOCAL_IP)) {
                isContainSelfIp = true;
                break;
            }
        }

        if (isContainSelfIp) {
            isInIpList = true;
        } else {
            isInIpList = false;
            String selfAddr = getFormatServerAddr(LOCAL_IP);
            newList.add(selfAddr);
            fatalLog.error("########## [serverlist] self ip {} not in serverlist {}", selfAddr, newList);
        }

        if (newList.equals(serverList)) {
            return;
        }

        serverList = new ArrayList<String>(newList);

        List<String> unhealthRemoved = new ArrayList<String>();
        for (String unhealthIp : serverListUnhealth) {
            if (!newList.contains(unhealthIp)) {
                unhealthRemoved.add(unhealthIp);
            }
        }

        serverListUnhealth.removeAll(unhealthRemoved);

        List<String> unhealthCountRemoved = new ArrayList<String>();
        for (Map.Entry<String, Integer> ip2UnhealthCountTmp : serverIp2unhealthCount.entrySet()) {
            if (!newList.contains(ip2UnhealthCountTmp.getKey())) {
                unhealthCountRemoved.add(ip2UnhealthCountTmp.getKey());
            }
        }

        for (String unhealthCountTmp : unhealthCountRemoved) {
            serverIp2unhealthCount.remove(unhealthCountTmp);
        }

        defaultLog.warn("[serverlist] updated to {}", serverList);

        /**
         * 非并发fireEvent
         */
        EventDispatcher.fireEvent(new ServerlistChangeEvent());
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
                    addressServerFailCcount = 0;
                    List<String> lines = IoUtils.readLines(new StringReader(result.content));
                    List<String> ips = new ArrayList<String>(lines.size());
                    for (String serverAddr : lines) {
                        if (StringUtils.isNotBlank(serverAddr)) {
                            ips.add(getFormatServerAddr(serverAddr));
                        }
                    }
                    return ips;
                } else {
                    addressServerFailCcount++;
                    if (addressServerFailCcount >= maxFailCount) {
                        isAddressServerHealth = false;
                    }
                    defaultLog.error("[serverlist] failed to get serverlist, error code {}", result.code);
                    return Collections.emptyList();
                }
            } catch (IOException e) {
                addressServerFailCcount++;
                if (addressServerFailCcount >= maxFailCount) {
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

    private String getEnvIdHttp() {
        try {
            // "http://jmenv.tbsite.net:8080/env";
            HttpResult result = NotifyService.invokeURL(envIdUrl, null, null);

            if (HttpServletResponse.SC_OK == result.code) {
                return result.content.trim();
            } else {
                defaultLog.error("[envId] failed to get envId, error code {}", result.code);
                return "";
            }
        } catch (IOException e) {
            defaultLog.error("[envId] exception, " + e.toString(), e);
            return "";
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
            httpclient.execute(request, new AyscCheckServerHealthCallBack(serverIp));
        }
        long endCheckTime = System.currentTimeMillis();
        long cost = endCheckTime - startCheckTime;
        defaultLog.debug("checkServerHealth cost: {}", cost);
    }

    class AyscCheckServerHealthCallBack implements FutureCallback<HttpResponse> {

        private String serverIp;

        public AyscCheckServerHealthCallBack(String serverIp) {
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
            int failCount = serverIp2unhealthCount.compute(serverIp,(key,oldValue)->{
                    if(oldValue == null){
                        return 1;
                    }
                    return oldValue+1;
            });
            if (failCount > maxFailCount) {
                if (!serverListUnhealth.contains(serverIp)) {
                    serverListUnhealth.add(serverIp);
                }
                defaultLog.error("unhealthIp:{}, unhealthCount:{}", serverIp, failCount);
                MetricsMonitor.getUnhealthException().increment();
            }
        }

        @Override
        public void cancelled() {
            int failCount = serverIp2unhealthCount.compute(serverIp,(key,oldValue)->{
                if(oldValue == null){
                    return 1;
                }
                return oldValue+1;
            });
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
    private static volatile List<String> serverListUnhealth = new ArrayList<String>();
    private static volatile boolean isAddressServerHealth = true;
    private static volatile int addressServerFailCcount = 0;
    private static volatile boolean isInIpList = true;

    /**
     * ip unhealth count
     */
    private static volatile Map<String, Integer> serverIp2unhealthCount = new HashMap<String, Integer>();
    private RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(PropertyUtil.getNotifyConnectTimeout())
        .setSocketTimeout(PropertyUtil.getNotifySocketTimeout()).build();

    private CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig)
        .build();

    /**
     * server之间通信的端口
     */
    public String serverPort;
    public String domainName;
    public String addressPort;
    public String addressUrl;
    public String envIdUrl;
    public String addressServerUrl;
    private Boolean isUseAddressServer = true;
    private boolean isHealthCheck = true;
    private final static String FALSE_STR = "false";

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
    }

}
