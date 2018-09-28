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
package com.alibaba.nacos.client.naming.net;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.client.naming.utils.*;
import com.alibaba.nacos.common.util.UuidUtil;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author dungu.zpf
 */
public class NamingProxy {

    private static final int DEFAULT_SERVER_PORT = 8848;

    private String namespace;

    private String endpoint;

    private String nacosDomain;

    private List<String> serverList;

    private List<String> serversFromEndpoint = new ArrayList<String>();

    private long lastSrvRefTime = 0L;

    private long vipSrvRefInterMillis = TimeUnit.SECONDS.toMillis(30);

    private ScheduledExecutorService executorService;

    public NamingProxy(String namespace, String endpoint, String serverList) {

        this.namespace = namespace;
        this.endpoint = endpoint;
        if (StringUtils.isNotEmpty(serverList)) {
            this.serverList = Arrays.asList(serverList.split(","));
            if (this.serverList.size() == 1) {
                this.nacosDomain = serverList;
            }
        }

        executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.taobao.vipserver.serverlist.updater");
                t.setDaemon(true);
                return t;
            }
        });

        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                refreshSrvIfNeed();
            }
        }, 0, vipSrvRefInterMillis, TimeUnit.MILLISECONDS);

        refreshSrvIfNeed();
    }

    public List<String> getServerListFromEndpoint() {

        try {
            String urlString = "http://" + endpoint + "/vipserver/serverlist";

            List<String> headers = Arrays.asList("Client-Version", UtilAndComs.VERSION,
                    "Accept-Encoding", "gzip,deflate,sdch",
                    "Connection", "Keep-Alive",
                    "RequestId", UuidUtil.generateUuid());

            HttpClient.HttpResult result = HttpClient.httpGet(urlString, headers, null, UtilAndComs.ENCODING);
            if (HttpURLConnection.HTTP_OK != result.code) {
                throw new IOException("Error while requesting: " + urlString + "'. Server returned: "
                        + result.code);
            }

            String content = result.content;
            List<String> list = new ArrayList<String>();
            for (String line : IoUtils.readLines(new StringReader(content))) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }

            return list;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void refreshSrvIfNeed() {
        try {

            if (!CollectionUtils.isEmpty(serverList)) {
                LogUtils.LOG.debug("server list provided by user: " + serverList);
                return;
            }

            if (System.currentTimeMillis() - lastSrvRefTime < vipSrvRefInterMillis) {
                return;
            }

            List<String> list = getServerListFromEndpoint();

            if (list.isEmpty()) {
                throw new Exception("Can not acquire vipserver list");
            }

            if (!CollectionUtils.isEqualCollection(list, serversFromEndpoint)) {
                LogUtils.LOG.info("SERVER-LIST", "server list is updated: " + list);
            }

            serversFromEndpoint = list;
            lastSrvRefTime = System.currentTimeMillis();
        } catch (Throwable e) {
            LogUtils.LOG.warn("failed to update server list", e);
        }
    }

    public void registerService(String serviceName, Instance instance) throws NacosException {

        LogUtils.LOG.info("REGISTER-SERVICE", "registering service " + serviceName + " with instance:" + instance);

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put("tenant", namespace);
        params.put("ip", instance.getIp());
        params.put("port", String.valueOf(instance.getPort()));
        params.put("weight", String.valueOf(instance.getWeight()));
        params.put("healthy", String.valueOf(instance.isHealthy()));
        params.put("metadata", JSON.toJSONString(instance.getMetadata()));
        if (instance.getService() == null) {
            params.put("serviceName", serviceName);
        } else {
            params.put("service", JSON.toJSONString(instance.getService()));
        }
        params.put("cluster", JSON.toJSONString(instance.getCluster()));

        reqAPI(UtilAndComs.NACOS_URL_INSTANCE, params, "PUT");
    }

    public void deregisterService(String serviceName, String ip, int port, String cluster) throws NacosException {

        LogUtils.LOG.info("DEREGISTER-SERVICE", "deregistering service " + serviceName
                + " with instance:" + ip + ":" + port + "@" + cluster);

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put("tenant", namespace);
        params.put("ip", ip);
        params.put("port", String.valueOf(port));
        params.put("serviceName", serviceName);
        params.put("cluster", cluster);

        reqAPI(UtilAndComs.NACOS_URL_INSTANCE, params, "DELETE");
    }

    public String queryList(String serviceName, String clusters, boolean healthyOnly) throws NacosException {

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put("tenant", namespace);
        params.put("serviceName", serviceName);
        params.put("clusters", clusters);
        params.put("healthyOnly", String.valueOf(healthyOnly));

        return reqAPI(UtilAndComs.NACOS_URL_BASE + "/instance/list", params, "GET");
    }

    private String doRegDom(Map<String, String> params) throws Exception {
        String api = UtilAndComs.NACOS_URL_BASE + "/api/regService";
        return reqAPI(api, params);
    }

    public boolean serverHealthy() {

        try {
            reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/hello", new HashMap<String, String>(2));
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    public ListView<String> getServiceList(int pageNo, int pageSize) throws NacosException {

        Map<String, String> params = new HashMap<String, String>(4);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));

        String result = reqAPI(UtilAndComs.NACOS_URL_BASE + "/service/list", params);

        JSONObject json = JSON.parseObject(result);
        ListView<String> listView = new ListView<String>();
        listView.setCount(json.getInteger("count"));
        listView.setData(JSON.parseObject(json.getString("doms"), new TypeReference<List<String>>() {
        }));

        return listView;
    }

    public String callAllServers(String api, Map<String, String> params) throws NacosException {
        String result = "";

        List<String> snapshot = serversFromEndpoint;
        if (!CollectionUtils.isEmpty(serverList)) {
            snapshot = serverList;
        }

        try {
            result = reqAPI(api, params, snapshot);
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "req api:" + api + " failed, servers: " + snapshot, e);
        }

        if (StringUtils.isNotEmpty(result)) {
            return result;
        }

        throw new IllegalStateException("failed to req API:/api/" + api + " after all sites(" + snapshot + ") tried");
    }

    public String reqAPI(String api, Map<String, String> params) throws NacosException {


        List<String> snapshot = serversFromEndpoint;
        if (!CollectionUtils.isEmpty(serverList)) {
            snapshot = serverList;
        }

        return reqAPI(api, params, snapshot);
    }

    public String reqAPI(String api, Map<String, String> params, String method) throws NacosException {

        List<String> snapshot = serversFromEndpoint;
        if (!CollectionUtils.isEmpty(serverList)) {
            snapshot = serverList;
        }

        return reqAPI(api, params, snapshot, method);
    }

    public String callServer(String api, Map<String, String> params, String curServer) throws NacosException {
        return callServer(api, params, curServer, "GET");
    }

    public String callServer(String api, Map<String, String> params, String curServer, String method) throws NacosException {

        List<String> headers = Arrays.asList("Client-Version", UtilAndComs.VERSION,
                "Accept-Encoding", "gzip,deflate,sdch",
                "Connection", "Keep-Alive",
                "RequestId", UuidUtil.generateUuid());

        String url;

        if (!curServer.contains(UtilAndComs.SERVER_ADDR_IP_SPLITER)) {
            curServer = curServer + UtilAndComs.SERVER_ADDR_IP_SPLITER + DEFAULT_SERVER_PORT;
        }

        url = HttpClient.getPrefix() + curServer + api;

        HttpClient.HttpResult result = HttpClient.request(url, headers, params, UtilAndComs.ENCODING, method);

        if (HttpURLConnection.HTTP_OK == result.code) {
            return result.content;
        }

        if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
            return StringUtils.EMPTY;
        }

        LogUtils.LOG.error("CALL-SERVER", "failed to req API:" + HttpClient.getPrefix() + curServer
                + api + ". code:"
                + result.code + " msg: " + result.content);

        throw new NacosException(NacosException.SERVER_ERROR, "failed to req API:" + HttpClient.getPrefix() + curServer
                + api + ". code:"
                + result.code + " msg: " + result.content);
    }

    public String reqAPI(String api, Map<String, String> params, List<String> servers) {
        return reqAPI(api, params, servers, "GET");
    }

    public String reqAPI(String api, Map<String, String> params, List<String> servers, String method) {

        if (CollectionUtils.isEmpty(servers) && StringUtils.isEmpty(nacosDomain)) {
            throw new IllegalArgumentException("no server available");
        }

        if (servers != null && !servers.isEmpty()) {

            Random random = new Random(System.currentTimeMillis());
            int index = random.nextInt(servers.size());

            for (int i = 0; i < servers.size(); i++) {
                String server = servers.get(index);
                try {
                    return callServer(api, params, server, method);
                } catch (Exception e) {
                    LogUtils.LOG.error("NA", "req api:" + api + " failed, server(" + server, e);
                }

                index = (index + 1) % servers.size();
            }

            throw new IllegalStateException("failed to req API:" + api + " after all servers(" + servers + ") tried");
        }


        for (int i = 0; i < UtilAndComs.REQUEST_DOMAIN_RETRY_COUNT; i++) {
            try {
                return callServer(api, params, nacosDomain);
            } catch (Exception e) {
                LogUtils.LOG.error("NA", "req api:" + api + " failed, server(" + nacosDomain, e);
            }
        }

        throw new IllegalStateException("failed to req API:/api/" + api + " after all servers(" + servers + ") tried");

    }

}
