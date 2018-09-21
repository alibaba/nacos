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
import com.alibaba.nacos.common.util.IoUtils;
import com.alibaba.nacos.common.util.Md5Utils;
import com.alibaba.nacos.common.util.SystemUtil;
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
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.AccessControlException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
                result = ApiCommands.this.srvIPXT(MockHttpRequest.buildRequest(params));
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
        String name = BaseServlet.optional(request, "name", StringUtils.EMPTY);
        if (StringUtils.isEmpty(name)) {
            name = BaseServlet.required(request, "dom");
        }

        Loggers.SRV_LOG.info("DOM", "request dom:" + name);

        Domain dom = domainsManager.getDomain(name);
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
        String dom = BaseServlet.required(request, "dom");

        VirtualClusterDomain domObj
                = (VirtualClusterDomain) domainsManager.getDomain(dom);
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
        String domName = BaseServlet.required(request, "dom");

        VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(domName);

        if (dom == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom: " + domName + " not found.");
        }

        List<IpAddress> ips = dom.allIPs();

        JSONObject result = new JSONObject();
        JSONArray ipArray = new JSONArray();

        for (IpAddress ip : ips) {
            ipArray.add(ip.toIPAddr() + "_" + ip.isValid() + "_" + ip.getInvalidType());
        }

        result.put("ips", ipArray);
        return result;
    }

    @RequestMapping("/ip4Dom")
    public JSONObject ip4Dom(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();
        try {
            String domName = BaseServlet.required(request, "dom");
            String clusters = BaseServlet.optional(request, "clusters", StringUtils.EMPTY);
            String agent = BaseServlet.optional(request, "header:Client-Version", StringUtils.EMPTY);

            VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(domName);

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
                ipPac.put("invalidType", ip.getInvalidType());

                ipArray.add(ipPac);
            }

            result.put("ips", ipArray);
        } catch (Throwable e) {
            Loggers.SRV_LOG.warn("VIPSRV-IP4DOM", "failed to call ip4Dom, caused " + e.getMessage());
            throw new IllegalArgumentException(e);
        }

        return result;
    }

    @RequestMapping("/regDom")
    public String regDom(HttpServletRequest request) throws Exception {


        String dom = BaseServlet.required(request, "dom");
        if (domainsManager.getDomain(dom) != null) {
            throw new IllegalArgumentException("specified dom already exists, dom : " + dom);
        }

        addOrReplaceDom(request);

        return "ok";
    }

    @RequestMapping("/clientBeat")
    public JSONObject clientBeat(HttpServletRequest request) throws Exception {
        String beat = BaseServlet.required(request, "beat");
        RsInfo clientBeat = JSON.parseObject(beat, RsInfo.class);
        String dom = BaseServlet.required(request, "dom");
        String app;
        app = BaseServlet.optional(request, "app", StringUtils.EMPTY);
        String clusterName = clientBeat.getCluster();

        Loggers.TENANT.debug("client-beat", "beat: " + beat);
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        //if domain does not exist, register it.
        if (virtualClusterDomain == null) {
            Map<String, String[]> stringMap = new HashMap<>(16);
            stringMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
            stringMap.put("enableClientBeat", Arrays.asList("true").toArray(new String[1]));
            stringMap.put("cktype", Arrays.asList("TCP").toArray(new String[1]));
            stringMap.put("appName", Arrays.asList(app).toArray(new String[1]));
            stringMap.put("clusterName", Arrays.asList(clusterName).toArray(new String[1]));
            regDom(MockHttpRequest.buildRequest(stringMap));

            virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);
            String ip = clientBeat.getIp();
            int port = clientBeat.getPort();

            IpAddress ipAddress = new IpAddress();
            ipAddress.setPort(port);
            ipAddress.setIp(ip);
            ipAddress.setWeight(1);
            ipAddress.setClusterName(clusterName);

            stringMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
            stringMap.put("json", Arrays.asList("true").toArray(new String[1]));
            addIP4Dom(MockHttpRequest.buildRequest(stringMap));
            Loggers.SRV_LOG.warn("dom not found, register it, dom:" + dom);
        }

        if (!DistroMapper.responsible(dom)) {
            String server = DistroMapper.mapSrv(dom);
            Loggers.EVT_LOG.info("I'm not responsible for " + dom + ", proxy it to " + server);
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
            if (virtualClusterDomain != null) {
                virtualClusterDomain.processClientBeat(clientBeat);
            }
        }

        JSONObject result = new JSONObject();

        result.put("clientBeatInterval", Switch.getClientBeatInterval());

        return result;
    }


    private String addOrReplaceDom(HttpServletRequest request) throws Exception {

        String dom = BaseServlet.required(request, "dom");
        String owners = BaseServlet.optional(request, "owners", StringUtils.EMPTY);
        String token = BaseServlet.optional(request, "token", Md5Utils.getMD5(dom, "utf-8"));

        float protectThreshold = NumberUtils.toFloat(BaseServlet.optional(request, "protectThreshold", "0.0"));
        boolean isUseSpecifiedURL = Boolean.parseBoolean(BaseServlet.optional(request, "isUseSpecifiedURL", "false"));
        String envAndSite = BaseServlet.optional(request, "envAndSites", StringUtils.EMPTY);
        boolean resetWeight = Boolean.parseBoolean(BaseServlet.optional(request, "resetWeight", "false"));
        boolean enableHealthCheck = Boolean.parseBoolean(BaseServlet.optional(request, "enableHealthCheck", "true"));
        boolean enable = Boolean.parseBoolean(BaseServlet.optional(request, "enable", "true"));
        String disabledSites = BaseServlet.optional(request, "disabledSites", StringUtils.EMPTY);
        boolean eanbleClientBeat = Boolean.parseBoolean(BaseServlet.optional(request, "enableClientBeat", "false"));
        String clusterName = BaseServlet.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);

        String serviceMetadataJson = BaseServlet.optional(request, "serviceMetadata", StringUtils.EMPTY);
        String clusterMetadataJson = BaseServlet.optional(request, "clusterMetadata", StringUtils.EMPTY);

        Loggers.SRV_LOG.info("RESET-WEIGHT", String.valueOf(resetWeight));

        VirtualClusterDomain domObj = new VirtualClusterDomain();
        domObj.setName(dom);
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

        String clusters = BaseServlet.optional(request, "clusters", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(clusters)) {
            // new format
            List<Cluster> clusterObjs = JSON.parseArray(clusters, Cluster.class);

            for (Cluster cluster : clusterObjs) {
                domObj.getClusterMap().put(cluster.getName(), cluster);
            }
        } else {
            // old format, default cluster will be constructed automatically
            String cktype = BaseServlet.optional(request, "cktype", "TCP");
            String ipPort4Check = BaseServlet.optional(request, "ipPort4Check", "true");
            String nodegroup = BaseServlet.optional(request, "nodegroup", StringUtils.EMPTY);

            int defIPPort = NumberUtils.toInt(BaseServlet.optional(request, "defIPPort", "-1"));
            int defCkport = NumberUtils.toInt(BaseServlet.optional(request, "defCkport", "80"));

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

            if (AbstractHealthCheckConfig.Tcp.TYPE.equals(cktype)) {
                AbstractHealthCheckConfig.Tcp config = new AbstractHealthCheckConfig.Tcp();
                cluster.setHealthChecker(config);
            } else if (AbstractHealthCheckConfig.Http.TYPE.equals(cktype)) {

                String path = BaseServlet.optional(request, "path", StringUtils.EMPTY);
                String headers = BaseServlet.optional(request, "headers", StringUtils.EMPTY);
                String expectedResponseCode = BaseServlet.optional(request, "expectedResponseCode", "200");

                AbstractHealthCheckConfig.Http config = new AbstractHealthCheckConfig.Http();
                config.setType(cktype);
                config.setPath(path);
                config.setHeaders(headers);
                config.setExpectedResponseCode(Integer.parseInt(expectedResponseCode));
                cluster.setHealthChecker(config);

            } else if (AbstractHealthCheckConfig.Mysql.TYPE.equals(cktype)) {

                AbstractHealthCheckConfig.Mysql config = new AbstractHealthCheckConfig.Mysql();
                String user = BaseServlet.optional(request, "user", StringUtils.EMPTY);
                String pwd = BaseServlet.optional(request, "pwd", StringUtils.EMPTY);
                String cmd = BaseServlet.optional(request, "cmd", StringUtils.EMPTY);
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

    @NeedAuth
    @RequestMapping("/replaceDom")
    public String replaceDom(HttpServletRequest request) throws Exception {
        String dom = BaseServlet.required(request, "dom");
        if (domainsManager.getDomain(dom) == null) {
            throw new IllegalArgumentException("specified dom doesn't exist, dom : " + dom);
        }

        addOrReplaceDom(request);

        Loggers.SRV_LOG.info("dom: " + dom + " is updated, operator: "
                + BaseServlet.optional(request, "clientIP", "unknown"));

        return "ok";
    }

    private IpAddress getIPAddress(HttpServletRequest request) {

        String ip = BaseServlet.required(request, "ip");
        String port = BaseServlet.required(request, "port");
        String weight = BaseServlet.optional(request, "weight", "1");
        String cluster = BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        if (StringUtils.isEmpty(cluster)) {
            cluster = BaseServlet.required(request, "clusterName");
        }

        IpAddress ipAddress = new IpAddress();
        ipAddress.setPort(Integer.parseInt(port));
        ipAddress.setIp(ip);
        ipAddress.setWeight(Double.parseDouble(weight));
        ipAddress.setClusterName(cluster);

        return ipAddress;
    }

    @RequestMapping("/deRegService")
    public String deRegService(HttpServletRequest request) throws Exception {
        IpAddress ipAddress = getIPAddress(request);
        String dom = BaseServlet.optional(request, "serviceName", StringUtils.EMPTY);
        if (StringUtils.isEmpty(dom)) {
            dom = BaseServlet.required(request, "dom");
        }

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);
        if (virtualClusterDomain == null) {
            return "ok";
        }

        ParameterMap<String, String[]> parameterMap = new ParameterMap<>();
        parameterMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
        parameterMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
        parameterMap.put("json", Arrays.asList("true").toArray(new String[1]));
        parameterMap.put("token", Arrays.asList(virtualClusterDomain.getToken()).toArray(new String[1]));
        MockHttpRequest mockHttpRequest = MockHttpRequest.buildRequest(parameterMap);

        return remvIP4Dom(mockHttpRequest);

    }

    @SuppressFBWarnings("JLM_JSR166_LOCK_MONITORENTER")
    @RequestMapping("/regService")
    public String regService(HttpServletRequest request) throws Exception {

        String dom = BaseServlet.required(request, "dom");
        String tenant = BaseServlet.optional(request, "tid", StringUtils.EMPTY);
        String app = BaseServlet.optional(request, "app", "DEFAULT");
        String env = BaseServlet.optional(request, "env", StringUtils.EMPTY);
        String instanceMetadataJson = BaseServlet.optional(request, "metadata", StringUtils.EMPTY);

        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        IpAddress ipAddress = getIPAddress(request);
        ipAddress.setApp(app);
        ipAddress.setLastBeat(System.currentTimeMillis());
        if (StringUtils.isNotEmpty(instanceMetadataJson)) {
            ipAddress.setMetadata(JSON.parseObject(instanceMetadataJson, new TypeReference<Map<String, String>>() {
            }));
        }

        Loggers.TENANT.debug("reg-service: " + dom + "|" + ipAddress.toJSON() + "|" + env + "|" + tenant + "|" + app);

        if (virtualClusterDomain == null) {

            regDom(request);

            Lock lock = domainsManager.addLock(dom);

            synchronized (lock) {
                lock.wait(5000L);
            }

            virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);
        }

        if (virtualClusterDomain != null) {

            if (!virtualClusterDomain.getClusterMap().containsKey(ipAddress.getClusterName())) {
                doAddCluster4Dom(request);
            }

            Loggers.TENANT.debug("reg-service", "add ip: " + dom + "|" + ipAddress.toJSON());
            Map<String, String[]> stringMap = new HashMap<>(16);
            stringMap.put("dom", Arrays.asList(dom).toArray(new String[1]));
            stringMap.put("ipList", Arrays.asList(JSON.toJSONString(Arrays.asList(ipAddress))).toArray(new String[1]));
            stringMap.put("json", Arrays.asList("true").toArray(new String[1]));
            stringMap.put("token", Arrays.asList(virtualClusterDomain.getToken()).toArray(new String[1]));
            doAddIP4Dom(MockHttpRequest.buildRequest(stringMap));
        } else {
            throw new IllegalArgumentException("dom not found: " + dom);
        }

        return "ok";
    }


    @NeedAuth
    @RequestMapping("/updateDom")
    public String updateDom(HttpServletRequest request) throws Exception {
        // dom
        String name = BaseServlet.required(request, "dom");
        VirtualClusterDomain dom = (VirtualClusterDomain) domainsManager.getDomain(name);
        if (dom == null) {
            throw new IllegalStateException("dom not found");
        }

        RaftPeer leader = RaftCore.getLeader();
        if (leader == null) {
            throw new IllegalStateException("not leader at present, cannot update");
        }

        String owners = BaseServlet.optional(request, "owners", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(owners)) {
            dom.setOwners(Arrays.asList(owners.split(",")));
        }

        String token = BaseServlet.optional(request, "newToken", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(token)) {
            dom.setToken(token);
        }

        String enableClientBeat = BaseServlet.optional(request, "enableClientBeat", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(enableClientBeat)) {
            dom.setEnableClientBeat(Boolean.parseBoolean(enableClientBeat));
        }

        String protectThreshold = BaseServlet.optional(request, "protectThreshold", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(protectThreshold)) {
            dom.setProtectThreshold(Float.parseFloat(protectThreshold));
        }

        String sitegroup = BaseServlet.optional(request, "sitegroup", StringUtils.EMPTY);
        String setSiteGroupForce = BaseServlet.optional(request, "setSiteGroupForce", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(sitegroup) || !StringUtils.isEmpty(setSiteGroupForce)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setSitegroup(sitegroup);
        }

        String cktype = BaseServlet.optional(request, "cktype", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(cktype)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            if (cktype.equals(AbstractHealthCheckProcessor.HTTP_PROCESSOR.getType())) {
                AbstractHealthCheckConfig.Http config = new AbstractHealthCheckConfig.Http();
                config.setType(cktype);
                config.setPath(BaseServlet.required(request, "path"));
                cluster.setHealthChecker(config);
            } else if (cktype.equals(AbstractHealthCheckProcessor.TCP_PROCESSOR.getType())) {
                AbstractHealthCheckConfig.Tcp config = new AbstractHealthCheckConfig.Tcp();
                config.setType(cktype);
                cluster.setHealthChecker(config);
            } else if (cktype.equals(AbstractHealthCheckProcessor.MYSQL_PROCESSOR.getType())) {
                AbstractHealthCheckConfig.Mysql config = new AbstractHealthCheckConfig.Mysql();
                config.setCmd(BaseServlet.required(request, "cmd"));
                config.setPwd(BaseServlet.required(request, "pwd"));
                config.setUser(BaseServlet.required(request, "user"));
                cluster.setHealthChecker(config);
            } else {
                throw new IllegalArgumentException("unsupported health check type: " + cktype);
            }

        }

        String defIPPort = BaseServlet.optional(request, "defIPPort", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(defIPPort)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setDefIPPort(Integer.parseInt(defIPPort));
        }

        String submask = BaseServlet.optional(request, "submask", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(submask)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setSubmask(submask);
        }

        String ipPort4Check = BaseServlet.optional(request, "ipPort4Check", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(ipPort4Check)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setUseIPPort4Check(Boolean.parseBoolean(ipPort4Check));
        }

        String defCkPort = BaseServlet.optional(request, "defCkPort", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(defCkPort)) {
            Cluster cluster
                    = dom.getClusterMap().get(BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME));
            if (cluster == null) {
                throw new IllegalStateException("cluster not found");
            }

            cluster.setDefCkport(Integer.parseInt(defCkPort));
        }

        String useSpecifiedUrl = BaseServlet.optional(request, "useSpecifiedURL", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(useSpecifiedUrl)) {
            dom.setUseSpecifiedURL(Boolean.parseBoolean(useSpecifiedUrl));
        }

        String resetWeight = BaseServlet.optional(request, "resetWeight", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(resetWeight)) {
            dom.setResetWeight(Boolean.parseBoolean(resetWeight));
        }

        String enableHealthCheck = BaseServlet.optional(request, "enableHealthCheck", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(enableHealthCheck)) {
            dom.setEnableHealthCheck(Boolean.parseBoolean(enableHealthCheck));
        }

        String enabled = BaseServlet.optional(request, "enabled", StringUtils.EMPTY);
        if (!StringUtils.isEmpty(enabled)) {
            dom.setEnabled(Boolean.parseBoolean(enabled));
        }

        String ipDeletedTimeout = BaseServlet.optional(request, "ipDeletedTimeout", "-1");

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
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total dom: diamond "
                + domainsManager.getDomMap().size() + ",raft " + domainsManager.getRaftDomMap().size()
                + ", local port:" + RunningConfig.getServerPort());
        return result;
    }


    @NeedAuth
    @RequestMapping("/remvDom")
    public String remvDom(HttpServletRequest request) throws Exception {
        String dom = BaseServlet.required(request, "dom");
        if (domainsManager.getDomain(dom) == null) {
            throw new IllegalStateException("specified domain doesn't exists.");
        }

        domainsManager.easyRemoveDom(dom);

        return "ok";
    }

    @RequestMapping("/getDomsByIP")
    public JSONObject getDomsByIP(HttpServletRequest request) {
        String ip = BaseServlet.required(request, "ip");

        Set<String> doms = new HashSet<String>();
        for (String dom : domainsManager.getAllDomNames()) {
            Domain domObj = domainsManager.getDomain(dom);

            List<IpAddress> ipObjs = domObj.allIPs();
            for (IpAddress ipObj : ipObjs) {
                if (ip.contains(":")) {
                    if (StringUtils.equals(ipObj.getIp() + ":" + ipObj.getPort(), ip)) {
                        doms.add(domObj.getName());
                    }
                } else {
                    if (StringUtils.equals(ipObj.getIp(), ip)) {
                        doms.add(domObj.getName());
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

        String clientIP = BaseServlet.required(request, "clientIP");

        long term = Long.parseLong(BaseServlet.required(request, "term"));

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
                    + term + ", cur-term: " + RaftCore.getPeerSet().local().term.get());
        }

        RaftCore.getPeerSet().local().resetLeaderDue();

        final String dom = BaseServlet.required(request, "dom");
        if (domainsManager.getDomain(dom) == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        boolean updateOnly = Boolean.parseBoolean(BaseServlet.optional(request, "updateOnly", Boolean.FALSE.toString()));

        String ipListString = BaseServlet.required(request, "ipList");
        List<IpAddress> newIPs = new ArrayList<>();

        List<String> ipList;
        if (Boolean.parseBoolean(BaseServlet.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            newIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
            for (String ip : ipList) {
                IpAddress ipAddr = IpAddress.fromJSON(ip);
                newIPs.add(ipAddr);
            }
        }

        long timestamp = Long.parseLong(BaseServlet.required(request, "timestamp"));

        if (CollectionUtils.isEmpty(newIPs)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        if (updateOnly) {
            //make sure every IP is in the dom, otherwise refuse update
            List<IpAddress> oldIPs = domainsManager.getDomain(dom).allIPs();
            Collection diff = CollectionUtils.subtract(newIPs, oldIPs);
            if (diff.size() != 0) {
                throw new IllegalArgumentException("these IPs are not present: " + Arrays.toString(diff.toArray())
                        + ", if you want to add them, remove updateOnly flag");
            }
        }
        domainsManager.easyAddIP4Dom(dom, newIPs, timestamp, term);

        return "ok";
    }


    private String doAddIP4Dom(HttpServletRequest request) throws Exception {

        if (Switch.getDisableAddIP()) {
            throw new AccessControlException("Adding IP for dom is forbidden now.");
        }

        Map<String, String> proxyParams = new HashMap<>(16);
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            proxyParams.put(entry.getKey(), entry.getValue()[0]);
        }

        String ipListString = BaseServlet.required(request, "ipList");
        final List<String> ipList;
        List<IpAddress> newIPs = new ArrayList<>();

        if (Boolean.parseBoolean(BaseServlet.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
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
                Loggers.SRV_LOG.warn("failed to add ip for dom, caused " + result1.content);
                throw new IllegalArgumentException("failed to add ip for dom, caused " + result1.content);
            }

            return "ok";
        }

        final String dom = BaseServlet.required(request, "dom");
        if (domainsManager.getDomain(dom) == null) {
            throw new IllegalStateException("dom doesn't exist: " + dom);
        }

        boolean updateOnly = Boolean.parseBoolean(BaseServlet.optional(request, "updateOnly", "false"));

        if (CollectionUtils.isEmpty(newIPs)) {
            throw new IllegalArgumentException("Empty ip list");
        }

        if (updateOnly) {
            //make sure every IP is in the dom, otherwise refuse update
            List<IpAddress> oldIPs = domainsManager.getDomain(dom).allIPs();
            Collection diff = CollectionUtils.subtract(newIPs, oldIPs);
            if (diff.size() != 0) {
                throw new IllegalArgumentException("these IPs are not present: " + Arrays.toString(diff.toArray())
                        + ", if you want to add them, remove updateOnly flag");
            }
        }

        String key = UtilsAndCommons.getIPListStoreKey(domainsManager.getDomain(dom));

        long timestamp = System.currentTimeMillis();
        if (RaftCore.isLeader()) {
            RaftCore.OPERATE_LOCK.lock();
            try {
                final CountDownLatch countDownLatch = new CountDownLatch(RaftCore.getPeerSet().majorityCount());
                proxyParams.put("clientIP", NetUtils.localIP());
                proxyParams.put("notify", "true");

                proxyParams.put("term", String.valueOf(RaftCore.getPeerSet().local().term));
                proxyParams.put("timestamp", String.valueOf(timestamp));

                for (final RaftPeer peer : RaftCore.getPeers()) {
                    String server = peer.ip;
                    if (!server.contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {
                        server = server + UtilsAndCommons.CLUSTER_CONF_IP_SPLITER + RunningConfig.getServerPort();
                    }
                    String url = "http://" + server
                            + RunningConfig.getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/api/onAddIP4Dom";
                    HttpClient.asyncHttpPost(url, null, proxyParams, new AsyncCompletionHandler() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                                Loggers.SRV_LOG.warn("failed to add ip for dom: " + dom
                                        + ",ipList = " + ipList + ",code: " + response.getStatusCode()
                                        + ", caused " + response.getResponseBody() + ", server: " + peer.ip);
                                return 1;
                            }
                            countDownLatch.countDown();
                            return 0;
                        }
                    });
                }

                if (!countDownLatch.await(UtilsAndCommons.MAX_PUBLISH_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
                    Loggers.RAFT.info("data publish failed, key=" + key, ",notify timeout.");
                    throw new IllegalArgumentException("data publish failed, key=" + key);
                }

                Loggers.EVT_LOG.info("{" + dom + "} {POS} {IP-ADD}" + " new: "
                        + Arrays.toString(ipList.toArray()) + " operatorIP: "
                        + BaseServlet.optional(request, "clientIP", "unknown"));
            } finally {
                RaftCore.OPERATE_LOCK.unlock();
            }
        }

        return "ok";
    }

    @NeedAuth
    @RequestMapping("/addIP4Dom")
    public String addIP4Dom(HttpServletRequest request) throws Exception {
        return doAddIP4Dom(request);
    }

    @NeedAuth
    @RequestMapping("/replaceIP4Dom")
    public synchronized String replaceIP4Dom(HttpServletRequest request) throws Exception {
        String dom = BaseServlet.required(request, "dom");
        String cluster = BaseServlet.required(request, "cluster");

        List<String> ips = Arrays.asList(BaseServlet.required(request, "ipList").split(","));
        List<IpAddress> ipObjList = new ArrayList<IpAddress>(ips.size());
        for (String ip : ips) {
            IpAddress ipObj = IpAddress.fromJSON(ip);
            if (ipObj == null || ipObj.getPort() <= 0) {
                throw new IllegalArgumentException("malformed ip: " + ip + ", format: ip:port[_weight][_cluster]");
            }

            ipObj.setClusterName(cluster);
            ipObjList.add(ipObj);
        }

        if (CollectionUtils.isEmpty(ipObjList)) {
            throw new IllegalArgumentException("empty ip list");
        }

        domainsManager.easyReplaceIP4Dom(dom, cluster, ipObjList);

        return "ok";
    }

    @RequestMapping("/srvAllIP")
    public JSONObject srvAllIP(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();

        if (DistroMapper.getLocalhostIP().equals(UtilsAndCommons.LOCAL_HOST_IP)) {
            throw new Exception("invalid localhost ip: " + DistroMapper.getLocalhostIP());
        }

        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain domObj = (VirtualClusterDomain) domainsManager.getDomain(dom);
        String clusters = BaseServlet.optional(request, "clusters", StringUtils.EMPTY);

        if (domObj == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom not found: " + dom);
        }

        checkIfDisabled(domObj);

        long cacheMillis = Switch.getCacheMillis(dom);

        List<IpAddress> srvedIPs;

        if (StringUtils.isEmpty(clusters)) {
            srvedIPs = domObj.allIPs();
        } else {
            srvedIPs = domObj.allIPs(Arrays.asList(clusters.split(",")));
        }

        JSONArray ipArray = new JSONArray();

        for (IpAddress ip : srvedIPs) {
            JSONObject ipObj = new JSONObject();

            ipObj.put("ip", ip.getIp());
            ipObj.put("port", ip.getPort());
            ipObj.put("valid", ip.isValid());
            ipObj.put("weight", ip.getWeight());
            ipObj.put("doubleWeight", ip.getWeight());
            ipObj.put("instanceId", ip.generateInstanceId());
            ipObj.put("metadata", ip.getMetadata());
            ipArray.add(ipObj);
        }

        result.put("hosts", ipArray);

        result.put("dom", dom);
        result.put("clusters", clusters);
        result.put("cacheMillis", cacheMillis);
        result.put("lastRefTime", System.currentTimeMillis());
        result.put("checksum", domObj.getChecksum());
        result.put("allIPs", "true");

        return result;
    }

    @RequestMapping("/srvIPXT")
    @ResponseBody
    public JSONObject srvIPXT(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();

        if (DistroMapper.getLocalhostIP().equals(UtilsAndCommons.LOCAL_HOST_IP)) {
            throw new Exception("invalid localhost ip: " + DistroMapper.getLocalhostIP());
        }

        String dom = BaseServlet.required(request, "dom");

        VirtualClusterDomain domObj = (VirtualClusterDomain) domainsManager.getDomain(dom);
        String agent = request.getHeader("Client-Version");
        String clusters = BaseServlet.optional(request, "clusters", StringUtils.EMPTY);
        String clientIP = BaseServlet.optional(request, "clientIP", StringUtils.EMPTY);
        Integer udpPort = Integer.parseInt(BaseServlet.optional(request, "udpPort", "0"));
        String env = BaseServlet.optional(request, "env", StringUtils.EMPTY);
        String error = BaseServlet.optional(request, "unconsistentDom", StringUtils.EMPTY);
        boolean isCheck = Boolean.parseBoolean(BaseServlet.optional(request, "isCheck", "false"));

        String app = BaseServlet.optional(request, "app", StringUtils.EMPTY);

        String tenant = BaseServlet.optional(request, "tid", StringUtils.EMPTY);

        boolean healthyOnly = Boolean.parseBoolean(BaseServlet.optional(request, "healthOnly", "false"));

        if (!StringUtils.isEmpty(error)) {
            Loggers.ROLE_LOG.info("ENV-NOT-CONSISTENT", error);
        }

        if (domObj == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom not found: " + dom);
        }

        checkIfDisabled(domObj);

        long cacheMillis = Switch.getCacheMillis(dom);

        // now try to enable the push
        try {
            if (udpPort > 0 && PushService.canEnablePush(agent)) {
                PushService.addClient(dom,
                        clusters,
                        agent,
                        new InetSocketAddress(clientIP, udpPort),
                        pushDataSource,
                        tenant,
                        app);
                cacheMillis = Switch.getPushCacheMillis(dom);
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.error("VIPSRV-API", "failed to added push client", e);
            cacheMillis = Switch.getCacheMillis(dom);
        }

        List<IpAddress> srvedIPs;

        srvedIPs = domObj.srvIPs(clientIP, Arrays.asList(StringUtils.split(clusters, ",")));

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

            Loggers.SRV_LOG.warn("protect threshold reached, return all ips, " +
                    "dom: " + dom);
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
                ipObj.put("instanceId", ip.generateInstanceId());
                ipObj.put("metadata", ip.getMetadata());
                double weight = ip.getWeight();

                ipObj.put("weight", ip.getWeight());

                hosts.add(ipObj);

            }
        }

        result.put("hosts", hosts);

        result.put("dom", dom);
        result.put("clusters", clusters);
        result.put("cacheMillis", cacheMillis);
        result.put("lastRefTime", System.currentTimeMillis());
        result.put("checksum", domObj.getChecksum() + System.currentTimeMillis());
        result.put("useSpecifiedURL", false);
        result.put("env", env);

        return result;
    }

    @NeedAuth
    @RequestMapping("/remvIP4Dom")
    public String remvIP4Dom(HttpServletRequest request) throws Exception {
        String dom = BaseServlet.required(request, "dom");
        String ipListString = BaseServlet.required(request, "ipList");
        List<IpAddress> newIPs = new ArrayList<>();
        List<String> ipList = new ArrayList<>();
        if (Boolean.parseBoolean(BaseServlet.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            newIPs = JSON.parseObject(ipListString, new TypeReference<List<IpAddress>>() {
            });
        } else {
            ipList = Arrays.asList(ipListString.split(","));
        }

        List<IpAddress> ipObjList = new ArrayList<>(ipList.size());
        if (Boolean.parseBoolean(BaseServlet.optional(request, SwitchEntry.PARAM_JSON, Boolean.FALSE.toString()))) {
            ipObjList = newIPs;
        } else {
            for (String ip : ipList) {
                ipObjList.add(IpAddress.fromJSON(ip));
            }
        }

        domainsManager.easyRemvIP4Dom(dom, ipObjList);

        Loggers.EVT_LOG.info("{" + dom + "} {POS} {IP-REMV}" + " dead: "
                + Arrays.toString(ipList.toArray()) + " operator: "
                + BaseServlet.optional(request, "clientIP", "unknown"));

        return "ok";
    }

    @RequestMapping("/pushState")
    public JSONObject pushState(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        boolean detail = Boolean.parseBoolean(BaseServlet.optional(request, "detail", "false"));
        boolean reset = Boolean.parseBoolean(BaseServlet.optional(request, "reset", "false"));

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
        Boolean debug = Boolean.parseBoolean(BaseServlet.optional(request, "debug", "false"));

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
            String entry = BaseServlet.required(request, "entry");

            Datum datum = RaftCore.getDatum(UtilsAndCommons.DOMAINS_DATA_ID + ".00-00---000-VIPSRV_SWITCH_DOMAIN-000---00-00");
            SwitchDomain switchDomain = null;

            if (datum != null) {
                switchDomain = JSON.parseObject(datum.value, SwitchDomain.class);
            } else {
                Loggers.SRV_LOG.warn("datum: " + UtilsAndCommons.DOMAINS_DATA_ID + ".00-00---000-VIPSRV_SWITCH_DOMAIN-000---00-00 is null");
            }

            if (SwitchEntry.BATCH.equals(entry)) {
                //batch update
                SwitchDomain dom = JSON.parseObject(BaseServlet.required(request, "json"), SwitchDomain.class);
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
                Float threshold = Float.parseFloat(BaseServlet.required(request, "distroThreshold"));

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
                Boolean enable = Boolean.parseBoolean(BaseServlet.required(request, "enableAllDomNameCache"));
                Switch.setAllDomNameCache(enable);

                if (!debug) {
                    Switch.save();
                }

                return "ok";
            }

            if (entry.equals(SwitchEntry.INCREMENTAL_LIST)) {
                String action = BaseServlet.required(request, "action");
                List<String> doms = Arrays.asList(BaseServlet.required(request, "incrementalList").split(","));

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
                String action = BaseServlet.required(request, "action");
                List<String> whiteList = Arrays.asList(BaseServlet.required(request, "healthCheckWhiteList").split(","));

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
                long clientBeatInterval = Long.parseLong(BaseServlet.required(request, "clientBeatInterval"));
                Switch.setClientBeatInterval(clientBeatInterval);

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.PUSH_VERSION)) {
                String type = BaseServlet.required(request, "type");
                String version = BaseServlet.required(request, "version");

                if (!version.matches(UtilsAndCommons.VERSION_STRING_SYNTAX)) {
                    throw new IllegalArgumentException("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX);
                }

                if (StringUtils.equals(SwitchEntry.CLIENT_JAVA, type)) {
                    Switch.setPushJavaVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_PYTHON, type)) {
                    Switch.setPushPythonVersion(version);
                } else if (StringUtils.equals(SwitchEntry.CLIENT_C, type)) {
                    Switch.setPushCVersion(version);
                } else {
                    throw new IllegalArgumentException("unsupported client type: " + type);
                }

                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.TRAFFIC_SCHEDULING_VERSION)) {
                String type = BaseServlet.required(request, "type");
                String version = BaseServlet.required(request, "version");

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
                String dom = BaseServlet.optional(request, "dom", StringUtils.EMPTY);
                Long cacheMillis = Long.parseLong(BaseServlet.required(request, "millis"));

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
                String dom = BaseServlet.optional(request, "dom", StringUtils.EMPTY);
                Long cacheMillis = Long.parseLong(BaseServlet.required(request, "millis"));

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
                List<String> masters = Arrays.asList(BaseServlet.required(request, "names").split(","));

                Switch.setMasters(masters);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DISTRO)) {
                boolean enabled = Boolean.parseBoolean(BaseServlet.required(request, "enabled"));

                Switch.setDistroEnabled(enabled);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.CHECK)) {
                boolean enabled = Boolean.parseBoolean(BaseServlet.required(request, "enabled"));

                Switch.setHeathCheckEnabled(enabled);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DOM_STATUS_SYNC_PERIOD)) {
                Long millis = Long.parseLong(BaseServlet.required(request, "millis"));

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
                Long millis = Long.parseLong(BaseServlet.required(request, "millis"));

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
                Integer times = Integer.parseInt(BaseServlet.required(request, "times"));

                Switch.setCheckTimes(times);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.DISABLE_ADD_IP)) {
                boolean disableAddIP = Boolean.parseBoolean(BaseServlet.required(request, "disableAddIP"));

                Switch.setDisableAddIP(disableAddIP);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.ENABLE_CACHE)) {
                boolean enableCache = Boolean.parseBoolean(BaseServlet.required(request, "enableCache"));

                Switch.setEnableCache(enableCache);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.SEND_BEAT_ONLY)) {
                boolean sendBeatOnly = Boolean.parseBoolean(BaseServlet.required(request, "sendBeatOnly"));

                Switch.setSendBeatOnly(sendBeatOnly);
                if (!debug) {
                    Switch.save();
                }
                return "ok";
            }

            if (entry.equals(SwitchEntry.LIMITED_URL_MAP)) {
                Map<String, Integer> limitedUrlMap = new HashMap<>(16);
                String limitedUrls = BaseServlet.required(request, "limitedUrls");

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
                String enable = BaseServlet.required(request, "enableStandalone");

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

    @RequestMapping("/checkStatus")
    public JSONObject checkStatus(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("healthCheckEnabled", Switch.isHealthCheckEnabled());
        result.put("allDoms", domainsManager.getAllDomNames());

        List<String> doms = new ArrayList<String>();
        for (String dom : domainsManager.getAllDomNames()) {
            if (DistroMapper.responsible(dom)) {
                doms.add(dom);
            }
        }

        result.put("respDoms", doms);

        return result;
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

        boolean responsibleOnly = Boolean.parseBoolean(BaseServlet.optional(request, "responsibleOnly", "false"));
        boolean withOwner = Boolean.parseBoolean((BaseServlet.optional(request, "withOwner", "false")));

        List<String> doms = new ArrayList<String>();
        Set<String> domSet;

        domSet = domainsManager.getAllDomNames();
        for (String dom : domSet) {
            if (DistroMapper.responsible(dom) || !responsibleOnly) {
                if (withOwner) {
                    doms.add(dom + ":" + ArrayUtils.toString(domainsManager.getDomain(dom).getOwners()));
                } else {
                    doms.add(dom);
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
        String expr = BaseServlet.required(request, "expr");

        List<Domain> doms
                = domainsManager.searchDomains(".*" + expr + ".*");

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

    @RequestMapping("/getWeightsByIP")
    public JSONObject getWeightsByIP(HttpServletRequest request) {
        String ip = BaseServlet.required(request, "ip");

        Map<String, List<IpAddress>> dom2IPList = new HashMap<String, List<IpAddress>>(1024);
        for (String dom : domainsManager.getAllDomNames()) {
            Domain domObj = domainsManager.getDomain(dom);

            List<IpAddress> ipObjs = domObj.allIPs();
            for (IpAddress ipObj : ipObjs) {
                if (StringUtils.startsWith(ipObj.getIp() + ":" + ipObj.getPort(), ip)) {
                    List<IpAddress> list = dom2IPList.get(domObj.getName());

                    if (CollectionUtils.isEmpty(list)) {
                        list = new ArrayList<>();
                        dom2IPList.put(domObj.getName(), list);
                    }
                    list.add(ipObj);
                }
            }
        }

        JSONObject result = new JSONObject();
        JSONArray ipArray = new JSONArray();
        for (Map.Entry<String, List<IpAddress>> entry : dom2IPList.entrySet()) {
            for (IpAddress ipAddress : entry.getValue()) {

                JSONObject packet = new JSONObject();
                packet.put("dom", entry.getKey());
                packet.put("ip", ipAddress.getIp());
                packet.put("weight", ipAddress.getWeight());
                packet.put("port", ipAddress.getPort());
                packet.put("cluster", ipAddress.getClusterName());

                ipArray.add(packet);
            }
        }

        result.put("ips", ipArray);

        result.put("code", 200);
        result.put("successful", "success");

        return result;
    }


    private Cluster getClusterFromJson(String json) {
        JSONObject object = JSON.parseObject(json);
        String type = object.getJSONObject("healthChecker").getString("type");
        AbstractHealthCheckConfig abstractHealthCheckConfig;

        if (type.equals(HealthCheckType.HTTP.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthCheckConfig.Http.class);
        } else if (type.equals(HealthCheckType.TCP.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthCheckConfig.Tcp.class);
        } else if (type.equals(HealthCheckType.MYSQL.name())) {
            abstractHealthCheckConfig = JSON.parseObject(object.getString("healthChecker"), AbstractHealthCheckConfig.Mysql.class);
        } else {
            throw new IllegalArgumentException("can not prase cluster from json: " + json);
        }

        Cluster cluster = JSON.parseObject(json, Cluster.class);

        cluster.setHealthChecker(abstractHealthCheckConfig);
        return cluster;
    }

    public String doAddCluster4Dom(HttpServletRequest request) throws Exception {

        String dom = BaseServlet.required(request, "dom");
        String json = BaseServlet.optional(request, "clusterJson", StringUtils.EMPTY);

        VirtualClusterDomain domObj = (VirtualClusterDomain) domainsManager.getDomain(dom);

        if (domObj == null) {
            throw new IllegalArgumentException("dom not found: " + dom);
        }

        Cluster cluster = new Cluster();

        if (!StringUtils.isEmpty(json)) {
            try {
                cluster = getClusterFromJson(json);

            } catch (Exception e) {
                Loggers.SRV_LOG.warn("ADD-CLUSTER", "failed to parse json, try old format.");
            }
        } else {
            String cktype = BaseServlet.optional(request, "cktype", "TCP");
            String clusterName = BaseServlet.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
            String ipPort4Check = BaseServlet.optional(request, "ipPort4Check", "true");
            String path = BaseServlet.optional(request, "path", StringUtils.EMPTY);
            String headers = BaseServlet.optional(request, "headers", StringUtils.EMPTY);
            String nodegroup = BaseServlet.optional(request, "nodegroup", StringUtils.EMPTY);
            String expectedResponseCode = BaseServlet.optional(request, "expectedResponseCode", "200");
            int defIPPort = NumberUtils.toInt(BaseServlet.optional(request, "defIPPort", "-1"));
            int defCkport = NumberUtils.toInt(BaseServlet.optional(request, "defCkport", "80"));
            String siteGroup = BaseServlet.optional(request, "siteGroup", StringUtils.EMPTY);
            String submask = BaseServlet.optional(request, "submask", StringUtils.EMPTY);
            String clusterMetadataJson = BaseServlet.optional(request, "clusterMetadata", StringUtils.EMPTY);
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
                AbstractHealthCheckConfig.Http config = new AbstractHealthCheckConfig.Http();
                config.setType(cktype);
                config.setPath(path);
                config.setHeaders(headers);
                config.setExpectedResponseCode(Integer.parseInt(expectedResponseCode));
                cluster.setHealthChecker(config);
            } else if (StringUtils.equals(cktype, HealthCheckType.TCP.name())) {
                AbstractHealthCheckConfig.Tcp config = new AbstractHealthCheckConfig.Tcp();
                config.setType(cktype);
                cluster.setHealthChecker(config);
            } else if (StringUtils.equals(cktype, HealthCheckType.MYSQL.name())) {
                AbstractHealthCheckConfig.Mysql config = new AbstractHealthCheckConfig.Mysql();
                String cmd = BaseServlet.required(request, "cmd");
                String pwd = BaseServlet.required(request, "pwd");
                String user = BaseServlet.required(request, "user");

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

    /**
     * This API returns dom names only. you should use API: dom to retrieve dom details
     */
    @RequestMapping("/domList")
    public JSONObject domList(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        int page = Integer.parseInt(BaseServlet.required(request, "startPg"));
        int pageSize = Integer.parseInt(BaseServlet.required(request, "pgSize"));

        List<Domain> doms = domainsManager.getPagedDom(page, pageSize);
        if (CollectionUtils.isEmpty(doms)) {
            result.put("domList", Collections.emptyList());
            return result;
        }

        JSONArray domArray = new JSONArray();
        for (Domain dom : doms) {
            domArray.add(dom.getName());
        }

        result.put("domList", domArray);

        return result;
    }

    @RequestMapping("/distroStatus")
    public JSONObject distroStatus(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        String action = BaseServlet.optional(request, "action", "view");

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
        int ipCount = domainsManager.getIPCount();

        int responsibleDomCount = domainsManager.getResponsibleDoms().size();
        int responsibleIPCount = domainsManager.getResponsibleIPCount();

        result.put("domCount", domCount);
        result.put("ipCount", ipCount);
        result.put("responsibleDomCount", responsibleDomCount);
        result.put("responsibleIPCount", responsibleIPCount);
        result.put("cpu", SystemUtil.getCPU());
        result.put("load", SystemUtil.getLoad());
        result.put("mem", SystemUtil.getMem());

        return result;
    }

    @RequestMapping("/updateClusterConf")
    public JSONObject updateClusterConf(HttpServletRequest request) throws IOException {

        JSONObject result = new JSONObject();

        String ipSpliter = ",";

        String ips = BaseServlet.optional(request, "ips", "");
        String action = BaseServlet.required(request, "action");

        if (SwitchEntry.ACTION_ADD.equals(action)) {

            List<String> oldList =
                    IoUtils.readLines(new InputStreamReader(new FileInputStream(UtilsAndCommons.getConfFile()), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (String ip : oldList) {
                sb.append(ip).append("\r\n");
            }
            for (String ip : ips.split(ipSpliter)) {
                sb.append(ip).append("\r\n");
            }

            Loggers.SRV_LOG.info("UPDATE-CLUSTER", "new ips:" + sb.toString());
            IoUtils.writeStringToFile(new File(UtilsAndCommons.getConfFile()), sb.toString(), "utf-8");
            return result;
        }

        if (SwitchEntry.ACTION_REPLACE.equals(action)) {

            StringBuilder sb = new StringBuilder();
            for (String ip : ips.split(ipSpliter)) {
                sb.append(ip).append("\r\n");
            }
            Loggers.SRV_LOG.info("UPDATE-CLUSTER", "new ips:" + sb.toString());
            IoUtils.writeStringToFile(new File(UtilsAndCommons.getConfFile()), sb.toString(), "utf-8");
            return result;
        }

        if (SwitchEntry.ACTION_DELETE.equals(action)) {

            Set<String> removeIps = new HashSet<>();
            for (String ip : ips.split(ipSpliter)) {
                removeIps.add(ip);
            }

            List<String> oldList =
                    IoUtils.readLines(new InputStreamReader(new FileInputStream(UtilsAndCommons.getConfFile()), "utf-8"));

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

            IoUtils.writeStringToFile(new File(UtilsAndCommons.getConfFile()), sb.toString(), "utf-8");

            return result;
        }

        if (SwitchEntry.ACTION_VIEW.equals(action)) {

            List<String> oldList =
                    IoUtils.readLines(new InputStreamReader(new FileInputStream(UtilsAndCommons.getConfFile()), "utf-8"));
            result.put("list", oldList);

            return result;
        }

        throw new InvalidParameterException("action is not qualified, action: " + action);

    }

    @RequestMapping("/serverStatus")
    public String serverStatus(HttpServletRequest request) {
        String serverStatus = BaseServlet.required(request, "serverStatus");
        DistroMapper.onReceiveServerStatus(serverStatus);

        return "ok";
    }

    @RequestMapping("/reCalculateCheckSum4Dom")
    public JSONObject reCalculateCheckSum4Dom(HttpServletRequest request) {
        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        virtualClusterDomain.recalculateChecksum();

        JSONObject result = new JSONObject();

        result.put("checksum", virtualClusterDomain.getChecksum());

        return result;
    }

    @RequestMapping("/getDomString4MD5")
    public JSONObject getDomString4MD5(HttpServletRequest request) throws NacosException {

        JSONObject result = new JSONObject();
        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        if (virtualClusterDomain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "dom not found");
        }

        result.put("domString", virtualClusterDomain.getDomString());

        return result;
    }

    @RequestMapping("/getResponsibleServer4Dom")
    public JSONObject getResponsibleServer4Dom(HttpServletRequest request) {
        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

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
        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        if (virtualClusterDomain == null) {
            throw new IllegalArgumentException("dom not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsible", DistroMapper.responsible(dom));

        return result;
    }

    @RequestMapping("/domServeStatus")
    public JSONObject domServeStatus(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        //all ips, sites, disabled site, checkserver, appName
        String dom = BaseServlet.required(request, "dom");
        VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(dom);

        Map<String, Object> data = new HashMap<>(2);

        if (virtualClusterDomain == null) {
            result.put("success", false);
            result.put("data", data);
            result.put("errMsg", "dom does not exisit.");
            return result;
        }

        List<IpAddress> ipAddresses = virtualClusterDomain.allIPs();
        List<Map<String, Object>> allIPs = new ArrayList<>();

        for (IpAddress ip : ipAddresses) {

            Map<String, Object> ipPac = new HashMap<>(16);
            ipPac.put("ip", ip.getIp());
            ipPac.put("valid", ip.isValid());
            ipPac.put("port", ip.getPort());
            ipPac.put("marked", ip.isMarked());
            ipPac.put("cluster", ip.getClusterName());
            ipPac.put("weight", ip.getWeight());

            allIPs.add(ipPac);
        }

        List<String> checkServers = Arrays.asList(DistroMapper.mapSrv(dom));

        data.put("ips", allIPs);
        data.put("checkers", checkServers);
        result.put("data", data);
        result.put("success", true);
        result.put("errMsg", StringUtils.EMPTY);

        return result;
    }

    @RequestMapping("/domStatus")
    public String domStatus(HttpServletRequest request) {
        //format: dom1@@checksum@@@dom2@@checksum
        String domsStatusString = BaseServlet.required(request, "domsStatus");
        String serverIP = BaseServlet.optional(request, "clientIP", "");

        if (!NamingProxy.getServers().contains(serverIP)) {
            throw new IllegalArgumentException("ip: " + serverIP + " is not in serverlist");
        }

        try {
            DomainsManager.DomainChecksum checksums = JSON.parseObject(domsStatusString, DomainsManager.DomainChecksum.class);
            if (checksums == null) {
                Loggers.SRV_LOG.warn("DOMAIN-STATUS", "receive malformed data: " + null);
                return "fail";
            }

            for (Map.Entry<String, String> entry : checksums.domName2Checksum.entrySet()) {
                if (entry == null || StringUtils.isEmpty(entry.getKey()) || StringUtils.isEmpty(entry.getValue())) {
                    continue;
                }
                String dom = entry.getKey();
                String checksum = entry.getValue();
                Domain domain = domainsManager.getDomain(dom);

                if (domain == null) {
                    continue;
                }

                domain.recalculateChecksum();

                if (!checksum.equals(domain.getChecksum())) {
                    Loggers.SRV_LOG.debug("checksum of " + dom + " is not consistent, remote: " + serverIP + ",checksum: " + checksum + ", local: " + domain.getChecksum());
                    domainsManager.addUpdatedDom2Queue(dom, serverIP, checksum);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("DOMAIN-STATUS", "receive malformed data: " + domsStatusString, e);
        }

        return "ok";
    }

    @RequestMapping("/checkDataConsistence")
    public JSONObject checkDataConsistence(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();
        String domName = BaseServlet.optional(request, "dom", StringUtils.EMPTY);
        Boolean checkConsistence = Boolean.parseBoolean(BaseServlet.optional(request, "checkConsistence", "true"));

        if (!checkConsistence) {
            request.getParameterMap().put("isCheck", (String[]) Arrays.asList("true").toArray());

            srvIPXT(request);
            srvAllIP(request);
            return result;
        }

        if (StringUtils.isEmpty(domName)) {
            List<String> domNames = new ArrayList<String>(domainsManager.getAllDomNames());
            domName = domNames.get((int) (System.currentTimeMillis() % domNames.size()));
        }

        Domain domain = domainsManager.getDomain(domName);
        List<String> diff = new ArrayList<String>();
        String localDomString = "";

        for (String ip : NamingProxy.getServers()) {
            Map<String, String> tmpParams = new HashMap<String, String>(16);

            tmpParams.put("dom", domName);
            tmpParams.put("redirect", "1");

            String domString;
            try {
                domString = NamingProxy.reqAPI("dom", tmpParams, ip, false);
                JSONObject jsonObject = JSON.parseObject(domString);

                if (!jsonObject.getString("checksum").equals(domain.getChecksum())) {
                    diff.add(ip + "_" + domString);
                }

                if (ip.equals(NetUtils.localIP())) {
                    localDomString = domString;
                }

            } catch (Exception e) {
                Loggers.SRV_LOG.warn("STATUS-SYNCHRONIZE", "Failed to get domain status from " + ip, e);
            }

        }

        result.put("local dom", localDomString);
        result.put("diff list", diff);

        return result;
    }

    @RequestMapping("/containerNotify")
    public String containerNotify(HttpServletRequest request) {

        String type = BaseServlet.required(request, "type");
        String domain = BaseServlet.required(request, "domain");
        String ip = BaseServlet.required(request, "ip");
        String port = BaseServlet.required(request, "port");
        String state = BaseServlet.optional(request, "state", StringUtils.EMPTY);

        Loggers.SRV_LOG.info("CONTAINER_NOTFY", "received notify event, type:" + type + ", domain:" + domain +
                ", ip:" + ip + ", port:" + port + ", state:" + state);

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

    public void setDomainsManager(DomainsManager domainsManager) {
        this.domainsManager = domainsManager;
    }

}
