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
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.client.config.impl.SpasAdapter;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.utils.*;
import com.alibaba.nacos.client.utils.StringUtils;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.util.HttpMethod;
import com.alibaba.nacos.common.util.UuidUtils;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author nkorange
 */
public class NamingProxy {

    private static final int DEFAULT_SERVER_PORT = 8848;

    private int serverPort = DEFAULT_SERVER_PORT;

    private String namespaceId;

    private String endpoint;

    private String nacosDomain;

    private List<String> serverList;

    private List<String> serversFromEndpoint = new ArrayList<String>();

    private long lastSrvRefTime = 0L;

    private long vipSrvRefInterMillis = TimeUnit.SECONDS.toMillis(30);

    private Properties properties;

    public NamingProxy(String namespaceId, String endpoint, String serverList) {

        this.namespaceId = namespaceId;
        this.endpoint = endpoint;
        if (StringUtils.isNotEmpty(serverList)) {
            this.serverList = Arrays.asList(serverList.split(","));
            if (this.serverList.size() == 1) {
                this.nacosDomain = serverList;
            }
        }

        initRefreshSrvIfNeed();
    }

    private void initRefreshSrvIfNeed() {
        if (StringUtils.isEmpty(endpoint)) {
            return;
        }

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.naming.serverlist.updater");
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
            String urlString = "http://" + endpoint + "/nacos/serverlist";
            List<String> headers = builderHeaders();

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
                NAMING_LOGGER.debug("server list provided by user: " + serverList);
                return;
            }

            if (System.currentTimeMillis() - lastSrvRefTime < vipSrvRefInterMillis) {
                return;
            }

            List<String> list = getServerListFromEndpoint();

            if (CollectionUtils.isEmpty(list)) {
                throw new Exception("Can not acquire Nacos list");
            }

            if (!CollectionUtils.isEqualCollection(list, serversFromEndpoint)) {
                NAMING_LOGGER.info("[SERVER-LIST] server list is updated: " + list);
            }

