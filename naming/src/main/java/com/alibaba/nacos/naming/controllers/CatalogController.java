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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.healthcheck.HealthCheckTask;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.ClusterInfo;
import com.alibaba.nacos.naming.pojo.IpAddressInfo;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceView;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @GetMapping("/service")
    public ObjectNode serviceDetail(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                    String serviceName) throws NacosException {

        Service detailedService = serviceManager.getService(namespaceId, serviceName);

        if (detailedService == null) {
            throw new NacosException(NacosException.NOT_FOUND, "service " + serviceName + " is not found!");
        }

        ObjectNode detailView = JacksonUtils.createEmptyJsonNode();

        ObjectNode serviceObject = JacksonUtils.createEmptyJsonNode();
        serviceObject.put("name", NamingUtils.getServiceName(serviceName));
        serviceObject.put("protectThreshold", detailedService.getProtectThreshold());
        serviceObject.put("groupName", NamingUtils.getGroupName(serviceName));
        serviceObject.replace("selector", JacksonUtils.transferToJsonNode(detailedService.getSelector()));
        serviceObject.replace("metadata", JacksonUtils.transferToJsonNode(detailedService.getMetadata()));

        detailView.replace("service", serviceObject);

        List<Cluster> clusters = new ArrayList<>();

        for (com.alibaba.nacos.naming.core.Cluster cluster : detailedService.getClusterMap().values()) {
            Cluster clusterView = new Cluster();
            clusterView.setName(cluster.getName());
            clusterView.setHealthChecker(cluster.getHealthChecker());
            clusterView.setMetadata(cluster.getMetadata());
            clusterView.setUseIPPort4Check(cluster.isUseIPPort4Check());
            clusterView.setDefaultPort(cluster.getDefaultPort());
            clusterView.setDefaultCheckPort(cluster.getDefaultCheckPort());
            clusterView.setServiceName(cluster.getService().getName());
            clusters.add(clusterView);
        }

        detailView.replace("clusters", JacksonUtils.transferToJsonNode(clusters));

        return detailView;
    }

    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @RequestMapping(value = "/instances")
    public ObjectNode instanceList(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                   @RequestParam String serviceName,
                                   @RequestParam String clusterName,
                                   @RequestParam(name = "pageNo") int page,
                                   @RequestParam int pageSize) throws NacosException {


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

        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("list", JacksonUtils.transferToJsonNode(instances.subList(start, end)));
        result.put("count", instances.size());

        return result;
    }

    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @GetMapping("/services")
    public Object listDetail(@RequestParam(required = false) boolean withInstances,
                             @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                             @RequestParam(required = false) int pageNo,
                             @RequestParam(required = false) int pageSize,
                             @RequestParam(name = "serviceNameParam", defaultValue = StringUtils.EMPTY) String serviceName,
                             @RequestParam(name = "groupNameParam", defaultValue = StringUtils.EMPTY) String groupName,
                             @RequestParam(name = "instance", defaultValue = StringUtils.EMPTY) String containedInstance,
                             @RequestParam(required = false) boolean hasIpCount
    ) {

        String param = StringUtils.isBlank(serviceName) && StringUtils.isBlank(groupName) ?
            StringUtils.EMPTY : NamingUtils.getGroupedName(serviceName, groupName);

        if (withInstances) {
            List<ServiceDetailInfo> serviceDetailInfoList = new ArrayList<>();

            List<Service> services = new ArrayList<>(8);
            serviceManager.getPagedService(namespaceId, pageNo, pageSize, param, StringUtils.EMPTY, services, false);

            for (Service service : services) {
                ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
                serviceDetailInfo.setServiceName(NamingUtils.getServiceName(service.getName()));
                serviceDetailInfo.setGroupName(NamingUtils.getGroupName(service.getName()));
                serviceDetailInfo.setMetadata(service.getMetadata());

                Map<String, ClusterInfo> clusterInfoMap = getStringClusterInfoMap(service);
                serviceDetailInfo.setClusterMap(clusterInfoMap);

                serviceDetailInfoList.add(serviceDetailInfo);
            }

            return serviceDetailInfoList;
        }

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        List<Service> services = new ArrayList<>();

        int total = serviceManager.getPagedService(namespaceId, pageNo - 1, pageSize, param, containedInstance, services, hasIpCount);

        if (CollectionUtils.isEmpty(services)) {
            result.replace("serviceList", JacksonUtils.transferToJsonNode(Collections.emptyList()));
            result.put("count", 0);
            return result;
        }

        List<ServiceView> serviceViews = new LinkedList<>();
        for (Service service : services) {
            ServiceView serviceView = new ServiceView();
            serviceView.setName(NamingUtils.getServiceName(service.getName()));
            serviceView.setGroupName(NamingUtils.getGroupName(service.getName()));
            serviceView.setClusterCount(service.getClusterMap().size());
            serviceView.setIpCount(service.allIPs().size());
            serviceView.setHealthyInstanceCount(service.healthyInstanceCount());
            serviceView.setTriggerFlag(service.triggerFlag() ? "true" : "false");
            serviceViews.add(serviceView);
        }

        result.replace("serviceList", JacksonUtils.transferToJsonNode(serviceViews));
        result.put("count", total);

        return result;
    }

    @RequestMapping("/rt/service")
    public ObjectNode rt4Service(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);

        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new IllegalArgumentException("request service doesn't exist");
        }

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        ArrayNode clusters = JacksonUtils.createEmptyArrayNode();
        for (Map.Entry<String, com.alibaba.nacos.naming.core.Cluster> entry : service.getClusterMap().entrySet()) {
            ObjectNode packet = JacksonUtils.createEmptyJsonNode();
            HealthCheckTask task = entry.getValue().getHealthCheckTask();

            packet.put("name", entry.getKey());
            packet.put("checkRTBest", task.getCheckRTBest());
            packet.put("checkRTWorst", task.getCheckRTWorst());
            packet.put("checkRTNormalized", task.getCheckRTNormalized());

            clusters.add(packet);
        }
        result.replace("clusters", clusters);
        return result;
    }

    /**
     * getStringClusterInfoMap
     *
     * @param service
     * @return
     */
    private Map<String, ClusterInfo> getStringClusterInfoMap(Service service) {
        Map<String, ClusterInfo> clusterInfoMap = new HashMap<>(8);

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
            ipAddressInfo.setValid(ipAddress.isHealthy());
            ipAddressInfo.setWeight(ipAddress.getWeight());
            ipAddressInfo.setEnabled(ipAddress.isEnabled());
            ipAddressInfos.add(ipAddressInfo);

        });
        return ipAddressInfos;
    }

}
