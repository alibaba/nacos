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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.web.ApiCommands;
import com.alibaba.nacos.naming.web.OverrideParameterRequestWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT)
public class InstanceController extends ApiCommands {

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
        return deRegService(request);
    }

    @RequestMapping(value = {"/instance/update", "instance"}, method = RequestMethod.PUT)
    public String update(HttpServletRequest request) throws Exception {
        return regService(request);
    }

    @RequestMapping(value = {"/instances", "/instance/list"}, method = RequestMethod.GET)
    public JSONObject queryList(HttpServletRequest request) throws Exception {
        return srvIPXT(OverrideParameterRequestWrapper.buildRequest(request, "dom", WebUtils.required(request, "serviceName")));
    }

    @RequestMapping(value = "/instance", method = RequestMethod.GET)
    public JSONObject queryDetail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, Constants.REQUEST_PARAM_SERVICE_NAME);
        String cluster = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, serviceName);
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
        return clientBeat(request);
    }
}