            serversFromEndpoint = list;
            lastSrvRefTime = System.currentTimeMillis();
        } catch (Throwable e) {
            NAMING_LOGGER.warn("failed to update server list", e);
        }
    }

    public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {

        NAMING_LOGGER.info("[REGISTER-SERVICE] {} registering service {} with instance: {}",
            namespaceId, serviceName, instance);

        final Map<String, String> params = new HashMap<String, String>(9);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put("ip", instance.getIp());
        params.put("port", String.valueOf(instance.getPort()));
        params.put("weight", String.valueOf(instance.getWeight()));
        params.put("enable", String.valueOf(instance.isEnabled()));
        params.put("healthy", String.valueOf(instance.isHealthy()));
        params.put("ephemeral", String.valueOf(instance.isEphemeral()));
        params.put("metadata", JSON.toJSONString(instance.getMetadata()));

        reqAPI(UtilAndComs.NACOS_URL_INSTANCE, params, HttpMethod.POST);

    }

    public void deregisterService(String serviceName, Instance instance) throws NacosException {

        NAMING_LOGGER.info("[DEREGISTER-SERVICE] {} deregistering service {} with instance: {}",
            namespaceId, serviceName, instance);

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put("ip", instance.getIp());
        params.put("port", String.valueOf(instance.getPort()));
        params.put("ephemeral", String.valueOf(instance.isEphemeral()));

        reqAPI(UtilAndComs.NACOS_URL_INSTANCE, params, HttpMethod.DELETE);
    }

    public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} update service {} with instance: {}",
            namespaceId, serviceName, instance);

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);
        params.put(CommonParams.CLUSTER_NAME, instance.getClusterName());
        params.put("ip", instance.getIp());
        params.put("port", String.valueOf(instance.getPort()));
        params.put("weight", String.valueOf(instance.getWeight()));
        params.put("enabled", String.valueOf(instance.isEnabled()));
        params.put("ephemeral", String.valueOf(instance.isEphemeral()));
        params.put("metadata", JSON.toJSONString(instance.getMetadata()));

        reqAPI(UtilAndComs.NACOS_URL_INSTANCE, params, HttpMethod.PUT);
    }

    public Service queryService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[QUERY-SERVICE] {} query service : {}, {}",
            namespaceId, serviceName, groupName);

        final Map<String, String> params = new HashMap<String, String>(3);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);

        String result = reqAPI(UtilAndComs.NACOS_URL_SERVICE, params, HttpMethod.GET);
        JSONObject jsonObject = JSON.parseObject(result);
        return jsonObject.toJavaObject(Service.class);
    }

    public void createService(Service service, AbstractSelector selector) throws NacosException {

        NAMING_LOGGER.info("[CREATE-SERVICE] {} creating service : {}",
            namespaceId, service);

        final Map<String, String> params = new HashMap<String, String>(6);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, service.getName());
        params.put(CommonParams.GROUP_NAME, service.getGroupName());
        params.put("protectThreshold", String.valueOf(service.getProtectThreshold()));
        params.put("metadata", JSON.toJSONString(service.getMetadata()));
        params.put("selector", JSON.toJSONString(selector));

        reqAPI(UtilAndComs.NACOS_URL_SERVICE, params, HttpMethod.POST);

    }

    public boolean deleteService(String serviceName, String groupName) throws NacosException {
        NAMING_LOGGER.info("[DELETE-SERVICE] {} deleting service : {} with groupName : {}",
            namespaceId, serviceName, groupName);

        final Map<String, String> params = new HashMap<String, String>(6);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put(CommonParams.GROUP_NAME, groupName);

        String result = reqAPI(UtilAndComs.NACOS_URL_SERVICE, params, HttpMethod.DELETE);
        return "ok".equals(result);
    }

    public void updateService(Service service, AbstractSelector selector) throws NacosException {
        NAMING_LOGGER.info("[UPDATE-SERVICE] {} updating service : {}",
            namespaceId, service);

        final Map<String, String> params = new HashMap<String, String>(6);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, service.getName());
        params.put(CommonParams.GROUP_NAME, service.getGroupName());
        params.put("protectThreshold", String.valueOf(service.getProtectThreshold()));
        params.put("metadata", JSON.toJSONString(service.getMetadata()));
        params.put("selector", JSON.toJSONString(selector));

        reqAPI(UtilAndComs.NACOS_URL_SERVICE, params, HttpMethod.PUT);
    }

    public String queryList(String serviceName, String clusters, int udpPort, boolean healthyOnly)
        throws NacosException {

        final Map<String, String> params = new HashMap<String, String>(8);
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.SERVICE_NAME, serviceName);
        params.put("clusters", clusters);
        params.put("udpPort", String.valueOf(udpPort));
        params.put("clientIP", NetUtils.localIP());
        params.put("healthyOnly", String.valueOf(healthyOnly));

        return reqAPI(UtilAndComs.NACOS_URL_BASE + "/instance/list", params, HttpMethod.GET);
    }

    public long sendBeat(BeatInfo beatInfo) {
        try {
            if (NAMING_LOGGER.isDebugEnabled()) {
                NAMING_LOGGER.debug("[BEAT] {} sending beat to server: {}", namespaceId, beatInfo.toString());
            }
            Map<String, String> params = new HashMap<String, String>(4);
            params.put("beat", JSON.toJSONString(beatInfo));
            params.put(CommonParams.NAMESPACE_ID, namespaceId);
            params.put(CommonParams.SERVICE_NAME, beatInfo.getServiceName());
            String result = reqAPI(UtilAndComs.NACOS_URL_BASE + "/instance/beat", params, HttpMethod.PUT);
            JSONObject jsonObject = JSON.parseObject(result);

            if (jsonObject != null) {
                return jsonObject.getLong("clientBeatInterval");
            }
        } catch (Exception e) {
            NAMING_LOGGER.error("[CLIENT-BEAT] failed to send beat: " + JSON.toJSONString(beatInfo), e);
        }
        return 0L;
    }

    public boolean serverHealthy() {

        try {
            String result = reqAPI(UtilAndComs.NACOS_URL_BASE + "/operator/metrics", new HashMap<String, String>(2));
            JSONObject json = JSON.parseObject(result);
            String serverStatus = json.getString("status");
            return "UP".equals(serverStatus);
        } catch (Exception e) {
            return false;
        }
    }

    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName) throws NacosException {
        return getServiceList(pageNo, pageSize, groupName, null);
    }

    public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector) throws NacosException {

        Map<String, String> params = new HashMap<String, String>(4);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put(CommonParams.NAMESPACE_ID, namespaceId);
        params.put(CommonParams.GROUP_NAME, groupName);

        if (selector != null) {
            switch (SelectorType.valueOf(selector.getType())) {
                case none:
                    break;
                case label:
                    ExpressionSelector expressionSelector = (ExpressionSelector) selector;
                    params.put("selector", JSON.toJSONString(expressionSelector));
                    break;
                default:
                    break;
            }
        }

        String result = reqAPI(UtilAndComs.NACOS_URL_BASE + "/service/list", params);

        JSONObject json = JSON.parseObject(result);
        ListView<String> listView = new ListView<String>();
        listView.setCount(json.getInteger("count"));
        listView.setData(JSON.parseObject(json.getString("doms"), new TypeReference<List<String>>() {
        }));

        return listView;
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
        return callServer(api, params, curServer, HttpMethod.GET);
    }

    public String callServer(String api, Map<String, String> params, String curServer, String method)
        throws NacosException {
        long start = System.currentTimeMillis();
        long end = 0;
        checkSignature(params);
        List<String> headers = builderHeaders();

        String url;
        if (curServer.startsWith(UtilAndComs.HTTPS) || curServer.startsWith(UtilAndComs.HTTP)) {
            url = curServer + api;
        } else {
            if (!curServer.contains(UtilAndComs.SERVER_ADDR_IP_SPLITER)) {
                curServer = curServer + UtilAndComs.SERVER_ADDR_IP_SPLITER + serverPort;
            }
            url = HttpClient.getPrefix() + curServer + api;
        }

        HttpClient.HttpResult result = HttpClient.request(url, headers, params, UtilAndComs.ENCODING, method);
        end = System.currentTimeMillis();

        MetricsMonitor.getNamingRequestMonitor(method, url, String.valueOf(result.code))
            .observe(end - start);

        if (HttpURLConnection.HTTP_OK == result.code) {
            return result.content;
        }

        if (HttpURLConnection.HTTP_NOT_MODIFIED == result.code) {
            return StringUtils.EMPTY;
        }

        throw new NacosException(NacosException.SERVER_ERROR, "failed to req API:"
            + curServer + api + ". code:"
            + result.code + " msg: " + result.content);
    }

    public String reqAPI(String api, Map<String, String> params, List<String> servers) {
        return reqAPI(api, params, servers, HttpMethod.GET);
    }

    public String reqAPI(String api, Map<String, String> params, List<String> servers, String method) {

        params.put(CommonParams.NAMESPACE_ID, getNamespaceId());

        if (CollectionUtils.isEmpty(servers) && StringUtils.isEmpty(nacosDomain)) {
            throw new IllegalArgumentException("no server available");
        }

        Exception exception = new Exception();

        if (servers != null && !servers.isEmpty()) {

            Random random = new Random(System.currentTimeMillis());
            int index = random.nextInt(servers.size());

            for (int i = 0; i < servers.size(); i++) {
                String server = servers.get(index);
                try {
                    return callServer(api, params, server, method);
                } catch (NacosException e) {
                    exception = e;
                    NAMING_LOGGER.error("request {} failed.", server, e);
                } catch (Exception e) {
                    exception = e;
                    NAMING_LOGGER.error("request {} failed.", server, e);
                }

                index = (index + 1) % servers.size();
            }

            throw new IllegalStateException("failed to req API:" + api + " after all servers(" + servers + ") tried: "
                + exception.getMessage());
        }

        for (int i = 0; i < UtilAndComs.REQUEST_DOMAIN_RETRY_COUNT; i++) {
            try {
                return callServer(api, params, nacosDomain);
            } catch (Exception e) {
                exception = e;
                NAMING_LOGGER.error("[NA] req api:" + api + " failed, server(" + nacosDomain, e);
            }
        }

        throw new IllegalStateException("failed to req API:/api/" + api + " after all servers(" + servers + ") tried: "
            + exception.getMessage());

    }

    private void checkSignature(Map<String, String> params) {
        String ak = getAccessKey();
        String sk = getSecretKey();
        if (StringUtils.isEmpty(ak) && StringUtils.isEmpty(sk)) {
            return;
        }

        try {
            String app = System.getProperty("project.name");
            String signData = getSignData(params.get("serviceName"));
            String signature = SignUtil.sign(signData, sk);
            params.put("signature", signature);
            params.put("data", signData);
            params.put("ak", ak);
            params.put("app", app);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> builderHeaders() {
        List<String> headers = Arrays.asList("Client-Version", UtilAndComs.VERSION,
            "User-Agent", UtilAndComs.VERSION,
            "Accept-Encoding", "gzip,deflate,sdch",
            "Connection", "Keep-Alive",
            "RequestId", UuidUtils.generateUuid(), "Request-Module", "Naming");
        return headers;
    }

    private static String getSignData(String serviceName) {
        return StringUtils.isNotEmpty(serviceName)
            ? System.currentTimeMillis() + "@@" + serviceName
            : String.valueOf(System.currentTimeMillis());
    }

    public String getAccessKey() {
        if (properties == null) {

            return SpasAdapter.getAk();
        }

        return TemplateUtils.stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.ACCESS_KEY), new Callable<String>() {

            @Override
            public String call() {
                return SpasAdapter.getAk();
            }
        });
    }

    public String getSecretKey() {
        if (properties == null) {

            return SpasAdapter.getSk();
        }

        return TemplateUtils.stringEmptyAndThenExecute(properties.getProperty(PropertyKeyConst.SECRET_KEY), new Callable<String>() {
            @Override
            public String call() throws Exception {
                return SpasAdapter.getSk();
            }
        });
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
        setServerPort(DEFAULT_SERVER_PORT);
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;

        String sp = System.getProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT);
        if (com.alibaba.nacos.client.utils.StringUtils.isNotBlank(sp)) {
            this.serverPort = Integer.parseInt(sp);
        }
    }
    
}

