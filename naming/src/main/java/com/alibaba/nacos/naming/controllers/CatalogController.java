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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Domain;
import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckMode;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.ClusterInfo;
import com.alibaba.nacos.naming.pojo.IpAddressInfo;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.view.ServiceDetailView;
import com.alibaba.nacos.naming.view.ServiceView;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController

@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT)
public class CatalogController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "/serviceList")
    public JSONObject serviceList(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        JSONObject result = new JSONObject();

        int page = Integer.parseInt(WebUtils.required(request, "startPg"));
        int pageSize = Integer.parseInt(WebUtils.required(request, "pgSize"));
        String keyword = WebUtils.optional(request, "keyword", StringUtils.EMPTY);

        List<Domain> doms = new ArrayList<>();
        int total = domainsManager.getPagedDom(namespaceId, page - 1, pageSize, keyword, doms);

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

            serviceView.setHealthyInstanceCount(validCount);

            domArray.add(serviceView);
        }

        result.put("serviceList", domArray);
        result.put("count", total);

        return result;
    }

    @RequestMapping(value = "/serviceDetail")
    public ServiceDetailView serviceDetail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, "serviceName");
        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, serviceName);
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

        switch (SelectorType.valueOf(domain.getSelector().getType())) {
            case label:
                service.setSelector((LabelSelector) domain.getSelector());
                break;
            case none:
            case unknown:
            default:
                service.setSelector((NoneSelector) domain.getSelector());
                break;
        }

        detailView.setService(service);

        List<Cluster> clusters = new ArrayList<>();

        for (com.alibaba.nacos.naming.core.Cluster cluster : domain.getClusterMap().values()) {
            Cluster clusterView = new Cluster();
            clusterView.setName(cluster.getName());
            clusterView.setHealthChecker(cluster.getHealthChecker());
            clusterView.setMetadata(cluster.getMetadata());
            clusterView.setUseIPPort4Check(cluster.isUseIPPort4Check());
            clusterView.setDefaultPort(cluster.getDefIPPort());
            clusterView.setDefaultCheckPort(cluster.getDefCkport());
            clusterView.setServiceName(serviceName);
            clusters.add(clusterView);
        }

        detailView.setClusters(clusters);

        return detailView;
    }

    @RequestMapping(value = "/instanceList")
    public JSONObject instanceList(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        String serviceName = WebUtils.required(request, "serviceName");
        String clusterName = WebUtils.required(request, "clusterName");
        int page = Integer.parseInt(WebUtils.required(request, "startPg"));
        int pageSize = Integer.parseInt(WebUtils.required(request, "pgSize"));

        VirtualClusterDomain domain = (VirtualClusterDomain) domainsManager.getDomain(namespaceId, serviceName);
        if (domain == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        if (!domain.getClusterMap().containsKey(clusterName)) {
            throw new NacosException(NacosException.NOT_FOUND, "cluster " + clusterName + " is not found!");
        }

        List<Instance> instances = new ArrayList<>();

        for (IpAddress ipAddress : domain.getClusterMap().get(clusterName).allIPs()) {
            Instance instance = new Instance();
            instance.setIp(ipAddress.getIp());
            instance.setMetadata(ipAddress.getMetadata());
            instance.setHealthy(ipAddress.isValid());
            instance.setPort(ipAddress.getPort());
            instance.setInstanceId(ipAddress.getInstanceId());
            instance.setWeight(ipAddress.getWeight());
            instance.setEnabled(ipAddress.isEnabled());

            instances.add(instance);
        }

        int start = (page - 1) * pageSize;
        int end = page * pageSize;

        if (start < 0) {
            start = 0;
        }

        if (start > instances.size()) {
            start = instances.size();
        }

        if (end > instances.size()) {
            end = instances.size();
        }

        JSONObject result = new JSONObject();
        result.put("list", instances.subList(start, end));
        result.put("count", instances.size());

        return result;
    }

    @RequestMapping(value = "/services", method = RequestMethod.GET)
    public List<ServiceDetailInfo> listDetail(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, Constants.REQUEST_PARAM_NAMESPACE_ID,
            UtilsAndCommons.getDefaultNamespaceId());
        List<ServiceDetailInfo> serviceDetailInfoList = new ArrayList<>();

        domainsManager
                .getDomMap(namespaceId)
                .forEach(
                        (serviceName, domain) -> {

                            if (domain instanceof VirtualClusterDomain) {

                                VirtualClusterDomain virtualClusterDomain = (VirtualClusterDomain) domain;
                                ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
                                serviceDetailInfo.setServiceName(serviceName);
                                serviceDetailInfo.setMetadata(virtualClusterDomain.getMetadata());

                                Map<String, ClusterInfo> clusterInfoMap = getStringClusterInfoMap(virtualClusterDomain);
                                serviceDetailInfo.setClusterMap(clusterInfoMap);

                                serviceDetailInfoList.add(serviceDetailInfo);
                            }
                        });

        return serviceDetailInfoList;

    }

    /**
     * getStringClusterInfoMap
     *
     * @param virtualClusterDomain
     * @return
     */
    private Map<String, ClusterInfo> getStringClusterInfoMap(VirtualClusterDomain virtualClusterDomain) {
        Map<String, ClusterInfo> clusterInfoMap = new HashedMap();

        virtualClusterDomain.getClusterMap().forEach((clusterName, cluster) -> {

            ClusterInfo clusterInfo = new ClusterInfo();
            List<IpAddressInfo> ipAddressInfos = getIpAddressInfos(cluster.allIPs());
            clusterInfo.setHosts(ipAddressInfos);
            clusterInfoMap.put(clusterName, clusterInfo);

        });
        return clusterInfoMap;
    }

    /**
     * getIpAddressInfos
     *
     * @param ipAddresses
     * @return
     */
    private List<IpAddressInfo> getIpAddressInfos(List<IpAddress> ipAddresses) {
        List<IpAddressInfo> ipAddressInfos = new ArrayList<>();

        ipAddresses.forEach((ipAddress) -> {

            IpAddressInfo ipAddressInfo = new IpAddressInfo();
            ipAddressInfo.setIp(ipAddress.getIp());
            ipAddressInfo.setPort(ipAddress.getPort());
            ipAddressInfo.setMetadata(ipAddress.getMetadata());
            ipAddressInfo.setValid(ipAddress.isValid());
            ipAddressInfo.setWeight(ipAddress.getWeight());
            ipAddressInfo.setEnabled(ipAddress.isEnabled());
            ipAddressInfos.add(ipAddressInfo);

        });
        return ipAddressInfos;
    }

}
