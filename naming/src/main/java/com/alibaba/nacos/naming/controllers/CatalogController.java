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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.naming.core.Domain;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckMode;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.view.ServiceDetailView;
import com.alibaba.nacos.naming.view.ServiceView;
import com.alibaba.nacos.naming.web.BaseServlet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author dungu.zpf
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)
public class CatalogController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "/serviceList")
    public JSONObject serviceList(HttpServletRequest request) throws Exception {

        JSONObject result = new JSONObject();

        int page = Integer.parseInt(BaseServlet.required(request, "startPg"));
        int pageSize = Integer.parseInt(BaseServlet.required(request, "pgSize"));
        String keyword = BaseServlet.optional(request, "keyword", StringUtils.EMPTY);

        List<Domain> doms = new ArrayList<>();
        int total = domainsManager.getPagedDom(page, pageSize, keyword, doms);

        if (CollectionUtils.isEmpty(doms)) {
            result.put("serviceList", Collections.emptyList());
            result.put("count", 0);
            return result;
        }

        JSONArray domArray = new JSONArray();
        for (Domain dom : doms) {
            VirtualClusterDomain vDomain = (VirtualClusterDomain) dom;
            ServiceView serviceView = new ServiceView();
            serviceView.setName(vDomain.getName());
            serviceView.setClusterCount(vDomain.getClusterMap().size());
            serviceView.setIpCount(vDomain.allIPs().size());

            // FIXME should be optimized:
            int validCount = 0;
            for (IpAddress ipAddress : vDomain.allIPs()) {
                if (ipAddress.isValid()) {
                    validCount++;
                }
            }

            double validRatio = validCount * 1.0 / vDomain.allIPs().size();

            // FIXME:
            validRatio = RandomUtils.nextDouble(0, 1.2);

            if (validRatio > 0.9) {
                serviceView.setStatus("优");
            } else if (validRatio > 0.6) {
                serviceView.setStatus("良");
            } else if (validRatio > 0.3) {
                serviceView.setStatus("中");
            } else {
                serviceView.setStatus("差");
            }

            domArray.add(serviceView);
        }

        result.put("serviceList", domArray);
        result.put("count", total);

        return result;
    }

    @RequestMapping(value = "/serviceDetail")
    public ServiceDetailView serviceDetail(HttpServletRequest request) throws Exception {

        String serviceName = BaseServlet.required(request, "serviceName");
        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        ServiceDetailView detailView = new ServiceDetailView();

        Service service = new Service(serviceName);
        service.setName(serviceName);
        service.setProtectThreshold(domain.getProtectThreshold());
        service.setHealthCheckMode(HealthCheckMode.none.name());
        if (domain.getEnableHealthCheck()) {
            service.setHealthCheckMode(HealthCheckMode.server.name());
        }
        if (domain.getEnableClientBeat()) {
            service.setHealthCheckMode(HealthCheckMode.client.name());
        }
        service.setMetadata(domain.getMetadata());
        detailView.setService(service);

        List<Cluster> clusters = new ArrayList<>();

        clusters.addAll(domain.getClusterMap().values());

        detailView.setClusters(clusters);

        Map<String, List<Instance>> instanceMap = new HashMap<String, List<Instance>>(domain.allIPs().size());
        for (IpAddress ipAddress : domain.allIPs()) {
            if (!instanceMap.containsKey(ipAddress.getClusterName())) {
                instanceMap.put(ipAddress.getClusterName(), new ArrayList<Instance>());
            }

            instanceMap.get(ipAddress.getClusterName()).add(ipAddress);
        }

        detailView.setInstances(instanceMap);

        return detailView;
    }
}
