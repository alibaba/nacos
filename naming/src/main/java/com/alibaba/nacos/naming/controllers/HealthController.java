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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.web.CanDistro;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Health status related operation controller
 *
 * @author nkorange
 * @author nanamikon
 * @since 0.8.0
 */
@RestController("namingHealthController")
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/health")
public class HealthController {

    @Autowired
    private ServiceManager serviceManager;


    @Autowired
    private PushService pushService;

    @RequestMapping("/server")
    public ObjectNode server() {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total services: raft " + serviceManager.getServiceCount()
            + ", local port:" + ApplicationUtils.getPort());
        return result;
    }

    @CanDistro
    @PutMapping(value = {"", "/instance"})
    @Secured(action = ActionTypes.WRITE)
    public String update(HttpServletRequest request) {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME
            , UtilsAndCommons.DEFAULT_CLUSTER_NAME);

        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        boolean valid = false;

        String healthyString = WebUtils.optional(request, "healthy", StringUtils.EMPTY);
        if (StringUtils.isBlank(healthyString)) {
            healthyString = WebUtils.optional(request, "valid", StringUtils.EMPTY);
        }

        if (StringUtils.isBlank(healthyString)) {
            throw new IllegalArgumentException("Param 'healthy' is required.");
        }

        valid = BooleanUtils.toBoolean(healthyString);

        Service service = serviceManager.getService(namespaceId, serviceName);
        // Only health check "none" need update health status with api
        if (HealthCheckType.NONE.name().equals(service.getClusterMap().get(clusterName).getHealthChecker().getType())) {
            for (Instance instance : service.allIPs(Lists.newArrayList(clusterName))) {
                if (instance.getIp().equals(ip) && instance.getPort() == port) {
                    instance.setHealthy(valid);
                    Loggers.EVT_LOG.info((valid ? "[IP-ENABLED]" : "[IP-DISABLED]") + " ips: "
                        + instance.getIp() + ":" + instance.getPort() + "@" + instance.getClusterName()
                        + ", service: " + serviceName + ", msg: update thought HealthController api");
                    pushService.serviceChanged(service);
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException("health check is still working, service: " + serviceName);
        }

        return "ok";
    }

    @GetMapping("checkers")
    public ResponseEntity checkers() {
        List<Class<? extends AbstractHealthChecker>> classes = HealthCheckType.getLoadedHealthCheckerClasses();
        Map<String, AbstractHealthChecker> checkerMap = new HashMap<>(8);
        for (Class<? extends AbstractHealthChecker> clazz : classes) {
            try {
                AbstractHealthChecker checker = clazz.newInstance();
                checkerMap.put(checker.getType(), checker);
            } catch (InstantiationException | IllegalAccessException e) {
                Loggers.EVT_LOG.error("checkers error ", e);
            }
        }
        return ResponseEntity.ok(checkerMap);
    }
}
