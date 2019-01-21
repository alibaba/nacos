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
package com.alibaba.nacos.naming.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.common.util.Md5Utils;
import com.alibaba.nacos.common.util.SystemUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.*;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.AccessControlException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.alibaba.nacos.common.util.SystemUtils.readClusterConf;
import static com.alibaba.nacos.common.util.SystemUtils.writeClusterConf;

/**
 * Old API entry
 *
 * @author nacos
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api")
public class ApiCommands {

    @Autowired
    protected ServiceManager serviceManager;

    @Autowired
    private SwitchManager switchManager;

    @Autowired
    private ServerListManager serverListManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private PushService pushService;

    @Autowired
    private DistroMapper distroMapper;

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
                result = ApiCommands.this.doSrvIPXT(client.getNamespaceId(), client.getDom(), client.getAgent(),
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


    @RequestMapping("/dom")
    public JSONObject dom(HttpServletRequest request) throws NacosException {
        // SDK before version 2.0,0 use 'name' instead of 'dom' here
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String name = WebUtils.required(request, "dom");

        Domain dom = serviceManager.getService(namespaceId, name);
        if (dom == null) {
            throw new NacosException(NacosException.NOT_FOUND, "Dom doesn't exist");
        }

        return toPacket(dom);
    }

    @RequestMapping("/domCount")
    public JSONObject domCount(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("count", serviceManager.getDomCount());

        return result;
    }

    @RequestMapping("/rt4Dom")
    public JSONObject rt4Dom(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");

        VirtualClusterDomain domObj
            = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);
        if (domObj == null) {
            throw new IllegalArgumentException("request dom doesn't exist");
        }

        JSONObject result = new JSONObject();

        JSONArray clusters = new JSONArray();
        for (Map.Entry<String, Cluster> entry : domObj.getClusterMap().entrySet()) {
            JSONObject packet = new JSONObject();
            HealthCheckTask task = entry.getValue().getHealthCheckTask();

            packet.put("name", entry.getKey());
            packet.put("checkRTBest", task.getCheckRTBest());
            packet.put("checkRTWorst", task.getCheckRTWorst());
            packet.put("checkRTNormalized", task.getCheckRTNormalized());

            clusters.add(packet);
        }
        result.put("clusters", clusters);

        return result;
    }

    @RequestMapping("/ip4Dom2")
    public JSONObject ip4Dom2(HttpServletRequest request) throws NacosException {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String domName = WebUtils.required(request, "dom");

        VirtualClusterDomain dom = (VirtualClusterDomain) serviceManager.getService(namespaceId, domName);

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

    @RequestMapping("/ip4Dom")
    public JSONObject ip4Dom(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();
        try {
            String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
                UtilsAndCommons.getDefaultNamespaceId());
            String domName = WebUtils.required(request, "dom");
            String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
            String agent = WebUtils.optional(request, "header:Client-Version", StringUtils.EMPTY);

            VirtualClusterDomain dom = (VirtualClusterDomain) serviceManager.getService(namespaceId, domName);

            if (dom == null) {
                throw new NacosException(NacosException.NOT_FOUND, "dom: " + domName + " not found!");
            }

            List<IpAddress> ips = null;
            if (StringUtils.isEmpty(clusters)) {
                ips = dom.allIPs();
            } else {
                ips = dom.allIPs(Arrays.asList(clusters.split(",")));
            }

            if (CollectionUtils.isEmpty(ips)) {
                result.put("ips", Collections.emptyList());
                return result;
            }

            ClientInfo clientInfo = new ClientInfo(agent);

            JSONArray ipArray = new JSONArray();
            for (IpAddress ip : ips) {
                JSONObject ipPac = new JSONObject();

                ipPac.put("ip", ip.getIp());
                ipPac.put("valid", ip.isValid());
                ipPac.put("port", ip.getPort());
                ipPac.put("marked", ip.isMarked());
                ipPac.put("app", ip.getApp());

                if (clientInfo.version.compareTo(VersionUtil.parseVersion("1.5.0")) >= 0) {
                    ipPac.put("weight", ip.getWeight());
                } else {
                    double weight = ip.getWeight();
                    if (weight == 0) {
                        ipPac.put("weight", (int) ip.getWeight());
                    } else {
                        ipPac.put("weight", ip.getWeight() < 1 ? 1 : (int) ip.getWeight());
                    }
                }
                ipPac.put("checkRT", ip.getCheckRT());
                ipPac.put("cluster", ip.getClusterName());

                ipArray.add(ipPac);
            }

            result.put("ips", ipArray);
        } catch (Throwable e) {
            Loggers.SRV_LOG.warn("[NACOS-IP4DOM] failed to call ip4Dom, caused ", e);
            throw new IllegalArgumentException(e);
        }

        return result;
    }

    @RequestMapping("/regDom")
    public String regDom(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);
        if (serviceManager.getService(namespaceId, dom) != null) {
            throw new IllegalArgumentException("specified dom already exists, dom : " + dom);
        }

        addOrReplaceDom(request);

        return "ok";
    }

    @RequestMapping("/clientBeat")
    public JSONObject clientBeat(HttpServletRequest request) throws Exception {

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

    private String addOrReplaceDom(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);
        String owners = WebUtils.optional(request, "owners", StringUtils.EMPTY);
        String token = WebUtils.optional(request, "token", Md5Utils.getMD5(dom, "utf-8"));

        float protectThreshold = NumberUtils.toFloat(WebUtils.optional(request, "protectThreshold", "0.0"));
        boolean isUseSpecifiedURL = Boolean.parseBoolean(WebUtils.optional(request, "isUseSpecifiedURL", "false"));
        String envAndSite = WebUtils.optional(request, "envAndSites", StringUtils.EMPTY);
        boolean resetWeight = Boolean.parseBoolean(WebUtils.optional(request, "resetWeight", "false"));

        boolean enableHealthCheck = Boolean.parseBoolean(WebUtils.optional(request, "enableHealthCheck",
            String.valueOf(switchDomain.getDefaultHealthCheckMode().equals(HealthCheckMode.server.name()))));

        boolean enable = Boolean.parseBoolean(WebUtils.optional(request, "serviceEnabled", "true"));

        String disabledSites = WebUtils.optional(request, "disabledSites", StringUtils.EMPTY);
        boolean eanbleClientBeat = Boolean.parseBoolean(WebUtils.optional(request, "enableClientBeat",
            String.valueOf(switchDomain.getDefaultHealthCheckMode().equals(HealthCheckMode.client.name()))));

        String clusterName = WebUtils.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);

        String serviceMetadataJson = WebUtils.optional(request, "serviceMetadata", StringUtils.EMPTY);
        String clusterMetadataJson = WebUtils.optional(request, "clusterMetadata", StringUtils.EMPTY);

        Loggers.SRV_LOG.info("[RESET-WEIGHT] {}", String.valueOf(resetWeight));

        VirtualClusterDomain domObj = new VirtualClusterDomain();
        domObj.setName(dom);
        domObj.setNamespaceId(namespaceId);
        domObj.setToken(token);
        domObj.setOwners(Arrays.asList(owners.split(",")));
        domObj.setProtectThreshold(protectThreshold);
        domObj.setUseSpecifiedURL(isUseSpecifiedURL);
        domObj.setResetWeight(resetWeight);
        domObj.setEnableHealthCheck(enableHealthCheck);
        domObj.setEnabled(enable);
        domObj.setEnableClientBeat(eanbleClientBeat);

        if (StringUtils.isNotEmpty(serviceMetadataJson)) {
            domObj.setMetadata(JSON.parseObject(serviceMetadataJson, new TypeReference<Map<String, String>>() {
            }));
        }

        if (StringUtils.isNotEmpty(envAndSite) && StringUtils.isNotEmpty(disabledSites)) {
            throw new IllegalArgumentException("envAndSite and disabledSites are not allowed both not empty.");
        }

        String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(clusters)) {
            // new format
            List<Cluster> clusterObjs = JSON.parseArray(clusters, Cluster.class);

            for (Cluster cluster : clusterObjs) {
                domObj.getClusterMap().put(cluster.getName(), cluster);
            }
        } else {
            // old format, default cluster will be constructed automatically
            String cktype = WebUtils.optional(request, "cktype", "TCP");
            String ipPort4Check = WebUtils.optional(request, "ipPort4Check", "true");
            String nodegroup = WebUtils.optional(request, "nodegroup", StringUtils.EMPTY);

            int defIPPort = NumberUtils.toInt(WebUtils.optional(request, "defIPPort", "-1"));
            int defCkport = NumberUtils.toInt(WebUtils.optional(request, "defCkport", "80"));

            Cluster cluster = new Cluster();
            cluster.setName(clusterName);

            cluster.setLegacySyncConfig(nodegroup);

            cluster.setUseIPPort4Check(Boolean.parseBoolean(ipPort4Check));
            cluster.setDefIPPort(defIPPort);
            cluster.setDefCkport(defCkport);

            if (StringUtils.isNotEmpty(clusterMetadataJson)) {
                cluster.setMetadata(JSON.parseObject(clusterMetadataJson, new TypeReference<Map<String, String>>() {
                }));
            }

            if (AbstractHealthChecker.Tcp.TYPE.equals(cktype)) {
                AbstractHealthChecker.Tcp config = new AbstractHealthChecker.Tcp();
                cluster.setHealthChecker(config);
            } else if (AbstractHealthChecker.Http.TYPE.equals(cktype)) {

                String path = WebUtils.optional(request, "path", StringUtils.EMPTY);
                String headers = WebUtils.optional(request, "headers", StringUtils.EMPTY);
                String expectedResponseCode = WebUtils.optional(request, "expectedResponseCode", "200");

                AbstractHealthChecker.Http config = new AbstractHealthChecker.Http();
                config.setType(cktype);
                config.setPath(path);
                config.setHeaders(headers);
                config.setExpectedResponseCode(Integer.parseInt(expectedResponseCode));
                cluster.setHealthChecker(config);

            } else if (AbstractHealthChecker.Mysql.TYPE.equals(cktype)) {

                AbstractHealthChecker.Mysql config = new AbstractHealthChecker.Mysql();
                String user = WebUtils.optional(request, "user", StringUtils.EMPTY);
                String pwd = WebUtils.optional(request, "pwd", StringUtils.EMPTY);
                String cmd = WebUtils.optional(request, "cmd", StringUtils.EMPTY);
                config.setUser(user);
                config.setPwd(pwd);
                config.setCmd(cmd);
                cluster.setHealthChecker(config);
            }

            domObj.getClusterMap().put(clusterName, cluster);
        }

        // now valid the dom. if failed, exception will be thrown
        domObj.setLastModifiedMillis(System.currentTimeMillis());
        domObj.recalculateChecksum();
        domObj.valid();

        serviceManager.addOrReplaceService(domObj);

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

    @RequestMapping("/deRegService")
    public String deRegService(HttpServletRequest request) throws Exception {
        IpAddress ipAddress = getIPAddress(request);
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) serviceManager.getService(namespaceId, serviceName);
        if (virtualClusterDomain == null) {
            return "ok";
        }

        serviceManager.removeInstance(namespaceId, serviceName, ipAddress);

        return "ok";
    }

    @RequestMapping("/regService")
    public String regService(HttpServletRequest request) throws Exception {

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

    @NeedAuth
    @RequestMapping("/updateDom")
    public String updateDom(HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String name = WebUtils.required(request, "dom");
        VirtualClusterDomain dom = (VirtualClusterDomain) serviceManager.getService(namespaceId, name);
        if (dom == null) {
            throw new IllegalStateException("dom not found");
        }

        String owners = WebUtils.optional(request, "owners", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(owners)) {
            dom.setOwners(Arrays.asList(owners.split(",")));
        }

        String token = WebUtils.optional(request, "newToken", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(token)) {
            dom.setToken(token);
        }

        String enableClientBeat = WebUtils.optional(request, "enableClientBeat", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(enableClientBeat)) {
            dom.setEnableClientBeat(Boolean.parseBoolean(enableClientBeat));
        }

        String protectThreshold = WebUtils.optional(request, "protectThreshold", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(protectThreshold)) {
            dom.setProtectThreshold(Float.parseFloat(protectThreshold));
        }

        String sitegroup = WebUtils.optional(request, "sitegroup", StringUtils.EMPTY);
        String setSiteGroupForce = WebUtils.optional(request, "setSiteGroupForce", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(sitegroup) || !StringUtils.isEmpty(setSiteGroupForce)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setSitegroup(sitegroup);
        }

        String cktype = WebUtils.optional(request, "cktype", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(cktype)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            if (cktype.equals(HealthCheckType.HTTP.name().toLowerCase())) {
                AbstractHealthChecker.Http config = new AbstractHealthChecker.Http();
                config.setType(cktype);
                config.setPath(WebUtils.required(request, "path"));
                cluster.setHealthChecker(config);
            } else if (cktype.equals(HealthCheckType.TCP.name().toLowerCase())) {
                AbstractHealthChecker.Tcp config = new AbstractHealthChecker.Tcp();
                config.setType(cktype);
                cluster.setHealthChecker(config);
            } else if (cktype.equals(HealthCheckType.MYSQL.name().toLowerCase())) {
                AbstractHealthChecker.Mysql config = new AbstractHealthChecker.Mysql();
                config.setCmd(WebUtils.required(request, "cmd"));
                config.setPwd(WebUtils.required(request, "pwd"));
                config.setUser(WebUtils.required(request, "user"));
                cluster.setHealthChecker(config);
            } else {
                throw new IllegalArgumentException("unsupported health check type: " + cktype);
            }

        }

        String defIPPort = WebUtils.optional(request, "defIPPort", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(defIPPort)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setDefIPPort(Integer.parseInt(defIPPort));
        }

        String submask = WebUtils.optional(request, "submask", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(submask)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setSubmask(submask);
        }

        String ipPort4Check = WebUtils.optional(request, "ipPort4Check", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(ipPort4Check)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setUseIPPort4Check(Boolean.parseBoolean(ipPort4Check));
        }

        String defCkPort = WebUtils.optional(request, "defCkPort", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(defCkPort)) {
            Cluster cluster
                = dom.getClusterMap().get(WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setDefCkport(Integer.parseInt(defCkPort));
        }

        String useSpecifiedUrl = WebUtils.optional(request, "useSpecifiedURL", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(useSpecifiedUrl)) {
            dom.setUseSpecifiedURL(Boolean.parseBoolean(useSpecifiedUrl));
        }

        String resetWeight = WebUtils.optional(request, "resetWeight", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(resetWeight)) {
            dom.setResetWeight(Boolean.parseBoolean(resetWeight));
        }

        String enableHealthCheck = WebUtils.optional(request, "enableHealthCheck", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(enableHealthCheck)) {
            dom.setEnableHealthCheck(Boolean.parseBoolean(enableHealthCheck));
        }

        String enabled = WebUtils.optional(request, "serviceEnabled", "true");
        if (!StringUtils.isEmpty(enabled)) {
            dom.setEnabled(Boolean.parseBoolean(enabled));
        }

        String ipDeletedTimeout = WebUtils.optional(request, "ipDeletedTimeout", "-1");

        if (!StringUtils.isNotEmpty(ipDeletedTimeout)) {
            long timeout = Long.parseLong(ipDeletedTimeout);
            if (timeout < VirtualClusterDomain.MINIMUM_IP_DELETE_TIMEOUT) {
                throw new IllegalArgumentException("ipDeletedTimeout is too short: " + timeout + ", better longer than 60000");
            }

            dom.setIpDeleteTimeout(timeout);
        }

        // now do the validation
        dom.setLastModifiedMillis(System.currentTimeMillis());
        dom.recalculateChecksum();
        dom.valid();

        serviceManager.addOrReplaceService(dom);

        return "ok";
    }

    @RequestMapping("/hello")
    public JSONObject hello(HttpServletRequest request) {
        JSONObject result = new JSONObject();
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total services: raft " + serviceManager.getDomCount()
            + ", local port:" + RunningConfig.getServerPort());
        return result;
    }

    @NeedAuth
    @RequestMapping("/remvDom")
    public String remvDom(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        if (serviceManager.getService(namespaceId, dom) == null) {
            throw new IllegalStateException("specified domain doesn't exists.");
        }

        serviceManager.easyRemoveDom(namespaceId, dom);

        return "ok";
    }

    @RequestMapping("/getDomsByIP")
    public JSONObject getDomsByIP(HttpServletRequest request) {
        String ip = WebUtils.required(request, "ip");

        Set<String> doms = new HashSet<String>();
        Map<String, Set<String>> domMap = serviceManager.getAllDomNames();

        for (String namespaceId : domMap.keySet()) {
            for (String dom : domMap.get(namespaceId)) {
                Domain domObj = serviceManager.getService(namespaceId, dom);
                List<IpAddress> ipObjs = domObj.allIPs();
                for (IpAddress ipObj : ipObjs) {
                    if (ip.contains(":")) {
                        if (StringUtils.equals(ipObj.getIp() + ":" + ipObj.getPort(), ip)) {
                            doms.add(namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + domObj.getName());
                        }
                    } else {
                        if (StringUtils.equals(ipObj.getIp(), ip)) {
                            doms.add(namespaceId + UtilsAndCommons.SERVICE_GROUP_CONNECTOR + domObj.getName());
                        }
                    }
                }
            }
        }

        JSONObject result = new JSONObject();

        result.put("doms", doms);

        return result;
    }

    @NeedAuth
    @RequestMapping("/addIP4Dom")
    public String addIP4Dom(HttpServletRequest request) throws Exception {

        if (switchDomain.isDisableAddIP()) {
            throw new AccessControlException("Adding IP for dom is forbidden now.");
        }


        Map<String, String> proxyParams = new HashMap<>(16);
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            proxyParams.put(entry.getKey(), entry.getValue()[0]);
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[ADD-IP] full arguments: {}", proxyParams);
        }

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);

        String clusterName = WebUtils.required(request, "clusterName");

        String ipListString = WebUtils.required(request, "ipList");
        final List<String> ipList;
        List<IpAddress> newIPs = new ArrayList<>();

        if (Boolean.parseBoolean(WebUtils.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            newIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                IpAddress ipAddr = IpAddress.fromJSON(ip);
                newIPs.add(ipAddr);
            }
        }

        serviceManager.addInstance(namespaceId, serviceName, clusterName, newIPs.toArray(new IpAddress[newIPs.size()]));

        return "ok";
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

    @RequestMapping("/srvIPXT")
    @ResponseBody
    public JSONObject srvIPXT(HttpServletRequest request) throws Exception {

        if (NetUtils.localServer().equals(UtilsAndCommons.LOCAL_HOST_IP)) {
            throw new Exception("invalid localhost ip: " + NetUtils.localServer());
        }

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        String dom = WebUtils.required(request, "dom");
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

    @NeedAuth
    @RequestMapping("/remvIP4Dom")
    public String remvIP4Dom(HttpServletRequest request) throws Exception {

        if (switchDomain.isDisableAddIP()) {
            throw new AccessControlException("Adding IP for dom is forbidden now.");
        }

        Map<String, String> proxyParams = new HashMap<>(16);
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            proxyParams.put(entry.getKey(), entry.getValue()[0]);
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[REMOVE-IP] full arguments: {}", proxyParams);
        }

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);

        String ipListString = WebUtils.required(request, "ipList");
        final List<String> ipList;
        List<IpAddress> removedIPs = new ArrayList<>();

        if (Boolean.parseBoolean(WebUtils.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            removedIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                IpAddress ipAddr = IpAddress.fromJSON(ip);
                removedIPs.add(ipAddr);
            }
        }

        serviceManager.removeInstance(namespaceId, serviceName, removedIPs.toArray(new IpAddress[removedIPs.size()]));

        return "ok";
    }

    @RequestMapping("/pushState")
    public JSONObject pushState(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        boolean detail = Boolean.parseBoolean(WebUtils.optional(request, "detail", "false"));
        boolean reset = Boolean.parseBoolean(WebUtils.optional(request, "reset", "false"));

        List<PushService.Receiver.AckEntry> failedPushes = PushService.getFailedPushes();
        int failedPushCount = pushService.getFailedPushCount();
        result.put("succeed", pushService.getTotalPush() - failedPushCount);
        result.put("total", pushService.getTotalPush());

        if (pushService.getTotalPush() > 0) {
            result.put("ratio", ((float) pushService.getTotalPush() - failedPushCount) / pushService.getTotalPush());
        } else {
            result.put("ratio", 0);
        }

        JSONArray dataArray = new JSONArray();
        if (detail) {
            for (PushService.Receiver.AckEntry entry : failedPushes) {
                try {
                    dataArray.add(new String(entry.origin.getData(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    dataArray.add("[encoding failure]");
                }
            }
            result.put("data", dataArray);
        }

        if (reset) {
            PushService.resetPushState();
        }

        result.put("reset", reset);

        return result;
    }

    @NeedAuth
    @RequestMapping("/updateSwitch")
    public String updateSwitch(HttpServletRequest request) throws Exception {
        Boolean debug = Boolean.parseBoolean(WebUtils.optional(request, "debug", "false"));
        String entry = WebUtils.required(request, "entry");
        String value = WebUtils.required(request, "value");

        switchManager.update(entry, value, debug);

        return "ok";
    }

    public void checkIfDisabled(VirtualClusterDomain domObj) throws Exception {
        if (!domObj.getEnabled()) {
            throw new Exception("domain is disabled now.");
        }
    }

    @RequestMapping("/switches")
    public JSONObject switches(HttpServletRequest request) {
        return JSON.parseObject(switchDomain.toJSON());
    }

    @RequestMapping("/getVersion")
    public JSONObject getVersion(HttpServletRequest request) throws IOException {

        JSONObject result = new JSONObject();
        InputStream is = ApiCommands.class.getClassLoader().getResourceAsStream("application.properties");
        Properties properties = new Properties();
        properties.load(is);

        try (InputStreamReader releaseNode =
                 new InputStreamReader(ApiCommands.class.getClassLoader().getResourceAsStream("changelog.properties"), "UTF-8")) {

            Properties properties1 = new Properties();
            properties1.load(releaseNode);

            result.put("server version", properties.getProperty("version"));
            result.put("change log", properties1.getProperty(properties.getProperty("version")));
        }
        return result;
    }

    @RequestMapping("/getAllChangeLog")
    public JSONObject getAllChangeLog(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();
        try (InputStreamReader releaseNode =
                 new InputStreamReader(ApiCommands.class.getClassLoader().getResourceAsStream("changelog.properties"), "UTF-8")) {

            Properties properties1 = new Properties();
            properties1.load(releaseNode);

            for (String name : properties1.stringPropertyNames()) {
                result.put(name, properties1.getProperty(name));
            }
        }

        return result;
    }

    @RequestMapping("/allDomNames")
    public JSONObject allDomNames(HttpServletRequest request) throws Exception {

        boolean responsibleOnly = Boolean.parseBoolean(WebUtils.optional(request, "responsibleOnly", "false"));

        Map<String, Set<String>> doms = new HashMap<>(16);

        Map<String, Set<String>> domMap = serviceManager.getAllDomNames();

        for (String namespaceId : domMap.keySet()) {
            doms.put(namespaceId, new HashSet<>());
            for (String dom : domMap.get(namespaceId)) {
                if (distroMapper.responsible(dom) || !responsibleOnly) {
                    doms.get(namespaceId).add(dom);
                }
            }
        }

        JSONObject result = new JSONObject();

        result.put("doms", doms);
        result.put("count", doms.size());

        return result;
    }

    @RequestMapping("/searchDom")
    public JSONObject searchDom(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String expr = WebUtils.required(request, "expr");

        List<VirtualClusterDomain> doms
            = serviceManager.searchDomains(namespaceId, ".*" + expr + ".*");

        if (CollectionUtils.isEmpty(doms)) {
            result.put("doms", Collections.emptyList());
            return result;
        }

        JSONArray domArray = new JSONArray();
        for (Domain dom : doms) {
            domArray.add(dom.getName());
        }

        result.put("doms", domArray);

        return result;
    }


    private Cluster getClusterFromJson(String json) {
        JSONObject object = JSON.parseObject(json);
        String type = object.getJSONObject("healthChecker").getString("type");
        AbstractHealthChecker abstractHealthCheckConfig;

        if (type.equals(HealthCheckType.HTTP.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthChecker.Http.class);
        } else if (type.equals(HealthCheckType.TCP.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthChecker.Tcp.class);
        } else if (type.equals(HealthCheckType.MYSQL.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthChecker.Mysql.class);
        } else {
            throw new IllegalArgumentException("can not prase cluster from json: " + json);
        }

        Cluster cluster = JSON.parseObject(json, Cluster.class);

        cluster.setHealthChecker(abstractHealthCheckConfig);
        return cluster;
    }

    public String doAddCluster4Dom(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);
        String json = WebUtils.optional(request, "clusterJson", StringUtils.EMPTY);

        VirtualClusterDomain domObj = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);

        if (domObj == null) {
            throw new IllegalArgumentException("dom not found: " + dom);
        }

        Cluster cluster = new Cluster();

        if (!StringUtils.isEmpty(json)) {
            try {
                cluster = getClusterFromJson(json);

            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[ADD-CLUSTER] failed to parse json, try old format.");
            }
        } else {
            String cktype = WebUtils.optional(request, "cktype", "TCP");
            String clusterName = WebUtils.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
            String ipPort4Check = WebUtils.optional(request, "ipPort4Check", "true");
            String path = WebUtils.optional(request, "path", StringUtils.EMPTY);
            String headers = WebUtils.optional(request, "headers", StringUtils.EMPTY);
            String nodegroup = WebUtils.optional(request, "nodegroup", StringUtils.EMPTY);
            String expectedResponseCode = WebUtils.optional(request, "expectedResponseCode", "200");
            int defIPPort = NumberUtils.toInt(WebUtils.optional(request, "defIPPort", "-1"));
            int defCkport = NumberUtils.toInt(WebUtils.optional(request, "defCkport", "80"));
            String siteGroup = WebUtils.optional(request, "siteGroup", StringUtils.EMPTY);
            String submask = WebUtils.optional(request, "submask", StringUtils.EMPTY);
            String clusterMetadataJson = WebUtils.optional(request, "clusterMetadata", StringUtils.EMPTY);
            cluster.setName(clusterName);

            cluster.setLegacySyncConfig(nodegroup);

            cluster.setUseIPPort4Check(Boolean.parseBoolean(ipPort4Check));
            cluster.setDefIPPort(defIPPort);
            cluster.setDefCkport(defCkport);

            if (StringUtils.isNotEmpty(clusterMetadataJson)) {
                cluster.setMetadata(JSON.parseObject(clusterMetadataJson, new TypeReference<Map<String, String>>() {
                }));
            }

            if (StringUtils.equals(cktype, HealthCheckType.HTTP.name())) {
                AbstractHealthChecker.Http config = new AbstractHealthChecker.Http();
                config.setType(cktype);
                config.setPath(path);
                config.setHeaders(headers);
                config.setExpectedResponseCode(Integer.parseInt(expectedResponseCode));
                cluster.setHealthChecker(config);
            } else if (StringUtils.equals(cktype, HealthCheckType.TCP.name())) {
                AbstractHealthChecker.Tcp config = new AbstractHealthChecker.Tcp();
                config.setType(cktype);
                cluster.setHealthChecker(config);
            } else if (StringUtils.equals(cktype, HealthCheckType.MYSQL.name())) {
                AbstractHealthChecker.Mysql config = new AbstractHealthChecker.Mysql();
                String cmd = WebUtils.required(request, "cmd");
                String pwd = WebUtils.required(request, "pwd");
                String user = WebUtils.required(request, "user");

                config.setType(cktype);
                config.setCmd(cmd);
                config.setPwd(pwd);
                config.setUser(user);
                cluster.setHealthChecker(config);
            }
            cluster.setSitegroup(siteGroup);

            if (!StringUtils.isEmpty(submask)) {
                cluster.setSubmask(submask);
            }
        }
        cluster.setDom(domObj);
        cluster.init();

        if (domObj.getClusterMap().containsKey(cluster.getName())) {
            domObj.getClusterMap().get(cluster.getName()).update(cluster);
        } else {
            domObj.getClusterMap().put(cluster.getName(), cluster);
        }

        domObj.setLastModifiedMillis(System.currentTimeMillis());
        domObj.recalculateChecksum();
        domObj.valid();

        serviceManager.addOrReplaceService(domObj);

        return "ok";
    }

    @NeedAuth
    @RequestMapping("/addCluster4Dom")
    public String addCluster4Dom(HttpServletRequest request) throws Exception {
        return doAddCluster4Dom(request);
    }


    @RequestMapping("/distroStatus")
    public JSONObject distroStatus(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        String action = WebUtils.optional(request, "action", "view");

        if (StringUtils.equals(SwitchEntry.ACTION_VIEW, action)) {
            result.put("status", distroMapper.getDistroConfig());
            return result;
        }

        if (StringUtils.equals(SwitchEntry.ACTION_CLEAN, action)) {
            distroMapper.clean();
            return result;
        }

        return result;
    }

    @RequestMapping("/metrics")
    public JSONObject metrics(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        int domCount = serviceManager.getDomCount();
        int ipCount = serviceManager.getInstanceCount();

        int responsibleDomCount = serviceManager.getResponsibleDomCount();
        int responsibleIPCount = serviceManager.getResponsibleIPCount();

        result.put("domCount", domCount);
        result.put("ipCount", ipCount);
        result.put("responsibleDomCount", responsibleDomCount);
        result.put("responsibleIPCount", responsibleIPCount);
        result.put("cpu", SystemUtils.getCPU());
        result.put("load", SystemUtils.getLoad());
        result.put("mem", SystemUtils.getMem());

        return result;
    }

    @RequestMapping("/updateClusterConf")
    public JSONObject updateClusterConf(HttpServletRequest request) throws IOException {

        JSONObject result = new JSONObject();

        String ipSpliter = ",";

        String ips = WebUtils.optional(request, "ips", "");
        String action = WebUtils.required(request, "action");

        if (SwitchEntry.ACTION_ADD.equals(action)) {

            List<String> oldList = readClusterConf();
            StringBuilder sb = new StringBuilder();
            for (String ip : oldList) {
                sb.append(ip).append("\r\n");
            }
            for (String ip : ips.split(ipSpliter)) {
                sb.append(ip).append("\r\n");
            }

            Loggers.SRV_LOG.info("[UPDATE-CLUSTER] new ips: {}", sb.toString());
            writeClusterConf(sb.toString());
            return result;
        }

        if (SwitchEntry.ACTION_REPLACE.equals(action)) {

            StringBuilder sb = new StringBuilder();
            for (String ip : ips.split(ipSpliter)) {
                sb.append(ip).append("\r\n");
            }
            Loggers.SRV_LOG.info("[UPDATE-CLUSTER] new ips: {}", sb.toString());
            writeClusterConf(sb.toString());
            return result;
        }

        if (SwitchEntry.ACTION_DELETE.equals(action)) {

            Set<String> removeIps = new HashSet<>();
            for (String ip : ips.split(ipSpliter)) {
                removeIps.add(ip);
            }

            List<String> oldList = readClusterConf();

            Iterator<String> iterator = oldList.iterator();

            while (iterator.hasNext()) {

                String ip = iterator.next();
                if (removeIps.contains(ip)) {
                    iterator.remove();
                }
            }

            StringBuilder sb = new StringBuilder();
            for (String ip : oldList) {
                sb.append(ip).append("\r\n");
            }

            writeClusterConf(sb.toString());

            return result;
        }

        if (SwitchEntry.ACTION_VIEW.equals(action)) {

            List<String> oldList = readClusterConf();
            result.put("list", oldList);

            return result;
        }

        throw new InvalidParameterException("action is not qualified, action: " + action);

    }

    @RequestMapping("/serverStatus")
    public String serverStatus(HttpServletRequest request) {
        String serverStatus = WebUtils.required(request, "serverStatus");
        distroMapper.onReceiveServerStatus(serverStatus);

        return "ok";
    }

    @RequestMapping("/reCalculateCheckSum4Dom")
    public JSONObject reCalculateCheckSum4Dom(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        virtualClusterDomain.recalculateChecksum();

        JSONObject result = new JSONObject();

        result.put("checksum", virtualClusterDomain.getChecksum());

        return result;
    }

    @RequestMapping("/getResponsibleServer4Dom")
    public JSONObject getResponsibleServer4Dom(HttpServletRequest request) {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsibleServer", distroMapper.mapSrv(dom));

        return result;
    }

    @RequestMapping("/getHealthyServerList")
    public JSONObject getHealthyServerList(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("healthyList", distroMapper.getHealthyList());

        return result;
    }

    @RequestMapping("/responsible")
    public JSONObject responsible(HttpServletRequest request) {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) serviceManager.getService(namespaceId, dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsible", distroMapper.responsible(dom));

        return result;
    }

    @RequestMapping("/domStatus")
    public String domStatus(HttpServletRequest request) {
        //format: dom1@@checksum@@@dom2@@checksum
        String domsStatusString = WebUtils.required(request, "domsStatus");
        String serverIP = WebUtils.optional(request, "clientIP", "");

        if (!serverListManager.contains(serverIP)) {
            throw new IllegalArgumentException("ip: " + serverIP + " is not in serverlist");
        }

        try {
            ServiceManager.DomainChecksum checksums = JSON.parseObject(domsStatusString, ServiceManager.DomainChecksum.class);
            if (checksums == null) {
                Loggers.SRV_LOG.warn("[DOMAIN-STATUS] receive malformed data: null");
                return "fail";
            }

            for (Map.Entry<String, String> entry : checksums.domName2Checksum.entrySet()) {
                if (entry == null || StringUtils.isEmpty(entry.getKey()) || StringUtils.isEmpty(entry.getValue())) {
                    continue;
                }
                String dom = entry.getKey();
                String checksum = entry.getValue();
                Domain domain = serviceManager.getService(checksums.namespaceId, dom);

                if (domain == null) {
                    continue;
                }

                domain.recalculateChecksum();

                if (!checksum.equals(domain.getChecksum())) {
                    if (Loggers.SRV_LOG.isDebugEnabled()) {
                        Loggers.SRV_LOG.debug("checksum of {} is not consistent, remote: {}, checksum: {}, local: {}",
                            dom, serverIP, checksum, domain.getChecksum());
                    }
                    serviceManager.addUpdatedDom2Queue(checksums.namespaceId, dom, serverIP, checksum);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[DOMAIN-STATUS] receive malformed data: " + domsStatusString, e);
        }

        return "ok";
    }

    @RequestMapping("/containerNotify")
    public String containerNotify(HttpServletRequest request) {

        String type = WebUtils.required(request, "type");
        String domain = WebUtils.required(request, "domain");
        String ip = WebUtils.required(request, "ip");
        String port = WebUtils.required(request, "port");
        String state = WebUtils.optional(request, "state", StringUtils.EMPTY);

        Loggers.SRV_LOG.info("[CONTAINER_NOTFY] received notify event, type: {}, domain: {}, ip: {}, port: {}, state: {}",
            type, domain, ip, port, state);

        return "ok";
    }

    private JSONObject toPacket(Domain dom) {

        JSONObject pac = new JSONObject();

        VirtualClusterDomain vDom = (VirtualClusterDomain) dom;

        pac.put("name", vDom.getName());

        List<IpAddress> ips = vDom.allIPs();
        int invalidIPCount = 0;
        int ipCount = 0;
        for (IpAddress ip : ips) {
            if (!ip.isValid()) {
                invalidIPCount++;
            }

            ipCount++;
        }

        pac.put("ipCount", ipCount);
        pac.put("invalidIPCount", invalidIPCount);

        pac.put("owners", vDom.getOwners());
        pac.put("token", vDom.getToken());
        pac.put("checkServer", distroMapper.mapSrvName(vDom.getName()));

        pac.put("protectThreshold", vDom.getProtectThreshold());
        pac.put("checksum", vDom.getChecksum());
        pac.put("useSpecifiedURL", vDom.isUseSpecifiedURL());
        pac.put("enableClientBeat", vDom.getEnableClientBeat());

        Date date = new Date(vDom.getLastModifiedMillis());
        pac.put("lastModifiedTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
        pac.put("resetWeight", vDom.getResetWeight());
        pac.put("enableHealthCheck", vDom.getEnableHealthCheck());
        pac.put("enable", vDom.getEnabled());

        int totalCkRTMillis = 0;
        int validCkRTCount = 0;

        JSONArray clusters = new JSONArray();

        for (Map.Entry<String, Cluster> entry : vDom.getClusterMap().entrySet()) {
            Cluster cluster = entry.getValue();

            JSONObject clusterPac = new JSONObject();
            clusterPac.put("name", cluster.getName());
            clusterPac.put("healthChecker", cluster.getHealthChecker());
            clusterPac.put("defCkport", cluster.getDefCkport());
            clusterPac.put("defIPPort", cluster.getDefIPPort());
            clusterPac.put("useIPPort4Check", cluster.isUseIPPort4Check());
            clusterPac.put("submask", cluster.getSubmask());
            clusterPac.put("sitegroup", cluster.getSitegroup());
            clusterPac.put("metadatas", cluster.getMetadata());

            if (cluster.getHealthCheckTask() != null) {
                clusterPac.put("ckRTMillis", cluster.getHealthCheckTask().getCheckRTNormalized());

                // if there is no IP, the check rt doesn't make sense
                if (cluster.allIPs().size() > 0) {
                    totalCkRTMillis += cluster.getHealthCheckTask().getCheckRTNormalized();
                    validCkRTCount++;
                }
            }

            clusters.add(clusterPac);
        }

        pac.put("clusters", clusters);

        if (totalCkRTMillis > 0) {
            pac.put("avgCkRTMillis", totalCkRTMillis / validCkRTCount);
        } else {
            pac.put("avgCkRTMillis", 0);
        }

        return pac;
    }

    private List<IpAddress> getIpAddresses(HttpServletRequest request) {
        String ipListString = WebUtils.required(request, "ipList");
        final List<String> ipList;
        List<IpAddress> newIPs = new ArrayList<>();

        if (Boolean.parseBoolean(WebUtils.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            newIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                IpAddress ipAddr = IpAddress.fromJSON(ip);
                if (ipAddr == null) {
                    throw new IllegalArgumentException("malformed ip ->" + ip);
                }

                newIPs.add(ipAddr);
            }
        }

        return newIPs;
    }

    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

}
