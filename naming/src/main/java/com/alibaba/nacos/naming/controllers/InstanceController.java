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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.web.CanDistro;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Instance operation controller
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance")
public class InstanceController {

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private PushService pushService;

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private InstanceManager instanceManager;

    private DataSource pushDataSource = new DataSource() {

        @Override
        public String getData(PushService.PushClient client) {

            JSONObject result = new JSONObject();
            try {
                result = doSrvIPXT(client.getNamespaceId(), client.getServiceName(), client.getAgent(),
                    client.getClusters(), client.getSocketAddr().getAddress().getHostAddress(), 0, StringUtils.EMPTY,
                    false, StringUtils.EMPTY, StringUtils.EMPTY, false);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("PUSH-SERVICE: service is not modified", e);
            }

            // overdrive the cache millis to push mode
            result.put("cacheMillis", switchDomain.getPushCacheMillis(client.getServiceName()));

            return result.toJSONString();
        }
    };

    /**
     * Register an instance to a service in AP mode.
     * <p>
     * This method creates service or cluster silently if they don't exist.
     *
     * @param request the current HTTP request
     * @return "ok" if the registration is successful
     * @throws Exception if the server for the instance is not found
     */
    @CanDistro
    @PostMapping
    public String register(HttpServletRequest request) throws Exception {
        instanceManager.register(request);
        return "ok";
    }

    /**
     * Deregister the instance.
     *
     * @param request the current http request
     * @return "ok" if the deregistration is successful
     * @throws Exception if the server for the instance is not found
     */
    @CanDistro
    @DeleteMapping
    public String deregister(HttpServletRequest request) throws Exception {
        instanceManager.deregister(request);
        return "ok";
    }

    /**
     * Update the instance.
     *
     * @param request the current http request
     * @return "ok" if the update is successful
     * @throws Exception if the server for the instance is not found or the instance is not found
     */
    @CanDistro
    @PutMapping
    public String update(HttpServletRequest request) throws Exception {
        String agent = WebUtils.getUserAgent(request);

        ClientInfo clientInfo = new ClientInfo(agent);

        if (clientInfo.type == ClientInfo.ClientType.JAVA &&
            clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
            instanceManager.update(request);
        } else {
            instanceManager.register(request);
        }
        return "ok";
    }

    @GetMapping("/list")
    public JSONObject list(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);

        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String agent = WebUtils.getUserAgent(request);
        String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
        String clientIP = WebUtils.optional(request, "clientIP", StringUtils.EMPTY);
        Integer udpPort = Integer.parseInt(WebUtils.optional(request, "udpPort", "0"));
        String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
        boolean isCheck = Boolean.parseBoolean(WebUtils.optional(request, "isCheck", "false"));

        String app = WebUtils.optional(request, "app", StringUtils.EMPTY);

