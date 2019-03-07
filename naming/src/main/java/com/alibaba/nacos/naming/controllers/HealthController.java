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

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * Health status related operation controller
 *
 * @author nkorange
 * @author nanamikon
 * @since 0.8.0
 */
@RestController("namingHealthController")
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/health")
public class HealthController {

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private PushService pushService;

    @RequestMapping("/server")
    public JSONObject server(HttpServletRequest request) {
        JSONObject result = new JSONObject();
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total services: raft " + serviceManager.getServiceCount()
            + ", local port:" + RunningConfig.getServerPort());
        return result;
    }

    @RequestMapping(value = {"", "/instance"}, method = RequestMethod.PUT)
    public String update(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));
        boolean valid = Boolean.valueOf(WebUtils.required(request, "valid"));
        String clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME
            , UtilsAndCommons.DEFAULT_CLUSTER_NAME);

        if (!distroMapper.responsible(serviceName)) {
            String server = distroMapper.mapSrv(serviceName);
            Loggers.EVT_LOG.info("I'm not responsible for " + serviceName + ", proxy it to " + server);
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
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/health";
            HttpClient.HttpResult httpResult = HttpClient.httpPost(url, null, proxyParams);

            if (httpResult.code != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("failed to proxy health update to " + server + ", service: " + serviceName);
            }
        } else {
            Service service = serviceManager.getService(namespaceId, serviceName);
            // Only health check "none" need update health status with api
            if (HealthCheckType.NONE.name().equals(service.getClusterMap().get(clusterName).getHealthChecker().getType())) {
                for (Instance instance : service.allIPs(Lists.newArrayList(clusterName))) {
                    if (instance.getIp().equals(ip) && instance.getPort() == port) {
                        instance.setHealthy(valid);
                        Loggers.EVT_LOG.info((valid ? "[IP-ENABLED]" : "[IP-DISABLED]") + " ips: "
                            + instance.getIp() + ":" + instance.getPort() + "@" + instance.getClusterName()
                            + ", service: " + serviceName + ", msg: update thought HealthController api");
                        pushService.serviceChanged(namespaceId, service.getName());
                        break;
                    }
                }
            } else {
                throw new IllegalArgumentException("health check mode 'client' and 'server' are not supported, service: " + serviceName);
            }
        }
        return "ok";
    }
}
