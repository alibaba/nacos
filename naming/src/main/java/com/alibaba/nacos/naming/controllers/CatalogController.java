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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog")
public class CatalogController {

    @Autowired
    protected ServiceManager serviceManager;

    @RequestMapping(value = "/serviceList")
    public JSONObject serviceList(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        JSONObject result = new JSONObject();

        int page = Integer.parseInt(WebUtils.required(request, "startPg"));
        int pageSize = Integer.parseInt(WebUtils.required(request, "pgSize"));
        String keyword = WebUtils.optional(request, "keyword", StringUtils.EMPTY);

        List<Service> services = new ArrayList<>();
        int total = serviceManager.getPagedService(namespaceId, page - 1, pageSize, keyword, services);

        if (CollectionUtils.isEmpty(services)) {
            result.put("serviceList", Collections.emptyList());
            result.put("count", 0);
            return result;
        }

        JSONArray serviceJsonArray = new JSONArray();
        for (Service service : services) {
            ServiceView serviceView = new ServiceView();
            serviceView.setName(UtilsAndCommons.getServiceName(service.getName()));
            serviceView.setGroupName(UtilsAndCommons.getGroupName(service.getName()));
            serviceView.setClusterCount(service.getClusterMap().size());
            serviceView.setIpCount(service.allIPs().size());

            // FIXME should be optimized:
            int validCount = 0;
            for (Instance instance : service.allIPs()) {
                if (instance.isValid()) {
                    validCount++;
                }
            }

            serviceView.setHealthyInstanceCount(validCount);

            serviceJsonArray.add(serviceView);
        }

        result.put("serviceList", serviceJsonArray);
        result.put("count", total);

        return result;
    }

    @RequestMapping(value = "/serviceDetail")
    public ServiceDetailView serviceDetail(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        ServiceDetailView detailView = new ServiceDetailView();

        detailView.setService(service);

        List<Cluster> clusters = new ArrayList<>();

        for (com.alibaba.nacos.naming.core.Cluster cluster : service.getClusterMap().values()) {
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

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String clusterName = WebUtils.required(request, CommonParams.CLUSTER_NAME);
        int page = Integer.parseInt(WebUtils.required(request, "startPg"));
        int pageSize = Integer.parseInt(WebUtils.required(request, "pgSize"));

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND, "serivce " + serviceName + " is not found!");
        }

        if (!service.getClusterMap().containsKey(clusterName)) {
            throw new NacosException(NacosException.NOT_FOUND, "cluster " + clusterName + " is not found!");
        }

        List<Instance> instances = service.getClusterMap().get(clusterName).allIPs();

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

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        List<ServiceDetailInfo> serviceDetailInfoList = new ArrayList<>();

        serviceManager
            .getServiceMap(namespaceId)
            .forEach(
                (serviceName, service) -> {

                    ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
                    serviceDetailInfo.setServiceName(UtilsAndCommons.getServiceName(serviceName));
                    serviceDetailInfo.setGroupName(UtilsAndCommons.getGroupName(serviceName));
                    serviceDetailInfo.setMetadata(service.getMetadata());

                    Map<String, ClusterInfo> clusterInfoMap = getStringClusterInfoMap(service);
                    serviceDetailInfo.setClusterMap(clusterInfoMap);

                    serviceDetailInfoList.add(serviceDetailInfo);
                });

        return serviceDetailInfoList;

    }

    @RequestMapping("/rt4Service")
    public JSONObject rt4Service(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new IllegalArgumentException("request service doesn't exist");
        }

        JSONObject result = new JSONObject();

        JSONArray clusters = new JSONArray();
        for (Map.Entry<String, com.alibaba.nacos.naming.core.Cluster> entry : service.getClusterMap().entrySet()) {
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

    @RequestMapping("/getServicesByIP")
    public JSONObject getServicesByIP(HttpServletRequest request) {
        String ip = WebUtils.required(request, "ip");

        Set<String> serviceNames = new HashSet<>();
        Map<String, Set<String>> serviceNameMap = serviceManager.getAllServiceNames();

        for (String namespaceId : serviceNameMap.keySet()) {
            for (String serviceName : serviceNameMap.get(namespaceId)) {
                Service service = serviceManager.getService(namespaceId, serviceName);
                List<Instance> instances = service.allIPs();
                for (Instance instance : instances) {
                    if (ip.contains(":")) {
                        if (StringUtils.equals(instance.getIp() + ":" + instance.getPort(), ip)) {
                            serviceNames.add(namespaceId + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR + service.getName());
                        }
                    } else {
                        if (StringUtils.equals(instance.getIp(), ip)) {
                            serviceNames.add(namespaceId + UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR + service.getName());
                        }
                    }
                }
            }
        }

        JSONObject result = new JSONObject();

        result.put("doms", serviceNames);

        return result;
    }

    /**
     * getStringClusterInfoMap
     *
     * @param service
     * @return
     */
    private Map<String, ClusterInfo> getStringClusterInfoMap(Service service) {
        Map<String, ClusterInfo> clusterInfoMap = new HashedMap();

        service.getClusterMap().forEach((clusterName, cluster) -> {

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
     * @param instances
     * @return
     */
    private List<IpAddressInfo> getIpAddressInfos(List<Instance> instances) {
        List<IpAddressInfo> ipAddressInfos = new ArrayList<>();

        instances.forEach((ipAddress) -> {

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
