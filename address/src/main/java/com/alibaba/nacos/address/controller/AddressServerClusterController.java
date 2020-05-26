/*
 * Copyright (C) 2019 the original author or authors.
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
package com.alibaba.nacos.address.controller;

import com.alibaba.nacos.address.component.AddressServerGeneratorManager;
import com.alibaba.nacos.address.component.AddressServerManager;
import com.alibaba.nacos.address.constant.AddressServerConstants;
import com.alibaba.nacos.address.misc.Loggers;
import com.alibaba.nacos.address.util.AddressServerParamCheckUtil;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author pbting
 * @date 2019-06-10 9:59 AM
 * @since 1.1.0
 */
@RestController
@RequestMapping({AddressServerConstants.ADDRESS_SERVER_REQUEST_URL + "/nodes"})
public class AddressServerClusterController {

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private AddressServerManager addressServerManager;

    @Autowired
    private AddressServerGeneratorManager addressServerGeneratorManager;

    /**
     * @param product Ip list of products to be associated
     * @param cluster Ip list of product cluster to be associated
     * @param ips     will post ip list.
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResponseEntity postCluster(@RequestParam(required = false) String product,
                                      @RequestParam(required = false) String cluster,
                                      @RequestParam(name = "ips") String ips) {

        //1. prepare the storage name for product and cluster
        String productName = addressServerGeneratorManager.generateProductName(product);
        String clusterName = addressServerManager.getDefaultClusterNameIfEmpty(cluster);

        //2. prepare the response name for product and cluster to client
        String rawProductName = addressServerManager.getRawProductName(product);
        String rawClusterName = addressServerManager.getRawClusterName(cluster);
        Loggers.addressLogger.info("put cluster node,the cluster name is " + cluster + "; the product name=" + product + "; the ip list=" + ips);
        ResponseEntity responseEntity;
        try {
            String serviceName = addressServerGeneratorManager.generateNacosServiceName(productName);

            Cluster clusterObj = new Cluster();
            clusterObj.setName(clusterName);
            clusterObj.setHealthChecker(new AbstractHealthChecker.None());
            serviceManager.createServiceIfAbsent(Constants.DEFAULT_NAMESPACE_ID, serviceName, false, clusterObj);
            String[] ipArray = addressServerManager.splitIps(ips);
            String checkResult = AddressServerParamCheckUtil.checkIps(ipArray);
            if (AddressServerParamCheckUtil.CHECK_OK.equals(checkResult)) {
                List<Instance> instanceList = addressServerGeneratorManager.generateInstancesByIps(serviceName, rawProductName, clusterName, ipArray);
                for (Instance instance : instanceList) {
                    serviceManager.registerInstance(Constants.DEFAULT_NAMESPACE_ID, serviceName, instance);
                }
                responseEntity = ResponseEntity.ok("product=" + rawProductName + ",cluster=" + rawClusterName + "; put success with size=" + instanceList.size());
            } else {
                responseEntity = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(checkResult);
            }
        } catch (Exception e) {
            responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        return responseEntity;
    }

    /**
     * @param product Ip list of products to be associated
     * @param cluster Ip list of product cluster to be associated
     * @param ips     will delete ips.
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public ResponseEntity deleteCluster(@RequestParam(required = false) String product,
                                        @RequestParam(required = false) String cluster,
                                        @RequestParam String ips) {
        //1. prepare the storage name for product and cluster
        String productName = addressServerGeneratorManager.generateProductName(product);
        String clusterName = addressServerManager.getDefaultClusterNameIfEmpty(cluster);

        //2. prepare the response name for product and cluster to client
        String rawProductName = addressServerManager.getRawProductName(product);
        String rawClusterName = addressServerManager.getRawClusterName(cluster);
        ResponseEntity responseEntity = ResponseEntity.status(HttpStatus.OK).body("product=" + rawProductName + ", cluster=" + rawClusterName + " delete success.");
        try {

            String serviceName = addressServerGeneratorManager.generateNacosServiceName(productName);
            Service service = serviceManager.getService(Constants.DEFAULT_NAMESPACE_ID, serviceName);

            if (service == null) {
                responseEntity = ResponseEntity.status(HttpStatus.NOT_FOUND).body("product=" + rawProductName + " not found.");
            } else {

                if (StringUtils.isBlank(ips)) {
                    // delete all ips from the cluster
                    responseEntity = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ips must not be empty.");
                } else {
                    // delete specified ip list
                    String[] ipArray = addressServerManager.splitIps(ips);
                    String checkResult = AddressServerParamCheckUtil.checkIps(ipArray);
                    if (AddressServerParamCheckUtil.CHECK_OK.equals(checkResult)) {
                        List<Instance> instanceList = addressServerGeneratorManager.generateInstancesByIps(serviceName, rawProductName, clusterName, ipArray);
                        serviceManager.removeInstance(Constants.DEFAULT_NAMESPACE_ID, serviceName, false, instanceList.toArray(new Instance[instanceList.size()]));
                    } else {
                        responseEntity = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(checkResult);
                    }
                }
            }
        } catch (Exception e) {

            responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getCause());
        }

        return responseEntity;
    }

}
