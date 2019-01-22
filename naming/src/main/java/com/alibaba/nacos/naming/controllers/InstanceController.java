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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.web.OverrideParameterRequestWrapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.net.InetAddress;
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

            Map<String, String[]> params = new HashMap<String, String[]>(10);
            params.put("dom", new String[]{client.getDom()});
            params.put("clusters", new String[]{client.getClusters()});

            // set udp port to 0, otherwise will cause recursion
            params.put("udpPort", new String[]{"0"});

            InetAddress inetAddress = client.getSocketAddr().getAddress();
            params.put("clientIP", new String[]{inetAddress.getHostAddress()});
            params.put("header:Client-Version", new String[]{client.getAgent()});

            JSONObject result = new JSONObject();
            try {
                result = doSrvIPXT(client.getNamespaceId(), client.getDom(), client.getAgent(),
                    client.getClusters(), inetAddress.getHostAddress(), 0, StringUtils.EMPTY,
                    false, StringUtils.EMPTY, StringUtils.EMPTY, false);
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("PUSH-SERVICE: dom is not modified", e);
            }

            // overdrive the cache millis to push mode
            result.put("cacheMillis", switchDomain.getPushCacheMillis(client.getDom()));

            return result.toJSONString();
        }
    };

    @RequestMapping(value = "/instance", method = RequestMethod.POST)
    public String register(HttpServletRequest request) throws Exception {

        OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(request);

        String serviceJson = WebUtils.optional(request, "service", StringUtils.EMPTY);

        // set service info:
        if (StringUtils.isNotEmpty(serviceJson)) {
            JSONObject service = JSON.parseObject(serviceJson);
            requestWrapper.addParameter("serviceName", service.getString("name"));
        }

        return regService(requestWrapper);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.DELETE)
    public String deregister(HttpServletRequest request) throws Exception {
        IpAddress ipAddress = getIPAddress(request);
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);

        VirtualClusterDomain virtualClusterDomain = serviceManager.getService(namespaceId, serviceName);
        if (virtualClusterDomain == null) {
            return "ok";
        }

        serviceManager.removeInstance(namespaceId, serviceName, ipAddress);

        return "ok";
    }

    @RequestMapping(value = {"/instance/update", "instance"}, method = RequestMethod.PUT)
    public String update(HttpServletRequest request) throws Exception {
        return regService(request);
    }

    @RequestMapping(value = {"/instances", "/instance/list"}, method = RequestMethod.GET)
    public JSONObject queryList(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        String dom = WebUtils.required(request, "serviceName");
        String agent = request.getHeader("Client-Version");
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
    public JSONObject queryDetail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);
        String cluster = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        VirtualClusterDomain domain = serviceManager.getService(namespaceId, serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "no dom " + serviceName + " found!");
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<IpAddress> ips = domain.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            throw new IllegalStateException("no ips found for cluster " + cluster + " in dom " + serviceName);
        }

        for (IpAddress ipAddress : ips) {
            if (ipAddress.getIp().equals(ip) && ipAddress.getPort() == port) {
                JSONObject result = new JSONObject();
                result.put("service", serviceName);
                result.put("ip", ip);
                result.put("port", port);
                result.put("clusterName", cluster);
                result.put("weight", ipAddress.getWeight());
                result.put("healthy", ipAddress.isValid());
                result.put("metadata", ipAddress.getMetadata());
                result.put("instanceId", ipAddress.generateInstanceId());
                return result;
            }
        }

        throw new IllegalStateException("no matched ip found!");
    }

    @RequestMapping(value = "/instance/beat", method = RequestMethod.PUT)
    public JSONObject sendBeat(HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
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

        IpAddress ipAddress = serviceManager.getInstance(namespaceId, serviceName, clientBeat.getCluster(), clientBeat.getIp(),
            clientBeat.getPort());

        if (ipAddress == null) {
            ipAddress = new IpAddress();
            ipAddress.setPort(clientBeat.getPort());
            ipAddress.setIp(clientBeat.getIp());
            ipAddress.setWeight(clientBeat.getWeight());
            ipAddress.setMetadata(clientBeat.getMetadata());
            ipAddress.setClusterName(clusterName);
            ipAddress.setServiceName(serviceName);
            ipAddress.setInstanceId(ipAddress.generateInstanceId());
            serviceManager.registerInstance(namespaceId, serviceName, clusterName, ipAddress);
        }

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) serviceManager.getService(namespaceId, serviceName);

        if (virtualClusterDomain == null) {
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

            if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            String url = "http://" + server + RunningConfig.getContextPath()
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/clientBeat";
            HttpClient.HttpResult httpResult = HttpClient.httpGet(url, null, proxyParams);

            if (httpResult.code != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("failed to proxy client beat to" + server + ", beat: " + beat);
            }
        } else {
            virtualClusterDomain.processClientBeat(clientBeat);
        }

        JSONObject result = new JSONObject();

        result.put("clientBeatInterval", switchDomain.getClientBeatInterval());

        return result;
    }


    @RequestMapping("/ip4Dom2")
    public JSONObject ip4Dom2(HttpServletRequest request) throws NacosException {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String domName = WebUtils.required(request, "dom");

        VirtualClusterDomain dom = serviceManager.getService(namespaceId, domName);

        if (dom == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom: " + domName + " not found.");
        }

        List<IpAddress> ips = dom.allIPs();

        JSONObject result = new JSONObject();
        JSONArray ipArray = new JSONArray();

        for (IpAddress ip : ips) {
            ipArray.add(ip.toIPAddr() + "_" + ip.isValid());
        }

        result.put("ips", ipArray);
        return result;
    }

    private String regService(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, "serviceName");
        String clusterName = WebUtils.required(request, "clusterName");
        String app = WebUtils.optional(request, "app", "DEFAULT");
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        IpAddress ipAddress = getIPAddress(request);
        ipAddress.setApp(app);
        ipAddress.setServiceName(serviceName);
        ipAddress.setInstanceId(ipAddress.generateInstanceId());
        ipAddress.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(metadata)) {
            ipAddress.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }

        serviceManager.registerInstance(namespaceId, serviceName, clusterName, ipAddress);

        return "ok";
    }

    private IpAddress getIPAddress(HttpServletRequest request) {

        String ip = WebUtils.required(request, "ip");
        String port = WebUtils.required(request, "port");
        String weight = WebUtils.optional(request, "weight", "1");
        String cluster = WebUtils.optional(request, "cluster", StringUtils.EMPTY);
        if (StringUtils.isEmpty(cluster)) {
            cluster = WebUtils.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        boolean enabled = BooleanUtils.toBoolean(WebUtils.optional(request, "enable", "true"));

        IpAddress ipAddress = new IpAddress();
        ipAddress.setPort(Integer.parseInt(port));
        ipAddress.setIp(ip);
        ipAddress.setWeight(Double.parseDouble(weight));
        ipAddress.setClusterName(cluster);
        ipAddress.setEnabled(enabled);

        return ipAddress;
    }

    public void checkIfDisabled(VirtualClusterDomain domObj) throws Exception {
        if (!domObj.getEnabled()) {
            throw new Exception("domain is disabled now.");
        }
    }

    public JSONObject doSrvIPXT(String namespaceId, String dom, String agent, String clusters, String clientIP, int udpPort,
                                String env, boolean isCheck, String app, String tid, boolean healthyOnly) throws Exception {

        JSONObject result = new JSONObject();
        VirtualClusterDomain domObj = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);

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

        List<IpAddress> srvedIPs;

        srvedIPs = domObj.srvIPs(clientIP, Arrays.asList(StringUtils.split(clusters, ",")));

        // filter ips using selector:
        if (domObj.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
            srvedIPs = domObj.getSelector().select(clientIP, srvedIPs);
        }

        if (CollectionUtils.isEmpty(srvedIPs)) {
            String msg = "no ip to serve for dom: " + dom;

            Loggers.SRV_LOG.debug(msg);
        }

        Map<Boolean, List<IpAddress>> ipMap = new HashMap<>(2);
        ipMap.put(Boolean.TRUE, new ArrayList<IpAddress>());
        ipMap.put(Boolean.FALSE, new ArrayList<IpAddress>());

        for (IpAddress ip : srvedIPs) {
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

        for (Map.Entry<Boolean, List<IpAddress>> entry : ipMap.entrySet()) {
            List<IpAddress> ips = entry.getValue();

            if (healthyOnly && !entry.getKey()) {
                continue;
            }

            for (IpAddress ip : ips) {
                JSONObject ipObj = new JSONObject();

                ipObj.put("ip", ip.getIp());
                ipObj.put("port", ip.getPort());
                ipObj.put("valid", entry.getKey());
                ipObj.put("marked", ip.isMarked());
                ipObj.put("instanceId", ip.getInstanceId());
                ipObj.put("metadata", ip.getMetadata());
                ipObj.put("enabled", ip.isEnabled());
                ipObj.put("weight", ip.getWeight());
                ipObj.put("clusterName", ip.getClusterName());
                ipObj.put("serviceName", ip.getServiceName());
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
