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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
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
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @author nanamikon
 * @since 0.8.0
 */
@RestController("namingHealthController")
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/health")
public class HealthController {
    @Autowired
    private DomainsManager domainsManager;

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT})
    public String update(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String dom = WebUtils.required(request, "serviceName");
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));
        boolean valid = Boolean.valueOf(WebUtils.required(request, "valid"));
        String clusterName = WebUtils.optional(request, "clusterName", UtilsAndCommons.DEFAULT_CLUSTER_NAME);

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
                + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/health";
            HttpClient.HttpResult httpResult = HttpClient.httpPost(url, null, proxyParams);

            if (httpResult.code != HttpURLConnection.HTTP_OK) {
                throw new IllegalArgumentException("failed to proxy health update to " + server + ", dom: " + dom);
            }
        } else {
            VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, dom);
            // Only health check "none" need update health status with api
            if (!virtualClusterDomain.getEnableHealthCheck() && !virtualClusterDomain.getEnableClientBeat()) {
                for (IpAddress ipAddress : virtualClusterDomain.allIPs(Lists.newArrayList(clusterName))) {
                    if (ipAddress.getIp().equals(ip) && ipAddress.getPort() == port) {
                        ipAddress.setValid(valid);
                        Loggers.EVT_LOG.info((valid ? "[IP-ENABLED]" : "[IP-DISABLED]") + " ips: "
                            + ipAddress.getIp() + ":" + ipAddress.getPort() + "@" + ipAddress.getClusterName()
                            + ", dom: " + dom + ", msg: update thought HealthController api");
                        PushService.domChanged(namespaceId, virtualClusterDomain.getName());
                        break;
                    }
                }
            } else {
                throw new IllegalArgumentException("health check mode 'client' and 'server' are not supported  , dom: " + dom);
            }
        }
        return "ok";
    }
}
