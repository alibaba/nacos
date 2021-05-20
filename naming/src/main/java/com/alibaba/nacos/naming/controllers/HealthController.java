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
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.HealthOperator;
import com.alibaba.nacos.naming.core.HealthOperatorV1Impl;
import com.alibaba.nacos.naming.core.HealthOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static com.alibaba.nacos.naming.constants.RequestConstant.HEALTHY_KEY;
import static com.alibaba.nacos.naming.constants.RequestConstant.IP_KEY;
import static com.alibaba.nacos.naming.constants.RequestConstant.PORT_KEY;
import static com.alibaba.nacos.naming.constants.RequestConstant.VALID_KEY;

/**
 * Health status related operation controller.
 *
 * @author nkorange
 * @author nanamikon
 * @since 0.8.0
 */
@RestController("namingHealthController")
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + UtilsAndCommons.NACOS_NAMING_HEALTH_CONTEXT)
public class HealthController {
    
    @Autowired
    private HealthOperatorV1Impl healthOperatorV1;
    
    @Autowired
    private HealthOperatorV2Impl healthOperatorV2;
    
    @Autowired
    private UpgradeJudgement upgradeJudgement;
    
    /**
     * Just a health check.
     *
     * @return hello message
     */
    @RequestMapping("/server")
    public ResponseEntity server() {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("msg", "Hello! I am Nacos-Naming and healthy! total services: " + MetricsMonitor.getDomCountMonitor()
                + ", local port:" + EnvUtil.getPort());
        return ResponseEntity.ok(result);
    }
    
    /**
     * Update health check for instance.
     *
     * @param request http request
     * @return 'ok' if success
     */
    @CanDistro
    @PutMapping(value = {"", "/instance"})
    @Secured(action = ActionTypes.WRITE, parser = NamingResourceParser.class)
    public ResponseEntity update(HttpServletRequest request) throws NacosException {
        String healthyString = WebUtils.optional(request, HEALTHY_KEY, StringUtils.EMPTY);
        if (StringUtils.isBlank(healthyString)) {
            healthyString = WebUtils.optional(request, VALID_KEY, StringUtils.EMPTY);
        }
        if (StringUtils.isBlank(healthyString)) {
            throw new IllegalArgumentException("Param 'healthy' is required.");
        }
        boolean health = BooleanUtils.toBoolean(healthyString);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String clusterName = WebUtils
                .optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, IP_KEY);
        int port = Integer.parseInt(WebUtils.required(request, PORT_KEY));
        getHealthOperator()
                .updateHealthStatusForPersistentInstance(namespaceId, serviceName, clusterName, ip, port, health);
        return ResponseEntity.ok("ok");
    }
    
    /**
     * Get all health checkers.
     *
     * @return health checkers map
     */
    @GetMapping("/checkers")
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
    
    private HealthOperator getHealthOperator() {
        return upgradeJudgement.isUseGrpcFeatures() ? healthOperatorV2 : healthOperatorV1;
    }
}
