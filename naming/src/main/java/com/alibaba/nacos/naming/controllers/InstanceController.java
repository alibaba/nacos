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
package com.alibaba.nacos.naming.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Instance operation controller
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT)
public class InstanceController {

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private PushService pushService;

    @Autowired
    private ServiceManager serviceManager;

    private DataSource pushDataSource = new DataSource() {

        @Override
        public String getData(PushService.PushClient client) throws Exception {

            JSONObject result = new JSONObject();
            try {
                result = doSrvIPXT(client.getNamespaceId(), client.getServiceName(), client.getAgent(),
                    client.getClusters(), client.getSocketAddr().getAddress().getHostAddress(), 0, StringUtils.EMPTY,
                    false, StringUtils.EMPTY, StringUtils.EMPTY, false);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("PUSH-SERVICE: dom is not modified", e);
            }

            // overdrive the cache millis to push mode
            result.put("cacheMillis", switchDomain.getPushCacheMillis(client.getServiceName()));

            return result.toJSONString();
        }
    };

    @RequestMapping(value = "/instance", method = RequestMethod.POST)
    public String register(HttpServletRequest request) throws Exception {
        return regService(request);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.DELETE)
    public String deregister(HttpServletRequest request) throws Exception {
        Instance instance = getIPAddress(request);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            return "ok";
        }

        serviceManager.removeInstance(namespaceId, serviceName, instance.isEphemeral(), instance);

