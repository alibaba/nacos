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
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.naming.core.Application;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.ApplicationPageRequest;
import com.alibaba.nacos.naming.pojo.ApplicationPageResponse;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author kkyeer
 * @Description: Handle Application Requests
 * @Date:Created in 16:42 2-22
 * @Modified By:
 */
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_CATALOG_CONTEXT + "/applications")

@RestController
public class ApplicationController {
    @Autowired
    private ServiceManager serviceManager;
    /**
     * Query applications in page
     *
     * @param applicationPageRequest application Request params
     * @return Applications in page
     */
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    @GetMapping
    public ApplicationPageResponse queryApplicationPage(ApplicationPageRequest applicationPageRequest) {
        ApplicationPageResponse response = new ApplicationPageResponse();
        List<Application> applicationList = serviceManager.getApplications(
            applicationPageRequest.getNamespaceId(),
            applicationPageRequest.getApplicationIp(),
            applicationPageRequest.getApplicationPort(),
            applicationPageRequest.getServiceNameParam()
        );
        response.setCount(applicationList.size());
        if (applicationPageRequest.getPageSize() * (applicationPageRequest.getPageNo() - 1) >= applicationList.size()) {
            response.setApplicationList(Collections.EMPTY_LIST);
        }else {
            int fromIndex = applicationPageRequest.getPageSize() * (applicationPageRequest.getPageNo() - 1);
            int toIndex = applicationPageRequest.getPageSize() * (applicationPageRequest.getPageNo());
            if (toIndex >= applicationList.size()) {
                toIndex = applicationList.size();
            }
            response.setApplicationList(applicationList.subList(fromIndex, toIndex));
        }
        return response;
    }

    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    @GetMapping("/instances")
    public List<Instance> queryInstanceForApp(
        @RequestParam(required = false,defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
        @RequestParam String ip,
        @RequestParam Integer port){
        return serviceManager.getInstancesForApp(namespaceId, ip, port);
    }

    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    @PutMapping("/offline")
    public Map<String,Object> bringAppOffline(
        @RequestParam(required = false,defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
        @RequestParam String ip,
        @RequestParam Integer port
    ) {
        List<Instance> instanceList = serviceManager.getInstancesForApp(namespaceId, ip, port);
        AtomicInteger count = new AtomicInteger(0);
        AtomicBoolean success = new AtomicBoolean(true);
        AtomicReference<String> msg = new AtomicReference<>("");
        instanceList.forEach(
            instance -> {
                instance.setEnabled(false);
                try {
                    serviceManager.updateInstance(namespaceId, instance.getServiceName(), instance);
                    count.getAndIncrement();
                } catch (NacosException e) {
                    success.set(false);
                    msg.set(e.getErrMsg());
                }
            }
        );
        Map<String, Object> response = new HashMap<>(3);
        response.put("success", success.get());
        response.put("count", count.get());
        response.put("msg", msg.get());
        return response;
    }
}
