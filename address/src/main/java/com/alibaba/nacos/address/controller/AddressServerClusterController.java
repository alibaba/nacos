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
import com.alibaba.nacos.address.util.AddressServerParamCheckUtil;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;

/**
 * @author pbting
 * @date 2019-06-10 9:59 AM
 */
@RestController
@RequestMapping({AddressServerConstants.ADDRESS_SERVER_REQUEST_URL + "/instance", AddressServerConstants.ADDRESS_SERVER_REQUEST_URL_WITH_NONE_PREFIX + "/instance"})
public class AddressServerClusterController {

    private static final Logger logger = LoggerFactory.getLogger(AddressServerClusterController.class);

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private AddressServerManager addressServerManager;

    @Autowired
    private AddressServerGeneratorManager addressServerGeneratorManager;

    /**
     * @param product post ip list for product
     * @param cluster post ip list for cluster with product
     * @param ips     post ip list
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
        logger.info("post cluster,the cluster name is " + cluster + "; the product name=" + product + "; the ip list=" + ips);
        ResponseEntity responseEntity;
        try {
            Service service = addressServerManager.createServiceIfEmpty(productName);
            if (service.getClusterMap().containsKey(clusterName)) {
                /**
                 * Must conform to the operational semantics of POST。
                 * create the new resource,so the cluster name has contains must response error.
                 */
                responseEntity = ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body("product=" + rawProductName + ", cluster=" + rawClusterName + " already exists.");
            } else {
                String[] ipArray = addressServerManager.splitIps(ips);

                String result = AddressServerParamCheckUtil.checkIps(ipArray);
                if (AddressServerParamCheckUtil.CHECK_OK.equals(result)) {
                    List<Instance> instanceList = addressServerGeneratorManager.generateInstancesByIps(service.getName(), clusterName, ipArray);
                    for (Instance instance : instanceList) {
                        serviceManager.registerInstance(AddressServerConstants.DEFAULT_NAMESPACE, service.getName(), instance);
                    }
                    responseEntity = ResponseEntity.ok("product=" + rawProductName + ",cluster=" + rawClusterName + " post success with size=" + instanceList.size());
                } else {
                    responseEntity = ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(result);
                }
            }
        } catch (Exception e) {

            responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getCause());
        }

        return responseEntity;
    }

    /**
     * @param product
     * @param cluster
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.DELETE)
    public ResponseEntity deleteCluster(@RequestParam(required = false) String product,
                                        @RequestParam(required = false) String cluster,
                                        @RequestParam(required = false) String ips) {
        //1. prepare the storage name for product and cluster
        String productName = addressServerGeneratorManager.generateProductName(product);
        String clusterName = addressServerManager.getDefaultClusterNameIfEmpty(cluster);

        //2. prepare the response name for product and cluster to client
        String rawProductName = addressServerManager.getRawProductName(product);
        String rawClusterName = addressServerManager.getRawClusterName(cluster);
        ResponseEntity responseEntity = ResponseEntity.status(HttpStatus.OK).body("product=" + rawProductName + ", cluster=" + rawClusterName + " delete success.");
        try {

            String serviceName = addressServerGeneratorManager.generateNacosServiceName(productName);
            Service service = serviceManager.getService(AddressServerConstants.DEFAULT_NAMESPACE, serviceName);

            if (service == null) {
                responseEntity = ResponseEntity.status(HttpStatus.NOT_FOUND).body("product=" + rawProductName + " not found.");
            } else {

                if (StringUtils.isBlank(ips)) {
                    // delete all ips from the cluster
                    Cluster clusterObj = service.getClusterMap().remove(clusterName);
                    List<Instance> instanceList = clusterObj.allIPs(false);
                    serviceManager.removeInstance(AddressServerConstants.DEFAULT_NAMESPACE, serviceName, false, instanceList.toArray(new Instance[0]));
                } else {
                    // delete specified ip list
                    String[] ipArray = addressServerManager.splitIps(ips);
                    String checkResult = AddressServerParamCheckUtil.checkIps(ipArray);
                    if (AddressServerParamCheckUtil.CHECK_OK.equals(checkResult)) {
                        Cluster clusterObj = service.getClusterMap().get(clusterName);
                        List<Instance> instanceList = clusterObj.allIPs(false);
                        List<Instance> removeInstanceList = new LinkedList<>();
                        for (final String ip : ipArray) {
                            Instance deleteInstance = null;
                            for (Instance instance : instanceList) {
                                if (instance.getIp().equals(ip)) {
                                    deleteInstance = instance;
                                    break;
                                }
                            }

                            if (deleteInstance != null) {
                                removeInstanceList.add(deleteInstance);
                            }
                        }
                        serviceManager.removeInstance(AddressServerConstants.DEFAULT_NAMESPACE, serviceName, false, removeInstanceList.toArray(new Instance[0]));
                    } else {
                        responseEntity = ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(checkResult);
                    }
                }
            }
        } catch (Exception e) {

            responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getCause());
        }

        return responseEntity;
    }

    /**
     * @param product
     * @param cluster
     * @return
     */
    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResponseEntity putCluster(@RequestParam(required = false) String product,
                                     @RequestParam(required = false) String cluster,
                                     @RequestParam(name = "ips") String ips) {

        //1. prepare the storage name for product and cluster
        String productName = addressServerGeneratorManager.generateProductName(product);
        String clusterName = addressServerManager.getDefaultClusterNameIfEmpty(cluster);

        //2. prepare the response name for product and cluster to client
        String rawProductName = addressServerManager.getRawProductName(product);
        String rawClusterName = addressServerManager.getRawClusterName(cluster);
        logger.info("post cluster,the cluster name is " + cluster + "; the product name=" + product + "; the ip list=" + ips);
        ResponseEntity responseEntity;
        try {
            Service service = addressServerManager.createServiceIfEmpty(productName);
            String[] ipArray = addressServerManager.splitIps(ips);
            String checkResult = AddressServerParamCheckUtil.checkIps(ipArray);
            if (AddressServerParamCheckUtil.CHECK_OK.equals(checkResult)) {
                List<Instance> instanceList = addressServerGeneratorManager.generateInstancesByIps(service.getName(), clusterName, ipArray);
                if (instanceList.size() > 0) {
                    for (Instance instance : instanceList) {
                        serviceManager.registerInstance(AddressServerConstants.DEFAULT_NAMESPACE, service.getName(), instance);
                    }
                }
                responseEntity = ResponseEntity.ok("product=" + rawProductName + ",cluster=" + rawClusterName + "; put success with size=" + instanceList.size());
            } else {
                responseEntity = ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(checkResult);
            }
        } catch (Exception e) {

            responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }

        return responseEntity;
    }

}
