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
package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.common.util.SystemUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.common.util.SystemUtils.STANDALONE_MODE;

/**
 * @author nacos
 */
public class NamingProxy {

    private static volatile List<String> servers;

    private static List<String> serverlistFromConfig;

    private static List<String> lastServers = new ArrayList<String>();

    private static Map<String, List<String>> serverListMap = new ConcurrentHashMap<String, List<String>>();

    private static long lastSrvRefTime = 0L;

    /**
     * records last time that query site info of servers and localhost from armory
     */
    private static long lastSrvSiteRefreshTime = 0L;

    private static long VIP_SRV_REF_INTER_MILLIS = TimeUnit.SECONDS.toMillis(30);

    /**
     * query site info of servers and localhost every 12 hours
     */
    private static final long VIP_SRV_SITE_REF_INTER_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static String jmenv;

    public static String getJmenv() {
        jmenv = SystemUtils.getSystemEnv("nacos_jmenv_domain");

        if (StringUtils.isEmpty(jmenv)) {
            jmenv = System.getProperty("com.alibaba.nacos.naming.jmenv", "jmenv.tbsite.net");
        }

        if (StringUtils.isEmpty(jmenv)) {
            jmenv = "jmenv.tbsite.net";
        }

        return jmenv;
    }

    private static void refreshSrvSiteIfNeed() {
        refreshSrvIfNeed();
        try {
            if (System.currentTimeMillis() - lastSrvSiteRefreshTime > VIP_SRV_SITE_REF_INTER_MILLIS ||
                    !CollectionUtils.isEqualCollection(servers, lastServers)) {
                if (!CollectionUtils.isEqualCollection(servers, lastServers)) {
                    Loggers.SRV_LOG.info("[REFRESH-SERVER-SITE] server list is changed, old: " + lastServers + ", new: " + servers);
                }

                lastServers = servers;
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("fail to query server site: ", e);
        }
    }

    public static List<String> getServers() {
        refreshSrvIfNeed();
        return servers;
    }

    public static void refreshSrvIfNeed() {
        refreshSrvIfNeed(StringUtils.EMPTY);
    }

    public static void refreshSrvIfNeed(String env) {
        try {
            if (System.currentTimeMillis() - lastSrvRefTime < VIP_SRV_REF_INTER_MILLIS) {
                return;
            }

            if (STANDALONE_MODE) {
                servers = new ArrayList<>();
                servers.add(InetAddress.getLocalHost().getHostAddress() + ":" + RunningConfig.getServerPort());
                return;
            }

            List<String> serverlist = refreshServerListFromDisk();

            List<String> list = new ArrayList<String>();
            if (!CollectionUtils.isEmpty(serverlist)) {
                serverlistFromConfig = serverlist;
                if (list.isEmpty()) {
                    Loggers.SRV_LOG.warn("Can not acquire server list");
                }
            }


            if (!StringUtils.isEmpty(env)) {
                serverListMap.put(env, list);
            } else {
                if (!CollectionUtils.isEqualCollection(serverlistFromConfig, list) && CollectionUtils.isNotEmpty(serverlistFromConfig)) {
                    Loggers.SRV_LOG.info("[SERVER-LIST] server list is not the same between AS and config file, use config file.");
                    servers = serverlistFromConfig;
                } else {
                    servers = list;
                }
            }

            if (RunningConfig.getServerPort() > 0) {
                lastSrvRefTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("failed to update server list", e);
            List<String> serverlist = refreshServerListFromDisk();

            if (CollectionUtils.isNotEmpty(serverlist)) {
                serverlistFromConfig = serverlist;
            }

            if (CollectionUtils.isNotEmpty(serverlistFromConfig)) {
                servers = serverlistFromConfig;
            }
        }
    }

    public static List<String> refreshServerListFromDisk() {

        List<String> result = new ArrayList<>();
        // read nacos config if necessary.
        try {
            result = FileUtils.readLines(UtilsAndCommons.getConfFile(), "UTF-8");
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("failed to get config: " + UtilsAndCommons.getConfFilePath(), e);
        }

        Loggers.DEBUG_LOG.debug("REFRESH-SERVER-LIST1", result);

        //use system env
        if (CollectionUtils.isEmpty(result)) {
            result = SystemUtils.getIPsBySystemEnv(UtilsAndCommons.SELF_SERVICE_CLUSTER_ENV);
            Loggers.DEBUG_LOG.debug("REFRESH-SERVER-LIST4: " + result);
        }

        Loggers.DEBUG_LOG.debug("REFRESH-SERVER-LIST2" + result);

        if (!result.isEmpty() && !result.get(0).contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
            for (int i = 0; i < result.size(); i++) {
                result.set(i, result.get(i) + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort());
            }
        }

        return result;
    }

    /**
     * This method will classify all servers as two kinds of servers: servers in the same site with local host and others
     *
     * @return servers
     */
    public static ConcurrentHashMap<String, List<String>> getSameSiteServers() {
        refreshSrvSiteIfNeed();
        List<String> snapshot = servers;
        ConcurrentHashMap<String, List<String>> servers = new ConcurrentHashMap<>(2);
        servers.put("sameSite", snapshot);
        servers.put("otherSite", new ArrayList<String>());

        Loggers.SRV_LOG.debug("sameSiteServers:" + servers.toString());
        return servers;
    }

    public static String reqAPI(String api, Map<String, String> params, String curServer, boolean isPost) throws Exception {
        try {
            List<String> headers = Arrays.asList("Client-Version", UtilsAndCommons.SERVER_VERSION,
                    "Accept-Encoding", "gzip,deflate,sdch",
                    "Connection", "Keep-Alive",
                    "Content-Encoding", "gzip");


            HttpClient.HttpResult result;

            if (!curServer.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                curServer = curServer + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            if (isPost) {
                result = HttpClient.httpPost("http://" + curServer + RunningConfig.getContextPath()
                        + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            } else {
                result = HttpClient.httpGet("http://" + curServer + RunningConfig.getContextPath()
                        + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api, headers, params);
            }

            if (HttpURLConnection.HTTP_OK == result.code) {
                return result.content;
            }

            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
                return StringUtils.EMPTY;
            }

            throw new IOException("failed to req API:" + "http://" + curServer
                    + RunningConfig.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api + ". code:"
                    + result.code + " msg: " + result.content);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("NamingProxy", e);
        }
        return StringUtils.EMPTY;
    }

    public static String getEnv() {
        try {

            String urlString = "http://" + getJmenv() + ":8080" + "/env";

            List<String> headers = Arrays.asList("Client-Version", UtilsAndCommons.SERVER_VERSION,
                    "Accept-Encoding", "gzip,deflate,sdch",
                    "Connection", "Keep-Alive");

            HttpClient.HttpResult result = HttpClient.httpGet(urlString, headers, null);
            if (HttpURLConnection.HTTP_OK != result.code) {
                throw new IOException("Error while requesting: " + urlString + "'. Server returned: "
                        + result.code);
            }

            String content = result.content;

            return content.trim();
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("failed to get env", e);
        }

        return "sh";
    }
}