        String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);

        boolean healthyOnly = Boolean.parseBoolean(WebUtils.optional(request, "healthyOnly", "false"));

        return doSrvIPXT(namespaceId, serviceName, agent, clusters, clientIP, udpPort, env, isCheck, app, tenant,
            healthyOnly);
    }

    @GetMapping
    public JSONObject detail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND, "no service " + serviceName + " found!");
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<Instance> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            throw new NacosException(NacosException.NOT_FOUND,
                "no ips found for cluster " + cluster + " in service " + serviceName);
        }

        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                JSONObject result = new JSONObject();
                result.put("service", serviceName);
                result.put("ip", ip);
                result.put("port", port);
                result.put("clusterName", cluster);
                result.put("weight", instance.getWeight());
                result.put("healthy", instance.isHealthy());
                result.put("metadata", instance.getMetadata());
                result.put("instanceId", instance.getInstanceId());
                return result;
            }
        }

        throw new NacosException(NacosException.NOT_FOUND, "no matched ip found!");
    }

    /**
     * Heartbeat checking for instances.
     *
     * @param request the current http request
     * @return the new heart beat interval
     * @throws Exception if the server for the instance is not found
     */
    @CanDistro
    @PutMapping("/beat")
    public JSONObject beat(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                           @RequestParam String beat,
                           @RequestParam String serviceName) throws Exception {

        JSONObject result = new JSONObject();

        result.put("clientBeatInterval", switchDomain.getClientBeatInterval());

        RsInfo clientBeat = JSON.parseObject(beat, RsInfo.class);

        if (!switchDomain.isDefaultInstanceEphemeral() && !clientBeat.isEphemeral()) {
            return result;
        }

        if (StringUtils.isBlank(clientBeat.getCluster())) {
            clientBeat.setCluster(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }

        String clusterName = clientBeat.getCluster();

        if (Loggers.SRV_LOG.isDebugEnabled()) {
            Loggers.SRV_LOG.debug("[CLIENT-BEAT] full arguments: beat: {}, serviceName: {}", clientBeat, serviceName);
        }

        Instance instance = serviceManager.getInstance(namespaceId, serviceName, clientBeat.getCluster(),
            clientBeat.getIp(),
            clientBeat.getPort());

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (instance == null) {
            if (service == null) {
                service = serviceManager.createServiceIfAbsent(namespaceId, serviceName, clientBeat.isEphemeral());
            }
            Cluster cluster = new Cluster(clusterName, service);
            service.addCluster(cluster);
            instance = new Instance(clientBeat.getIp(), clientBeat.getPort(), cluster);
            instance.setWeight(clientBeat.getWeight());
            instance.setMetadata(clientBeat.getMetadata());
            instance.setClusterName(clusterName);
            instance.setServiceName(serviceName);
            instance.setInstanceId(instance.getInstanceId());
            instance.setEphemeral(clientBeat.isEphemeral());

            serviceManager.registerInstance(namespaceId, serviceName, instance);
        }

        if (service == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "service not found: " + serviceName + "@" + namespaceId);
        }

        service.processClientBeat(clientBeat);
        result.put("clientBeatInterval", instance.getInstanceHeartBeatInterval());
        return result;
    }

    @RequestMapping("/statuses")
    public JSONObject listWithHealthStatus(@RequestParam String key) throws NacosException {

        String serviceName;
        String namespaceId;

        if (key.contains(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)) {
            namespaceId = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[0];
            serviceName = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[1];
        } else {
            namespaceId = Constants.DEFAULT_NAMESPACE_ID;
            serviceName = key;
        }

        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND, "service: " + serviceName + " not found.");
        }

        List<Instance> ips = service.allIPs();

        JSONObject result = new JSONObject();
        JSONArray ipArray = new JSONArray();

        for (Instance ip : ips) {
            ipArray.add(ip.toIPAddr() + "_" + ip.isHealthy());
        }

        result.put("ips", ipArray);
        return result;
    }

    private Instance parseInstance(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String app = WebUtils.optional(request, "app", "DEFAULT");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);

        Instance instance = getIPAddress(request);
        instance.setApp(app);
        instance.setServiceName(serviceName);
        // Generate simple instance id first. This value would be updated according to
        // INSTANCE_ID_GENERATOR.
        instance.setInstanceId(instance.generateInstanceId());
        instance.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(metadata)) {
            instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }

        instance.validate();

        return instance;
    }

    private Instance getIPAddress(HttpServletRequest request) {

        String ip = WebUtils.required(request, "ip");
        String port = WebUtils.required(request, "port");
        String weight = WebUtils.optional(request, "weight", "1");
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
        if (StringUtils.isBlank(cluster)) {
            cluster = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        boolean healthy = BooleanUtils.toBoolean(WebUtils.optional(request, "healthy", "true"));

        String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
        boolean enabled;
        if (StringUtils.isBlank(enabledString)) {
            enabled = BooleanUtils.toBoolean(WebUtils.optional(request, "enable", "true"));
        } else {
            enabled = BooleanUtils.toBoolean(enabledString);
        }

        boolean ephemeral = BooleanUtils.toBoolean(WebUtils.optional(request, "ephemeral",
            String.valueOf(switchDomain.isDefaultInstanceEphemeral())));

        Instance instance = new Instance();
        instance.setPort(Integer.parseInt(port));
        instance.setIp(ip);
        instance.setWeight(Double.parseDouble(weight));
        instance.setClusterName(cluster);
        instance.setHealthy(healthy);
        instance.setEnabled(enabled);
        instance.setEphemeral(ephemeral);

        return instance;
    }

    private void checkIfDisabled(Service service) throws Exception {
        if (!service.getEnabled()) {
            throw new Exception("service is disabled now.");
        }
    }

    public JSONObject doSrvIPXT(String namespaceId, String serviceName, String agent, String clusters, String clientIP,
                                int udpPort,
                                String env, boolean isCheck, String app, String tid, boolean healthyOnly)
        throws Exception {

        ClientInfo clientInfo = new ClientInfo(agent);
        JSONObject result = new JSONObject();
        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("no instance to serve for service: {}", serviceName);
            }
            result.put("name", serviceName);
            result.put("clusters", clusters);
            result.put("hosts", new JSONArray());
            return result;
        }

        checkIfDisabled(service);

        long cacheMillis = switchDomain.getDefaultCacheMillis();

        // now try to enable the push
        try {
            if (udpPort > 0 && pushService.canEnablePush(agent)) {
                pushService.addClient(namespaceId, serviceName,
                    clusters,
                    agent,
                    new InetSocketAddress(clientIP, udpPort),
                    pushDataSource,
                    tid,
                    app);
                cacheMillis = switchDomain.getPushCacheMillis(serviceName);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-API] failed to added push client", e);
            cacheMillis = switchDomain.getDefaultCacheMillis();
        }

        List<Instance> srvedIPs;

        srvedIPs = service.srvIPs(Arrays.asList(StringUtils.split(clusters, ",")));

        // filter ips using selector:
        if (service.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
            srvedIPs = service.getSelector().select(clientIP, srvedIPs);
        }

        if (CollectionUtils.isEmpty(srvedIPs)) {

            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("no instance to serve for service: {}", serviceName);
            }

            if (clientInfo.type == ClientInfo.ClientType.JAVA &&
                clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
                result.put("dom", serviceName);
            } else {
                result.put("dom", NamingUtils.getServiceName(serviceName));
            }

            result.put("hosts", new JSONArray());
            result.put("name", serviceName);
            result.put("cacheMillis", cacheMillis);
            result.put("lastRefTime", System.currentTimeMillis());
            result.put("checksum", service.getChecksum());
            result.put("useSpecifiedURL", false);
            result.put("clusters", clusters);
            result.put("env", env);
            result.put("metadata", service.getMetadata());
            return result;
        }

        Map<Boolean, List<Instance>> ipMap = new HashMap<>(2);
        ipMap.put(Boolean.TRUE, new ArrayList<>());
        ipMap.put(Boolean.FALSE, new ArrayList<>());

        for (Instance ip : srvedIPs) {
            ipMap.get(ip.isHealthy()).add(ip);
        }

        if (isCheck) {
            result.put("reachProtectThreshold", false);
        }

        double threshold = service.getProtectThreshold();

        if ((float)ipMap.get(Boolean.TRUE).size() / srvedIPs.size() <= threshold) {

            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}", serviceName);
            if (isCheck) {
                result.put("reachProtectThreshold", true);
            }

            ipMap.get(Boolean.TRUE).addAll(ipMap.get(Boolean.FALSE));
            ipMap.get(Boolean.FALSE).clear();
        }

        if (isCheck) {
            result.put("protectThreshold", service.getProtectThreshold());
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

                // remove disabled instance:
                if (!instance.isEnabled()) {
                    continue;
                }

                JSONObject ipObj = new JSONObject();

                ipObj.put("ip", instance.getIp());
                ipObj.put("port", instance.getPort());
                // deprecated since nacos 1.0.0:
                ipObj.put("valid", entry.getKey());
                ipObj.put("healthy", entry.getKey());
                ipObj.put("marked", instance.isMarked());
                ipObj.put("instanceId", instance.getInstanceId());
                ipObj.put("metadata", instance.getMetadata());
                ipObj.put("enabled", instance.isEnabled());
                ipObj.put("weight", instance.getWeight());
                ipObj.put("clusterName", instance.getClusterName());
                if (clientInfo.type == ClientInfo.ClientType.JAVA &&
                    clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
                    ipObj.put("serviceName", instance.getServiceName());
                } else {
                    ipObj.put("serviceName", NamingUtils.getServiceName(instance.getServiceName()));
                }

                ipObj.put("ephemeral", instance.isEphemeral());
                hosts.add(ipObj);

            }
        }

        result.put("hosts", hosts);
        if (clientInfo.type == ClientInfo.ClientType.JAVA &&
            clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
            result.put("dom", serviceName);
        } else {
            result.put("dom", NamingUtils.getServiceName(serviceName));
        }
        result.put("name", serviceName);
        result.put("cacheMillis", cacheMillis);
        result.put("lastRefTime", System.currentTimeMillis());
        result.put("checksum", service.getChecksum());
        result.put("useSpecifiedURL", false);
        result.put("clusters", clusters);
        result.put("env", env);
        result.put("metadata", service.getMetadata());
        return result;
    }
}
