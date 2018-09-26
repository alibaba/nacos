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
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.healthcheck.HealthCheckMode;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.ApiCommands;
import com.alibaba.nacos.naming.web.BaseServlet;
import com.alibaba.nacos.naming.web.MockHttpRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dungu.zpf
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT)
public class InstanceController extends ApiCommands {

    @RequestMapping(value = "/instance", method = RequestMethod.PUT)
    public String register(HttpServletRequest request) throws Exception {

        Map<String, String[]> params = new HashMap<>(request.getParameterMap());
        MockHttpRequest mockHttpRequest = MockHttpRequest.buildRequest(params);

        String serviceJson = BaseServlet.optional(request, "service", StringUtils.EMPTY);
        String clusterJson = BaseServlet.optional(request, "cluster", StringUtils.EMPTY);

        // set service info:
        if (StringUtils.isNotEmpty(serviceJson)) {
            JSONObject service = JSON.parseObject(serviceJson);
            mockHttpRequest.addParameter("dom", service.getString("name"));
            mockHttpRequest.addParameter("app", service.getString("app"));
            mockHttpRequest.addParameter("group", service.getString("group"));
            mockHttpRequest.addParameter("protectThreshold", service.getString("protectThreshold"));

            String healthCheckMode = service.getString("healthCheckMode");

            if (HealthCheckMode.server.name().equals(healthCheckMode)) {
                mockHttpRequest.addParameter("enableHealthCheck", "true");
            }

            if (HealthCheckMode.client.name().equals(healthCheckMode)) {
                mockHttpRequest.addParameter("enableClientBeat", "true");
            }

            if (HealthCheckMode.none.name().equals(healthCheckMode)) {
                mockHttpRequest.addParameter("enableHealthCheck", "false");
                mockHttpRequest.addParameter("enableClientBeat", "false");
            }

            mockHttpRequest.addParameter("serviceMetadata", service.getString("metadata"));
        } else {
            mockHttpRequest.addParameter("dom", BaseServlet.required(request, "serviceName"));
        }

        // set cluster info:
        if (StringUtils.isNotEmpty(clusterJson)) {
            JSONObject cluster = JSON.parseObject(clusterJson);
            String clusterName = cluster.getString("name");
            if (StringUtils.isEmpty(clusterName)) {
                clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
            }
            mockHttpRequest.addParameter("clusterName", clusterName);

            JSONObject healthChecker = cluster.getJSONObject("healthChecker");
            if (healthChecker == null) {
                mockHttpRequest.addParameter("cktype", "TCP");
            } else {
                for (String key : healthChecker.keySet()) {
                    mockHttpRequest.addParameter(key, healthChecker.getString(key));
                }
                mockHttpRequest.addParameter("cktype", healthChecker.getString("type"));
            }

            mockHttpRequest.addParameter("cluster", StringUtils.EMPTY);
            mockHttpRequest.addParameter("defIPPort", cluster.getString("defaultPort"));
            mockHttpRequest.addParameter("defCkport", cluster.getString("defaultCheckPort"));
            mockHttpRequest.addParameter("ipPort4Check", cluster.getString("userIPPort4Check"));
            mockHttpRequest.addParameter("clusterMetadata", cluster.getString("metadata"));

        }

        return regService(mockHttpRequest);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.DELETE)
    public String deregister(HttpServletRequest request) throws Exception {
        return deRegService(request);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.POST)
    public String update(HttpServletRequest request) throws Exception {
        return addIP4Dom(request);
    }

    @RequestMapping(value = {"/instances", "/instance/list"}, method = RequestMethod.GET)
    public JSONObject queryList(HttpServletRequest request) throws Exception {

        Map<String, String[]> params = new HashMap<>(request.getParameterMap());
        params.put("dom", params.get("serviceName"));
        MockHttpRequest mockHttpRequest = MockHttpRequest.buildRequest(params);

        return srvIPXT(mockHttpRequest);
    }

    @RequestMapping(value = "/instance", method = RequestMethod.GET)
    public JSONObject queryDetail(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");
        String cluster = BaseServlet.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = BaseServlet.required(request, "ip");
        int port = Integer.parseInt(BaseServlet.required(request, "port"));

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
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
}
