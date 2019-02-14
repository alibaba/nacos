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
import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.*;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.raft.Datum;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.raft.RaftPeer;
import com.alibaba.nacos.naming.raft.RaftProxy;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.catalina.util.ParameterMap;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    protected DomainsManager domainsManager;

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
            result.put("cacheMillis", Switch.getPushCacheMillis(client.getDom()));

            return result.toJSONString();
        }
    };


    @RequestMapping("/dom")
    public JSONObject dom(HttpServletRequest request) throws NacosException {
        // SDK before version 2.0,0 use 'name' instead of 'dom' here
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String name = WebUtils.required(request, "dom");

        Domain dom = domainsManager.getDomain(namespaceId, name);
        if (dom == null) {
            throw new NacosException(NacosException.NOT_FOUND, "Dom doesn't exist");
        }

        return toPacket(dom);
    }

    @RequestMapping("/domCount")
    public JSONObject domCount(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("count", domainsManager.getDomCount());

        return result;
    }

    @RequestMapping("/rt4Dom")
    public JSONObject rt4Dom(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");

        VirtualClusterDomain domObj
            = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);
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

        String key = WebUtils.required(request, "dom");

        String domName;

        String namespaceId;

        if (key.contains(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)) {
            namespaceId = key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[0];
            domName = key.split(UtilsAndCommons.SERVICE_GROUP_CONNECTOR)[1];
        } else {
            namespaceId = UtilsAndCommons.getDefaultNamespaceId();
            domName = key;
        }

        VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, domName);

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

            VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, domName);

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
        if (domainsManager.getDomain(namespaceId, dom) != null) {
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
        String dom = WebUtils.optional(request, "serviceName", StringUtils.EMPTY);
        if (StringUtils.isBlank(dom)) {
            dom = WebUtils.required(request, "dom");
        }
        String app;
        app = WebUtils.optional(request, "app", StringUtils.EMPTY);
        String clusterName = clientBeat.getCluster();

        if (StringUtils.isBlank(clusterName)) {
            clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[CLIENT-BEAT] full arguments: beat: {}, serviceName: {}", clientBeat, dom);
        }

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);
        Map<String, String[]> stringMap = new HashMap<>(16);
        stringMap.put(Constants.REQUEST_PARAM_SERVICE_NAME, Arrays.asList(dom).toArray(new String[1]));
        stringMap.put("enableClientBeat", Arrays.asList("true").toArray(new String[1]));
        stringMap.put("cktype", Arrays.asList("TCP").toArray(new String[1]));
        stringMap.put("appName", Arrays.asList(app).toArray(new String[1]));
        stringMap.put("clusterName", Arrays.asList(clusterName).toArray(new String[1]));

        //if domain does not exist, register it.
        if (virtualClusterDomain == null) {
            regDom(OverrideParameterRequestWrapper.buildRequest(request, stringMap));
            Loggers.SRV_LOG.warn("dom not found, register it, dom: {}", dom);
        }

        virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        String ip = clientBeat.getIp();
        int port = clientBeat.getPort();

        IpAddress ipAddress = new IpAddress();
        ipAddress.setPort(port);
        ipAddress.setIp(ip);
        ipAddress.setWeight(clientBeat.getWeight());
        ipAddress.setMetadata(clientBeat.getMetadata());
        ipAddress.setClusterName(clusterName);
        ipAddress.setServiceName(dom);
        ipAddress.setInstanceId(ipAddress.generateInstanceId());

        if (!virtualClusterDomain.getClusterMap().containsKey(ipAddress.getClusterName())) {
            doAddCluster4Dom(OverrideParameterRequestWrapper.buildRequest(request, stringMap));
        }

        JSONObject result = new JSONObject();

        result.put("clientBeatInterval", Switch.getClientBeatInterval());

        if (!virtualClusterDomain.allIPs().contains(ipAddress)) {

            if (!virtualClusterDomain.getEnableClientBeat()) {
                return result;
            }

            stringMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
            stringMap.put("json", Arrays.asList("true").toArray(new String[1]));
            stringMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
            addIP4Dom(OverrideParameterRequestWrapper.buildRequest(request, stringMap));
            Loggers.SRV_LOG.warn("ip not found, register it, dom: {}, ip: {}", dom, ipAddress);
        }

        if (!DistroMapper.responsible(dom)) {
            String server = DistroMapper.mapSrv(dom);
            Loggers.EVT_LOG.info("I'm not responsible for {}, proxy it to {}", dom, server);
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
            String.valueOf(Switch.getDefaultHealthCheckMode().equals(HealthCheckMode.server.name()))));

        boolean enable = Boolean.parseBoolean(WebUtils.optional(request, "serviceEnabled", "true"));

        String disabledSites = WebUtils.optional(request, "disabledSites", StringUtils.EMPTY);
        boolean eanbleClientBeat = Boolean.parseBoolean(WebUtils.optional(request, "enableClientBeat",
            String.valueOf(Switch.getDefaultHealthCheckMode().equals(HealthCheckMode.client.name()))));

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

        domainsManager.easyAddOrReplaceDom(domObj);

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
        String dom = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);
        if (virtualClusterDomain == null) {
            return "ok";
        }

        ParameterMap<String, String[]> parameterMap = new ParameterMap<>();
        parameterMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
        parameterMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
        parameterMap.put("json", Arrays.asList("true").toArray(new String[1]));
        parameterMap.put("token", Arrays.asList(virtualClusterDomain.getToken()).toArray(new String[1]));

        return remvIP4Dom(OverrideParameterRequestWrapper.buildRequest(request, parameterMap));

    }

    @SuppressFBWarnings("JLM_JSR166_LOCK_MONITORENTER")
    @RequestMapping("/regService")
    public String regService(HttpServletRequest request) throws Exception {

        String dom = WebUtils.required(request, "serviceName");
        String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);
        String app = WebUtils.optional(request, "app", "DEFAULT");
        String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID, UtilsAndCommons.getDefaultNamespaceId());

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        IpAddress ipAddress = getIPAddress(request);
        ipAddress.setApp(app);
        ipAddress.setServiceName(dom);
        ipAddress.setInstanceId(ipAddress.generateInstanceId());
        ipAddress.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(metadata)) {
            ipAddress.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }

        if (virtualClusterDomain == null) {

            Lock lock = domainsManager.addLockIfAbsent(UtilsAndCommons.assembleFullServiceName(namespaceId, dom));
            Condition condition = domainsManager.addCondtion(UtilsAndCommons.assembleFullServiceName(namespaceId, dom));
            UtilsAndCommons.RAFT_PUBLISH_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        regDom(request);
                    } catch (Exception e) {
                        Loggers.SRV_LOG.error("[REG-SERIVCE] register service failed, service:" + dom, e);
                    }
                }
            });
            try {
                lock.lock();
                condition.await(5000, TimeUnit.MILLISECONDS);
            } finally {
                lock.unlock();
            }

            virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);
        }

        if (virtualClusterDomain != null) {

            if (!virtualClusterDomain.getClusterMap().containsKey(ipAddress.getClusterName())) {
                doAddCluster4Dom(request);
            }

            if (Loggers.SRV_LOG.isDebugEnabled()) {
                Loggers.SRV_LOG.debug("reg-service add ip: {}|{}", dom, ipAddress.toJSON());
            }

            Map<String, String[]> stringMap = new HashMap<>(16);
            stringMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
            stringMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
            stringMap.put("json", Arrays.asList("true").toArray(new String[1]));
            stringMap.put("token", Arrays.asList(virtualClusterDomain.getToken()).toArray(new String[1]));

            addIP4Dom(OverrideParameterRequestWrapper.buildRequest(request, stringMap));
        } else {
            throw new IllegalArgumentException("dom not found: " + dom);
        }

        return "ok";
    }


    @NeedAuth
    @RequestMapping("/updateDom")
    public String updateDom(HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String name = WebUtils.required(request, "dom");
        VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, name);
        if (dom == null) {
            throw new IllegalStateException("dom not found");
        }

        RaftPeer leader = RaftCore.getLeader();
        if (leader == null) {
            throw new IllegalStateException("not leader at present, cannot update");
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

            if (cktype.equals(AbstractHealthCheckProcessor.HTTP_PROCESSOR.getType())) {
                AbstractHealthChecker.Http config = new AbstractHealthChecker.Http();
                config.setType(cktype);
                config.setPath(WebUtils.required(request, "path"));
                cluster.setHealthChecker(config);
            } else if (cktype.equals(AbstractHealthCheckProcessor.TCP_PROCESSOR.getType())) {
                AbstractHealthChecker.Tcp config = new AbstractHealthChecker.Tcp();
                config.setType(cktype);
                cluster.setHealthChecker(config);
            } else if (cktype.equals(AbstractHealthCheckProcessor.MYSQL_PROCESSOR.getType())) {
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

        domainsManager.easyAddOrReplaceDom(dom);

        return "ok";
    }

    @RequestMapping("/hello")
    public JSONObject hello(HttpServletRequest request) {
        JSONObject result = new JSONObject();
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total services: raft " + domainsManager.getDomCount()
            + ", local port:" + RunningConfig.getServerPort());
        return result;
    }


    @NeedAuth
    @RequestMapping("/remvDom")
    public String remvDom(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        if (domainsManager.getDomain(namespaceId, dom) == null) {
            throw new IllegalStateException("specified domain doesn't exists.");
        }

        domainsManager.easyRemoveDom(namespaceId, dom);

        return "ok";
    }

    @RequestMapping("/getDomsByIP")
    public JSONObject getDomsByIP(HttpServletRequest request) {
        String ip = WebUtils.required(request, "ip");

        Set<String> doms = new HashSet<String>();
        Map<String, Set<String>> domMap = domainsManager.getAllDomNames();

        for (String namespaceId : domMap.keySet()) {
            for (String dom : domMap.get(namespaceId)) {
                Domain domObj = domainsManager.getDomain(namespaceId, dom);
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

    @RequestMapping("/onAddIP4Dom")
    public String onAddIP4Dom(HttpServletRequest request) throws Exception {
        if (Switch.getDisableAddIP()) {
            throw new AccessControlException("Adding IP for dom is forbidden now.");
        }

        String clientIP = WebUtils.required(request, "clientIP");

        long term = Long.parseLong(WebUtils.required(request, "term"));

        if (!RaftCore.isLeader(clientIP)) {
            Loggers.RAFT.warn("peer {} tried to publish data but wasn't leader, leader: {}",
                JSON.toJSONString(clientIP), JSON.toJSONString(RaftCore.getLeader()));
            throw new IllegalStateException("peer(" + clientIP + ") tried to publish " +
                "data but wasn't leader");
        }

        if (term < RaftCore.getPeerSet().local().term.get()) {
            Loggers.RAFT.warn("out of date publish, pub-term: {}, cur-term: {}",
                JSON.toJSONString(clientIP), JSON.toJSONString(RaftCore.getPeerSet().local()));
            throw new IllegalStateException("out of date publish, pub-term:"
                + term + ", cur-term: " + RaftCore.getPeerSet().local().term.get());
        }

        RaftCore.getPeerSet().local().resetLeaderDue();

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        final String dom = WebUtils.required(request, "dom");
        if (domainsManager.getDomain(namespaceId, dom) == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        boolean updateOnly = Boolean.parseBoolean(WebUtils.optional(request, "updateOnly", Boolean.FALSE.toString()));

        String ipListString = WebUtils.required(request, "ipList");
        List<IpAddress> newIPs = new ArrayList<>();

        List<String> ipList;
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

        if (CollectionUtils.isEmpty(newIPs)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        if (updateOnly) {
            //make sure every IP is in the dom, otherwise refuse update
            List<IpAddress> oldIPs = domainsManager.getDomain(namespaceId, dom).allIPs();
            Collection diff = CollectionUtils.subtract(newIPs, oldIPs);
            if (diff.size() != 0) {
                throw new IllegalArgumentException("these IPs are not present: " + Arrays.toString(diff.toArray())
                    + ", if you want to add them, remove updateOnly flag");
            }
        }
        domainsManager.easyAddIP4Dom(namespaceId, dom, newIPs, term);

        return "ok";
    }

    private void syncOnUpdateIP4Dom(String namespaceId, String dom, Map<String, String> proxyParams, String action) throws InterruptedException {

        String key = UtilsAndCommons.getIPListStoreKey(domainsManager.getDomain(namespaceId, dom));

        final CountDownLatch countDownLatch = new CountDownLatch(RaftCore.getPeerSet().majorityCount());
        updateIpPublish(proxyParams, countDownLatch, action);
        if (!countDownLatch.await(UtilsAndCommons.MAX_PUBLISH_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
            Loggers.RAFT.info("data publish failed, key=" + key, ",notify timeout.");
            throw new IllegalArgumentException("data publish failed, key=" + key);
        }
    }

    private void syncOnAddIP4Dom(String namespaceId, String dom, Map<String, String> proxyParams) throws InterruptedException {
        syncOnUpdateIP4Dom(namespaceId, dom, proxyParams, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD);
    }

    private void asyncOnAddIP4Dom(Map<String, String> proxyParams) {
        updateIpPublish(proxyParams, null, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD);
    }

    private void syncOnRemvIP4Dom(String namespaceId, String dom, Map<String, String> proxyParams) throws InterruptedException {
        syncOnUpdateIP4Dom(namespaceId, dom, proxyParams, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE);
    }

    private void asyncOnRemvIP4Dom(Map<String, String> proxyParams) {
        updateIpPublish(proxyParams, null, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE);
    }

    private void updateIpPublish(Map<String, String> proxyParams, CountDownLatch countDownLatch, String action) {

        for (final String peer : RaftCore.getPeerSet().allServersWithoutMySelf()) {

            UtilsAndCommons.RAFT_PUBLISH_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {

                    String server = peer;

                    if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                        server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
                    }

                    String api = action.equals("remove") ? "onRemvIP4Dom" : "onAddIP4Dom";

                    String url = "http://" + server
                        + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/" + api;

                    try {
                        HttpClient.asyncHttpPost(url, null, proxyParams, new AsyncCompletionHandler() {
                            @Override
                            public Integer onCompleted(Response response) throws Exception {
                                if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                    Loggers.SRV_LOG.warn("failed to add ip params: " + proxyParams
                                        + ",code: " + response.getStatusCode() + ", caused " + response.getResponseBody()
                                        + ", server: " + peer);
                                    return 1;
                                }
                                if (countDownLatch != null) {
                                    countDownLatch.countDown();
                                }
                                return 0;
                            }
                        });
                    } catch (Exception e) {
                        Loggers.SRV_LOG.error(action + "-IP", "failed when publish to peer." + url, e);
                    }
                }
            });
        }
    }

    @NeedAuth
    @RequestMapping("/addIP4Dom")
    public String addIP4Dom(HttpServletRequest request) throws Exception {

        if (Switch.getDisableAddIP()) {
            throw new AccessControlException("Adding IP for dom is forbidden now.");
        }

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        Map<String, String> proxyParams = new HashMap<>(16);
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            proxyParams.put(entry.getKey(), entry.getValue()[0]);
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[ADD-IP] full arguments: {}", proxyParams);
        }

        String ipListString = WebUtils.required(request, "ipList");
        final List<String> ipList;
        List<IpAddress> newIPs = new ArrayList<>();

        if (Boolean.parseBoolean(WebUtils.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            ipList = Arrays.asList(ipListString);
            newIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                IpAddress ipAddr = IpAddress.fromJSON(ip);
                newIPs.add(ipAddr);
            }
        }

        if (!RaftCore.isLeader()) {
            Loggers.RAFT.info("I'm not leader, will proxy to leader.");
            if (RaftCore.getLeader() == null) {
                throw new IllegalArgumentException("no leader now.");
            }

            RaftPeer leader = RaftCore.getLeader();

            String server = leader.ip;
            if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            String url = "http://" + server
                + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/addIP4Dom";
            HttpClient.HttpResult result1 = HttpClient.httpPost(url, null, proxyParams);

            if (result1.code != HttpURLConnection.HTTP_OK) {
                Loggers.SRV_LOG.warn("failed to add ip for dom, caused {}", result1.content);
                throw new IllegalArgumentException("failed to add ip for dom, caused " + result1.content);
            }

            return "ok";
        }

        final String dom = WebUtils.required(request, "dom");

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        if (domain == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        boolean updateOnly = Boolean.parseBoolean(WebUtils.optional(request, "updateOnly", "false"));

        if (CollectionUtils.isEmpty(newIPs)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        if (updateOnly) {
            //make sure every IP is in the dom, otherwise refuse update
            List<IpAddress> oldIPs = domain.allIPs();
            Collection diff = CollectionUtils.subtract(newIPs, oldIPs);
            if (diff.size() != 0) {
                throw new IllegalArgumentException("these IPs are not present: " + Arrays.toString(diff.toArray())
                    + ", if you want to add them, remove updateOnly flag");
            }
        }

        String key = UtilsAndCommons.getIPListStoreKey(domain);

        Datum datum = RaftCore.getDatum(key);
        if (datum == null) {
            try {
                domainsManager.getDom2LockMap().get(UtilsAndCommons.assembleFullServiceName(namespaceId, dom)).lock();
                datum = RaftCore.getDatum(key);
                if (datum == null) {
                    datum = new Datum();
                    datum.key = key;
                    RaftCore.addDatum(datum);
                }
            } finally {
                domainsManager.getDom2LockMap().get(UtilsAndCommons.assembleFullServiceName(namespaceId, dom)).unlock();
            }
        }

        long timestamp = RaftCore.getDatum(key).timestamp.get();

        if (RaftCore.isLeader()) {
            try {
                RaftCore.OPERATE_LOCK.lock();

                OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(request);
                requestWrapper.addParameter("clientIP", NetUtils.localServer());
                requestWrapper.addParameter("notify", "true");
                requestWrapper.addParameter("term", String.valueOf(RaftCore.getPeerSet().local().term));
                requestWrapper.addParameter("timestamp", String.valueOf(timestamp));

                onAddIP4Dom(requestWrapper);

                proxyParams.put("clientIP", NetUtils.localServer());
                proxyParams.put("notify", "true");
                proxyParams.put("term", String.valueOf(RaftCore.getPeerSet().local().term));
                proxyParams.put("timestamp", String.valueOf(timestamp));

                if (domain.getEnableHealthCheck() && !domain.getEnableClientBeat()) {
                    syncOnAddIP4Dom(namespaceId, dom, proxyParams);
                } else {
                    asyncOnAddIP4Dom(proxyParams);
                }
            } finally {
                RaftCore.OPERATE_LOCK.unlock();
            }

        }

        return "ok";
    }

    public JSONObject doSrvIPXT(String namespaceId, String dom, String agent, String clusters, String clientIP, int udpPort,
                                String env, boolean isCheck, String app, String tid, boolean healthyOnly) throws Exception {

        JSONObject result = new JSONObject();
        VirtualClusterDomain domObj = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        if (domObj == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom not found: " + dom);
        }

        checkIfDisabled(domObj);

        long cacheMillis = Switch.getCacheMillis(dom);

        // now try to enable the push
        try {
            if (udpPort > 0 && PushService.canEnablePush(agent)) {
                PushService.addClient(namespaceId, dom,
                    clusters,
                    agent,
                    new InetSocketAddress(clientIP, udpPort),
                    pushDataSource,
                    tid,
                    app);
                cacheMillis = Switch.getPushCacheMillis(dom);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[NACOS-API] failed to added push client", e);
            cacheMillis = Switch.getCacheMillis(dom);
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

    @RequestMapping("/onRemvIP4Dom")
    public void onRemvIP4Dom(HttpServletRequest request) throws Exception {
        if (Switch.getDisableAddIP()) {
            throw new AccessControlException("Deleting IP for dom is forbidden now.");
        }

        String clientIP = WebUtils.required(request, "clientIP");
        long term = Long.parseLong(WebUtils.required(request, "term"));

        if (!RaftCore.isLeader(clientIP)) {
            Loggers.RAFT.warn("peer(" + JSON.toJSONString(clientIP) + ") tried to publish " +
                "data but wasn't leader, leader: " + JSON.toJSONString(RaftCore.getLeader()));
            throw new IllegalStateException("peer(" + clientIP + ") tried to publish " +
                "data but wasn't leader");
        }

        if (term < RaftCore.getPeerSet().local().term.get()) {
            Loggers.RAFT.warn("out of date publish, pub-term: "
                + JSON.toJSONString(clientIP) + ", cur-term: " + JSON.toJSONString(RaftCore.getPeerSet().local()));
            throw new IllegalStateException("out of date publish, pub-term:"
                + term + ", cur-term: " + RaftCore.getPeerSet().local().term);
        }

        RaftCore.getPeerSet().local().resetLeaderDue();

        final String dom = WebUtils.required(request, "dom");
        final String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());

        if (domainsManager.getDomain(namespaceId, dom) == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        List<IpAddress> removedIPs = getIpAddresses(request);

        if (CollectionUtils.isEmpty(removedIPs)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        domainsManager.easyRemvIP4Dom(namespaceId, dom, removedIPs, term);
    }

    @RequestMapping("/srvIPXT")
    @ResponseBody
    public JSONObject srvIPXT(HttpServletRequest request) throws Exception {

        if (DistroMapper.getLocalhostIP().equals(UtilsAndCommons.LOCAL_HOST_IP)) {
            throw new Exception("invalid localhost ip: " + DistroMapper.getLocalhostIP());
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

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        String ipListString = WebUtils.required(request, "ipList");

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[REMOVE-IP] full arguments: serviceName: {}, iplist: {}", dom, ipListString);
        }

        Map<String, String> proxyParams = new HashMap<>(16);
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            proxyParams.put(entry.getKey(), entry.getValue()[0]);
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("[REMOVE-IP] full arguments, params: {}", proxyParams);
        }

        List<String> ipList = new ArrayList<>();

        List<IpAddress> ipObjList = new ArrayList<>(ipList.size());
        if (Boolean.parseBoolean(WebUtils.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            ipList = Arrays.asList(ipListString);
            ipObjList = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                ipObjList.add(IpAddress.fromJSON(ip));
            }
        }

        if (!RaftCore.isLeader()) {
            Loggers.RAFT.info("I'm not leader, will proxy to leader.");
            if (RaftCore.getLeader() == null) {
                throw new IllegalArgumentException("no leader now.");
            }

            RaftPeer leader = RaftCore.getLeader();

            String server = leader.ip;
            if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
            }

            String url = "http://" + server
                + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/remvIP4Dom";
            HttpClient.HttpResult result1 = HttpClient.httpPost(url, null, proxyParams);

            if (result1.code != HttpURLConnection.HTTP_OK) {
                Loggers.SRV_LOG.warn("failed to remove ip for dom, caused: {}", result1.content);
                throw new IllegalArgumentException("failed to remove ip for dom, caused " + result1.content);
            }

            return "ok";
        }

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        if (domain == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        if (CollectionUtils.isEmpty(ipObjList)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        String key = UtilsAndCommons.getIPListStoreKey(domainsManager.getDomain(namespaceId, dom));

        long timestamp = 1;
        if (RaftCore.getDatum(key) != null) {
            timestamp = RaftCore.getDatum(key).timestamp.get();
        }

        if (RaftCore.isLeader()) {

            try {

                RaftCore.OPERATE_LOCK.lock();

                OverrideParameterRequestWrapper requestWrapper = OverrideParameterRequestWrapper.buildRequest(request);
                requestWrapper.addParameter("clientIP", NetUtils.localServer());
                requestWrapper.addParameter("notify", "true");
                requestWrapper.addParameter("term", String.valueOf(RaftCore.getPeerSet().local().term));
                requestWrapper.addParameter("timestamp", String.valueOf(timestamp));

                onRemvIP4Dom(requestWrapper);

                proxyParams.put("clientIP", NetUtils.localServer());
                proxyParams.put("notify", "true");
                proxyParams.put("term", String.valueOf(RaftCore.getPeerSet().local().term));
                proxyParams.put("timestamp", String.valueOf(timestamp));

                if (domain.getEnableHealthCheck() && !domain.getEnableClientBeat()) {
                    syncOnRemvIP4Dom(namespaceId, dom, proxyParams);
                } else {
                    asyncOnRemvIP4Dom(proxyParams);
                }
            } finally {
                RaftCore.OPERATE_LOCK.unlock();
            }

            Loggers.EVT_LOG.info("dom: {} {POS} {IP-REMV} new: {} operatorIP: {}",
                dom, ipListString, WebUtils.optional(request, "clientIP", "unknown"));
        }

        return "ok";
    }

    @RequestMapping("/pushState")
    public JSONObject pushState(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        boolean detail = Boolean.parseBoolean(WebUtils.optional(request, "detail", "false"));
        boolean reset = Boolean.parseBoolean(WebUtils.optional(request, "reset", "false"));

        List<PushService.Receiver.AckEntry> failedPushes = PushService.getFailedPushes();
        int failedPushCount = PushService.getFailedPushCount();
        result.put("succeed", PushService.getTotalPush() - failedPushCount);
        result.put("total", PushService.getTotalPush());

        if (PushService.getTotalPush() > 0) {
            result.put("ratio", ((float) PushService.getTotalPush() - failedPushCount) / PushService.getTotalPush());
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


    ReentrantLock lock = new ReentrantLock();

    @NeedAuth
    @RequestMapping("/updateSwitch")
    public String updateSwitch(HttpServletRequest request) throws Exception {
        Boolean debug = Boolean.parseBoolean(WebUtils.optional(request, "debug", "false"));

        if (!RaftCore.isLeader() && !debug) {
            Map<String, String> tmpParams = new HashMap<>(16);
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                tmpParams.put(entry.getKey(), entry.getValue()[0]);
            }

            RaftProxy.proxyGET(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/updateSwitch", tmpParams);
            return "ok";
        }

        try {
            lock.lock();
            String entry = WebUtils.required(request, "entry");

            Datum datum = RaftCore.getDatum(UtilsAndCommons.DOMAINS_DATA_ID_PRE + UtilsAndCommons.SWITCH_DOMAIN_NAME);
            SwitchDomain switchDomain = null;

            if (datum != null) {
                switchDomain = JSON.parseObject(datum.value, SwitchDomain.class);
            } else {
                Loggers.SRV_LOG.warn("datum: {}{} is null", UtilsAndCommons.DOMAINS_DATA_ID_PRE, UtilsAndCommons.SWITCH_DOMAIN_NAME);
            }

            if (SwitchEntry.BATCH.equals(entry)) {
                //batch update
                SwitchDomain dom = JSON.parseObject(WebUtils.required(request, "json"), SwitchDomain.class);
                dom.setEnableStandalone(Switch.isEnableStandalone());
                if (dom.httpHealthParams.getMin() < SwitchDomain.HttpHealthParams.MIN_MIN
                    || dom.tcpHealthParams.getMin() < SwitchDomain.HttpHealthParams.MIN_MIN) {

                    throw new IllegalArgumentException("min check time for http or tcp is too small(<500)");
                }

                if (dom.httpHealthParams.getMax() < SwitchDomain.HttpHealthParams.MIN_MAX
                    || dom.tcpHealthParams.getMax() < SwitchDomain.HttpHealthParams.MIN_MAX) {

                    throw new IllegalArgumentException("max check time for http or tcp is too small(<3000)");
                }

                if (dom.httpHealthParams.getFactor() < 0
                    || dom.httpHealthParams.getFactor() > 1
                    || dom.tcpHealthParams.getFactor() < 0
                    || dom.tcpHealthParams.getFactor() > 1) {

                    throw new IllegalArgumentException("malformed factor");
                }

                Switch.setDom(dom);
                if (!debug) {
                    Switch.save();
                }

                return "ok";
            }

            if (switchDomain != null) {
                Switch.setDom(switchDomain);
            }

            if (entry.equals(SwitchEntry.DISTRO_THRESHOLD)) {
                Float threshold = Float.parseFloat(WebUtils.required(request, "distroThreshold"));

                if (threshold <= 0) {
                    throw new IllegalArgumentException("distroThreshold can not be zero or negative: " + threshold);
                }

                Switch.setDistroThreshold(threshold);

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }


            if (entry.equals(SwitchEntry.ENABLE_ALL_DOM_NAME_CACHE)) {
                Boolean enable = Boolean.parseBoolean(WebUtils.required(request, "enableAllDomNameCache"));
                Switch.setAllDomNameCache(enable);

                if (!debug) {
                    Switch.save();
                }

                return "ok";
            }

            if (entry.equals(SwitchEntry.INCREMENTAL_LIST)) {
                String action = WebUtils.required(request, "action");
                List<String> doms = Arrays.asList(WebUtils.required(request, "incrementalList").split(","));

                if (action.equals(SwitchEntry.ACTION_UPDATE)) {
                    Switch.getIncrementalList().addAll(doms);
                } else if (action.equals(SwitchEntry.ACTION_DELETE)) {
                    Switch.getIncrementalList().removeAll(doms);
                } else {
                    throw new IllegalArgumentException("action is not allowed: " + action);
                }

                if (!debug) {
                    Switch.save();
                }

                return "ok";
            }

            if (entry.equals(SwitchEntry.HEALTH_CHECK_WHITLE_LIST)) {
                String action = WebUtils.required(request, "action");
                List<String> whiteList = Arrays.asList(WebUtils.required(request, "healthCheckWhiteList").split(","));

                if (action.equals(SwitchEntry.ACTION_UPDATE)) {
                    Switch.getHealthCheckWhiteList().addAll(whiteList);
                    if (!debug) {
                        Switch.save();
                    }

                    return "ok";
                }

                if (action.equals(SwitchEntry.ACTION_DELETE)) {
                    Switch.getHealthCheckWhiteList().removeAll(whiteList);
                    if (!debug) {
                        Switch.save();
                    }
                    return "ok";
                }
            }

            if (entry.equals(SwitchEntry.CLIENT_BEAT_INTERVAL)) {
                long clientBeatInterval = Long.parseLong(WebUtils.required(request, "clientBeatInterval"));
                Switch.setClientBeatInterval(clientBeatInterval);

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.PUSH_VERSION)) {
                String type = WebUtils.required(request, "type");
                String version = WebUtils.required(request, "version");

                if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
                    throw new IllegalArgumentException("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
                }

                if (StringUtils.equals(SwitchEntry.CLIENT_JAVA, type)) {
                    Switch.setPushJavaVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_PYTHON, type)) {
                    Switch.setPushPythonVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_C, type)) {
                    Switch.setPushCVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_GO, type)) {
                    Switch.setPushGoVersion(version);
                } else {
                    throw new IllegalArgumentException("unsupported client type: " + type);
                }

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.TRAFFIC_SCHEDULING_VERSION)) {
                String type = WebUtils.required(request, "type");
                String version = WebUtils.required(request, "version");

                if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
                    throw new IllegalArgumentException("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
                }

                if (StringUtils.equals(SwitchEntry.CLIENT_JAVA, type)) {
                    Switch.setTrafficSchedulingJavaVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_PYTHON, type)) {
                    Switch.setTrafficSchedulingPythonVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_C, type)) {
                    Switch.setTrafficSchedulingCVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_TENGINE, type)) {
                    Switch.setTrafficSchedulingTengineVersion(version);
                } else {
                    throw new IllegalArgumentException("unsupported client type: " + type);
                }

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.PUSH_CACHE_MILLIS)) {
                String dom = WebUtils.optional(request, "dom", StringUtils.EMPTY);
                Long cacheMillis = Long.parseLong(WebUtils.required(request, "millis"));

                if (cacheMillis < SwitchEntry.MIN_PUSH_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min cache time for http or tcp is too small(<10000)");
                }

                Switch.setPushCacheMillis(dom, cacheMillis);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            // extremely careful while modifying this, cause it will affect all clients without pushing enabled
            if (entry.equals(SwitchEntry.DEFAULT_CACHE_MILLIS)) {
                String dom = WebUtils.optional(request, "dom", StringUtils.EMPTY);
                Long cacheMillis = Long.parseLong(WebUtils.required(request, "millis"));

                if (cacheMillis < SwitchEntry.MIN_CACHE_TIME_MIILIS) {
                    throw new IllegalArgumentException("min default cache time  is too small(<1000)");
                }

                Switch.setCacheMillis(dom, cacheMillis);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.MASTERS)) {
                List<String> masters = Arrays.asList(WebUtils.required(request, "names").split(","));

                Switch.setMasters(masters);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DISTRO)) {
                boolean enabled = Boolean.parseBoolean(WebUtils.required(request, "enabled"));

                Switch.setDistroEnabled(enabled);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.CHECK)) {
                boolean enabled = Boolean.parseBoolean(WebUtils.required(request, "enabled"));

                Switch.setHeathCheckEnabled(enabled);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DEFAULT_HEALTH_CHECK_MODE)) {
                String defaultHealthCheckMode = WebUtils.required(request, "mode");

                Switch.setDefaultHealthCheckMode(defaultHealthCheckMode);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DOM_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(WebUtils.required(request, "millis"));

                if (millis < SwitchEntry.MIN_DOM_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("domStatusSynchronizationPeriodMillis is too small(<5000)");
                }

                Switch.setDomStatusSynchronizationPeriodMillis(millis);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.SERVER_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(WebUtils.required(request, "millis"));

                if (millis < SwitchEntry.MIN_SERVER_SYNC_TIME_MIILIS) {
                    throw new IllegalArgumentException("serverStatusSynchronizationPeriodMillis is too small(<15000)");
                }

                Switch.setServerStatusSynchronizationPeriodMillis(millis);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.HEALTH_CHECK_TIMES)) {
                Integer times = Integer.parseInt(WebUtils.required(request, "times"));

                Switch.setCheckTimes(times);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DISABLE_ADD_IP)) {
                boolean disableAddIP = Boolean.parseBoolean(WebUtils.required(request, "disableAddIP"));

                Switch.setDisableAddIP(disableAddIP);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.ENABLE_CACHE)) {
                boolean enableCache = Boolean.parseBoolean(WebUtils.required(request, "enableCache"));

                Switch.setEnableCache(enableCache);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.SEND_BEAT_ONLY)) {
                boolean sendBeatOnly = Boolean.parseBoolean(WebUtils.required(request, "sendBeatOnly"));

                Switch.setSendBeatOnly(sendBeatOnly);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.LIMITED_URL_MAP)) {
                Map<String, Integer> limitedUrlMap = new HashMap<>(16);
                String limitedUrls = WebUtils.required(request, "limitedUrls");

                if (!StringUtils.isEmpty(limitedUrls)) {
                    String[] entries = limitedUrls.split(",");
                    for (int i = 0; i < entries.length; i++) {
                        String[] parts = entries[i].split(":");
                        if (parts.length < 2) {
                            throw new IllegalArgumentException("invalid input for limited urls");
                        }

                        String limitedUrl = parts[0];
                        if (StringUtils.isEmpty(limitedUrl)) {
                            throw new IllegalArgumentException("url can not be empty, url: " + limitedUrl);
                        }

                        int statusCode = Integer.parseInt(parts[1]);
                        if (statusCode <= 0) {
                            throw new IllegalArgumentException("illegal normal status code: " + statusCode);
                        }

                        limitedUrlMap.put(limitedUrl, statusCode);

                    }

                    Switch.setLimitedUrlMap(limitedUrlMap);
                    if (!debug) {
                        Switch.save();
                    }
                    return "ok";
                }
            }

            if (entry.equals(SwitchEntry.ENABLE_STANDALONE)) {
                String enable = WebUtils.required(request, "enableStandalone");

                if (!StringUtils.isNotEmpty(enable)) {
                    Switch.setEnableStandalone(Boolean.parseBoolean(enable));
                }

                if (!debug) {
                    Switch.save();
                }

                return "ok";
            }


            throw new IllegalArgumentException("update entry not found: " + entry);
        } finally {
            lock.unlock();
        }


    }

    public void checkIfDisabled(VirtualClusterDomain domObj) throws Exception {
        if (!domObj.getEnabled()) {
            throw new Exception("domain is disabled now.");
        }
    }

    @RequestMapping("/switches")
    public JSONObject switches(HttpServletRequest request) {
        return JSON.parseObject(Switch.getDom().toJSON());
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
        Map<String, Set<String>> domMap = domainsManager.getAllDomNames();
        JSONObject result = new JSONObject();
        // For old DNS-F client:
        String dnsfVersion = "1.0.1";
        String agent = request.getHeader("Client-Version");
        ClientInfo clientInfo = new ClientInfo(agent);
        if (clientInfo.type == ClientInfo.ClientType.DNS && clientInfo.version.compareTo(VersionUtil.parseVersion(dnsfVersion)) <= 0) {

            List<String> doms = new ArrayList<String>();
            Set<String> domSet = null;

            if (domMap.containsKey(Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID)) {
                domSet = domMap.get(Constants.REQUEST_PARAM_DEFAULT_NAMESPACE_ID);
            }

            if (CollectionUtils.isEmpty(domSet)) {
                result.put("doms", new HashSet<>());
                result.put("count", 0);
                return result;
            }

            for (String dom : domSet) {
                if (DistroMapper.responsible(dom) || !responsibleOnly) {
                    doms.add(dom);
                }
            }

            result.put("doms", doms);
            result.put("count", doms.size());
            return result;
        }

        Map<String, Set<String>> doms = new HashMap<>(16);
        int count = 0;
        for (String namespaceId : domMap.keySet()) {
            doms.put(namespaceId, new HashSet<>());
            for (String dom : domMap.get(namespaceId)) {
                if (DistroMapper.responsible(dom) || !responsibleOnly) {
                    doms.get(namespaceId).add(dom);
                }
            }
            count += doms.get(namespaceId).size();
        }

        result.put("doms", doms);
        result.put("count", count);

        return result;
    }

    @RequestMapping("/searchDom")
    public JSONObject searchDom(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String expr = WebUtils.required(request, "expr");

        List<Domain> doms
            = domainsManager.searchDomains(namespaceId, ".*" + expr + ".*");

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

        VirtualClusterDomain domObj = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

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

        domainsManager.easyAddOrReplaceDom(domObj);

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
            result.put("status", DistroMapper.getDistroConfig());
            return result;
        }

        if (StringUtils.equals(SwitchEntry.ACTION_CLEAN, action)) {
            DistroMapper.clean();
            return result;
        }

        return result;
    }

    @RequestMapping("/metrics")
    public JSONObject metrics(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        int domCount = domainsManager.getDomCount();
        int ipCount = domainsManager.getInstanceCount();

        int responsibleDomCount = domainsManager.getResponsibleDomCount();
        int responsibleIPCount = domainsManager.getResponsibleIPCount();

        result.put("domCount", domCount);
        result.put("ipCount", ipCount);
        result.put("responsibleDomCount", responsibleDomCount);
        result.put("responsibleIPCount", responsibleIPCount);
        result.put("cpu", SystemUtils.getCPU());
        result.put("load", SystemUtils.getLoad());
        result.put("mem", SystemUtils.getMem());
        result.put("notifyTask", RaftCore.notifier.getTaskSize());

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
        DistroMapper.onReceiveServerStatus(serverStatus);

        return "ok";
    }

    @RequestMapping("/reCalculateCheckSum4Dom")
    public JSONObject reCalculateCheckSum4Dom(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

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
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsibleServer", DistroMapper.mapSrv(dom));

        return result;
    }

    @RequestMapping("/getHealthyServerList")
    public JSONObject getHealthyServerList(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("healthyList", DistroMapper.getHealthyList());

        return result;
    }

    @RequestMapping("/responsible")
    public JSONObject responsible(HttpServletRequest request) {
        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsible", DistroMapper.responsible(dom));

        return result;
    }

    @RequestMapping("/domStatus")
    public String domStatus(HttpServletRequest request) {
        //format: dom1@@checksum@@@dom2@@checksum
        String domsStatusString = WebUtils.required(request, "domsStatus");
        String serverIP = WebUtils.optional(request, "clientIP", "");

        if (!NamingProxy.getServers().contains(serverIP)) {
            throw new IllegalArgumentException("ip: " + serverIP + " is not in serverlist");
        }

        try {
            DomainsManager.DomainChecksum checksums = JSON.parseObject(domsStatusString, DomainsManager.DomainChecksum.class);
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
                Domain domain = domainsManager.getDomain(checksums.namespaceId, dom);

                if (domain == null) {
                    continue;
                }

                domain.recalculateChecksum();

                if (!checksum.equals(domain.getChecksum())) {
                    if (Loggers.SRV_LOG.isDebugEnabled()) {
                        Loggers.SRV_LOG.debug("checksum of {} is not consistent, remote: {}, checksum: {}, local: {}",
                            dom, serverIP, checksum, domain.getChecksum());
                    }
                    domainsManager.addUpdatedDom2Queue(checksums.namespaceId, dom, serverIP, checksum);
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
        pac.put("checkServer", DistroMapper.mapSrvName(vDom.getName()));

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

    public void setDomainsManager(DomainsManager domainsManager) {
        this.domainsManager = domainsManager;
    }

}