        return "ok";
    }

    @RequestMapping(value = {"/instance/update", "instance"}, method = RequestMethod.PUT)
    public String update(HttpServletRequest request) throws Exception {
        return regService(request);
    }

    @RequestMapping(value = {"/instances", "/instance/list"}, method = RequestMethod.GET)
    public JSONObject list(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);

        String dom = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String agent = request.getHeader("Client-Version");
        if (StringUtils.isBlank(agent)) {
            agent = request.getHeader("User-Agent");
        }
        String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
        String clientIP = WebUtils.optional(request, "clientIP", StringUtils.EMPTY);
        Integer udpPort = Integer.parseInt(WebUtils.optional(request, "udpPort", "0"));
        String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
        boolean isCheck = Boolean.parseBoolean(WebUtils.optional(request, "isCheck", "false"));

        String app = WebUtils.optional(request, "app", StringUtils.EMPTY);

        String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);

        boolean healthyOnly = Boolean.parseBoolean(WebUtils.optional(request, "healthyOnly", "false"));

        return doSrvIPXT(namespaceId, dom, agent, clusters, clientIP, udpPort, env, isCheck, app, tenant, healthyOnly);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.GET)
    public JSONObject detail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        Service domain = serviceManager.getService(namespaceId, serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "no dom " + serviceName + " found!");
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<Instance> ips = domain.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            throw new IllegalStateException("no ips found for cluster " + cluster + " in dom " + serviceName);
        }

        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                JSONObject result = new JSONObject();
                result.put("service", serviceName);
                result.put("ip", ip);
                result.put("port", port);
                result.put("clusterName", cluster);
                result.put("weight", instance.getWeight());
                result.put("healthy", instance.isValid());
                result.put("metadata", instance.getMetadata());
                result.put("instanceId", instance.generateInstanceId());
                return result;
            }
        }

        throw new IllegalStateException("no matched ip found!");
    }

    @RequestMapping(value = "/instance/beat", method = RequestMethod.PUT)
    public JSONObject beat(HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String beat = WebUtils.required(request, "beat");
        RsInfo clientBeat = JSON.parseObject(beat, RsInfo.class);
        if (StringUtils.isBlank(clientBeat.getCluster())) {
            clientBeat.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        String serviceName = WebUtils.optional(request, "serviceName", StringUtils.EMPTY);
        if (StringUtils.isBlank(serviceName)) {
            serviceName = WebUtils.required(request, "dom");
        }

        String clusterName = clientBeat.getCluster();

        if (StringUtils.isBlank(clusterName)) {
            clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[CLIENT-BEAT] full arguments: beat: {}, serviceName: {}", clientBeat, serviceName);
        }

        Instance instance = serviceManager.getInstance(namespaceId, serviceName, clientBeat.getCluster(), clientBeat.getIp(),
            clientBeat.getPort());

        if (instance == null) {
            instance = new Instance();
            instance.setPort(clientBeat.getPort());
            instance.setIp(clientBeat.getIp());
            instance.setWeight(clientBeat.getWeight());
            instance.setMetadata(clientBeat.getMetadata());
            instance.setClusterName(clusterName);
            instance.setServiceName(serviceName);
            instance.setInstanceId(instance.generateInstanceId());
            serviceManager.registerInstance(namespaceId, serviceName, clusterName, instance);
        }

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.SERVER_ERROR, "service not found: " + serviceName + "@" + namespaceId);
        }

        if (!distroMapper.responsible(serviceName)) {
            String server = distroMapper.mapSrv(serviceName);
            Loggers.EVT_LOG.info("I'm not responsible for {}, proxy it to {}", serviceName, server);
            Map<String, String> proxyParams = new HashMap<>(16);
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue()[0];
                proxyParams.put(key, value);
            }

            if (!server.contains(UtilsAndCommons.IP_PORT_SPLITER)) {
                server = server + UtilsAndCommons.IP_PORT_SPLITER + RunningConfig.getServerPort();
            }

            String url = "http://" + server + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/clientBeat";
            HttpClient.HttpResult httpResult = HttpClient.httpGet(url, null, proxyParams);

            if (httpResult.code != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("failed to proxy client beat to" + server + ", beat: " + beat);
            }
        } else {
            service.processClientBeat(clientBeat);
        }

        JSONObject result = new JSONObject();

        result.put("clientBeatInterval", switchDomain.getClientBeatInterval());

        return result;
    }


    @RequestMapping("/instance/listWithHealthStatus")
    public JSONObject listWithHealthStatus(HttpServletRequest request) throws NacosException {

        String key = WebUtils.required(request, "key");

        String domName;
        String namespaceId;

        if (key.contains(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)) {
            namespaceId = key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0];
            domName = key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
        } else {
            namespaceId = UtilsAndCommons.DEFAULT_NAMESPACE_ID;
            domName = key;
        }

        Service dom = serviceManager.getService(namespaceId, domName);

        if (dom == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom: " + domName + " not found.");
        }

        List<Instance> ips = dom.allIPs();

        JSONObject result = new JSONObject();
        JSONArray ipArray = new JSONArray();

        for (Instance ip : ips) {
            ipArray.add(ip.toIPAddr() + "_" + ip.isValid());
        }

        result.put("ips", ipArray);
        return result;
    }

    private String regService(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String app = WebUtils.optional(request, "app", "DEFAULT");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);

        Instance instance = getIPAddress(request);
        instance.setApp(app);
        instance.setServiceName(serviceName);
        instance.setInstanceId(instance.generateInstanceId());
        instance.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(metadata)) {
            instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }

        serviceManager.registerInstance(namespaceId, serviceName, clusterName, instance);

        return "ok";
    }

    private Instance getIPAddress(HttpServletRequest request) {

        String ip = WebUtils.required(request, "ip");
        String port = WebUtils.required(request, "port");
        String weight = WebUtils.optional(request, "weight", "1");
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        boolean enabled = BooleanUtils.toBoolean(WebUtils.optional(request, "enable", "true"));
        boolean ephemeral = BooleanUtils.toBoolean(WebUtils.optional(request, "ephemeral", "true"));

        Instance instance = new Instance();
        instance.setPort(Integer.parseInt(port));
        instance.setIp(ip);
        instance.setWeight(Double.parseDouble(weight));
        instance.setClusterName(cluster);
        instance.setEnabled(enabled);
        instance.setEphemeral(ephemeral);

        return instance;
    }

    public void checkIfDisabled(Service domObj) throws Exception {
        if (!domObj.getEnabled()) {
            throw new Exception("domain is disabled now.");
        }
    }

    public JSONObject doSrvIPXT(String namespaceId, String dom, String agent, String clusters, String clientIP, int udpPort,
                                String env, boolean isCheck, String app, String tid, boolean healthyOnly) throws Exception {

        JSONObject result = new JSONObject();
        Service domObj = serviceManager.getService(namespaceId, dom);

        if (domObj == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom not found: " + dom);
        }

        checkIfDisabled(domObj);

        long cacheMillis = switchDomain.getDefaultCacheMillis();

        // now try to enable the push
        try {
            if (udpPort > 0 && pushService.canEnablePush(agent)) {
                pushService.addClient(namespaceId, dom,
                    clusters,
                    agent,
                    new InetSocketAddress(clientIP, udpPort),
                    pushDataSource,
                    tid,
                    app);
                cacheMillis = switchDomain.getPushCacheMillis(dom);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-API] failed to added push client", e);
            cacheMillis = switchDomain.getDefaultCacheMillis();
        }

        List<Instance> srvedIPs;

        srvedIPs = domObj.srvIPs(Arrays.asList(StringUtils.split(clusters, ",")));

        // filter ips using selector:
        if (domObj.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
            srvedIPs = domObj.getSelector().select(clientIP, srvedIPs);
        }

        if (CollectionUtils.isEmpty(srvedIPs)) {

            if (Loggers.DEBUG_LOG.isDebugEnabled()) {
                Loggers.DEBUG_LOG.debug("no instance to serve for service: " + dom);
            }

            result.put("hosts", new JSONArray());
            result.put("dom", dom);
            result.put("cacheMillis", cacheMillis);
            result.put("lastRefTime", System.currentTimeMillis());
            result.put("checksum", domObj.getChecksum() + System.currentTimeMillis());
            result.put("useSpecifiedURL", false);
            result.put("clusters", clusters);
            result.put("env", env);
            result.put("metadata", domObj.getMetadata());
            return result;
        }

        Map<Boolean, List<Instance>> ipMap = new HashMap<>(2);
        ipMap.put(Boolean.TRUE, new ArrayList<>());
        ipMap.put(Boolean.FALSE, new ArrayList<>());

        for (Instance ip : srvedIPs) {
            ipMap.get(ip.isValid()).add(ip);
        }

        if (isCheck) {
            result.put("reachProtectThreshold", false);
        }

        double threshold = domObj.getProtectThreshold();

        if ((float) ipMap.get(Boolean.TRUE).size() / srvedIPs.size() <= threshold) {

            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, dom: {}", dom);
            if (isCheck) {
                result.put("reachProtectThreshold", true);
            }

            ipMap.get(Boolean.TRUE).addAll(ipMap.get(Boolean.FALSE));
            ipMap.get(Boolean.FALSE).clear();
        }

        if (isCheck) {
            result.put("protectThreshold", domObj.getProtectThreshold());
            result.put("reachLocalSiteCallThreshold", false);

            return new JSONObject();
        }

        JSONArray hosts = new JSONArray();

        for (Map.Entry<Boolean, List<Instance>> entry : ipMap.entrySet()) {
            List<Instance> ips = entry.getValue();

            if (healthyOnly && !entry.getKey()) {
                continue;
            }

            for (Instance instance : ips) {
                JSONObject ipObj = new JSONObject();

                ipObj.put("ip", instance.getIp());
                ipObj.put("port", instance.getPort());
                ipObj.put("valid", entry.getKey());
                ipObj.put("marked", instance.isMarked());
                ipObj.put("instanceId", instance.getInstanceId());
                ipObj.put("metadata", instance.getMetadata());
                ipObj.put("enabled", instance.isEnabled());
                ipObj.put("weight", instance.getWeight());
                ipObj.put("clusterName", instance.getClusterName());
                ipObj.put("serviceName", instance.getServiceName());
                ipObj.put("ephemeral", instance.isEphemeral());
                hosts.add(ipObj);

            }
        }

        result.put("hosts", hosts);

        result.put("dom", dom);
        result.put("cacheMillis", cacheMillis);
        result.put("lastRefTime", System.currentTimeMillis());
        result.put("checksum", domObj.getChecksum() + System.currentTimeMillis());
        result.put("useSpecifiedURL", false);
        result.put("clusters", clusters);
        result.put("env", env);
        result.put("metadata", domObj.getMetadata());
        return result;
    }
}
