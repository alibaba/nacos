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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.nacos.naming.core.DomainsManager;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.core.VirtualClusterDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.ClusterInfo;
import com.alibaba.nacos.naming.pojo.IpAddressInfo;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;

/**
 * @author dungu.zpf
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/catalog")
public class CatalogController {

    @Autowired
    protected DomainsManager domainsManager;

    @RequestMapping(value = "/services", method = RequestMethod.GET)
    public List<ServiceDetailInfo> listDetail(HttpServletRequest request) {

        List<ServiceDetailInfo> serviceDetailInfoList = new ArrayList<>();

        domainsManager
            .getRaftDomMap()
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

            ipAddressInfos.add(ipAddressInfo);

        });
        return ipAddressInfos;
    }
}
