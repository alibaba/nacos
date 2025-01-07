/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.UpdateSwitchForm;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * Cluster controller.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.OPERATOR_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class OperatorControllerV3 {
    
    private final SwitchManager switchManager;
    
    private final ServerStatusManager serverStatusManager;
    
    private final SwitchDomain switchDomain;
    
    private final ClientManager clientManager;
    
    public OperatorControllerV3(SwitchManager switchManager, ServerStatusManager serverStatusManager,
            SwitchDomain switchDomain, ClientManager clientManager) {
        this.switchManager = switchManager;
        this.serverStatusManager = serverStatusManager;
        this.switchDomain = switchDomain;
        this.clientManager = clientManager;
    }
    
    /**
     * Get switch information.
     *
     * @return switchDomain
     */
    @GetMapping("/switches")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<SwitchDomain> switches() {
        return Result.success(switchDomain);
    }
    
    /**
     * Update switch information.
     *
     * @param updateSwitchForm debug, entry, value
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PutMapping("/switches")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> updateSwitch(UpdateSwitchForm updateSwitchForm) throws Exception {
        updateSwitchForm.validate();
        try {
            switchManager.update(updateSwitchForm.getEntry(), updateSwitchForm.getValue(), updateSwitchForm.getDebug());
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.SERVER_ERROR,
                    e.getMessage());
        }
        
        return Result.success("ok");
    }
    
    /**
     * Get metrics information.
     */
    @GetMapping("/metrics")
    @Secured(resource = UtilsAndCommons.OPERATOR_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<MetricsInfoVo> metrics(
            @RequestParam(value = "onlyStatus", required = false, defaultValue = "true") Boolean onlyStatus) {
        MetricsInfoVo metricsInfoVo = new MetricsInfoVo();
        metricsInfoVo.setStatus(serverStatusManager.getServerStatus().name());
        if (onlyStatus) {
            return Result.success(metricsInfoVo);
        }
        
        int connectionBasedClient = 0;
        int ephemeralIpPortClient = 0;
        int persistentIpPortClient = 0;
        int responsibleClientCount = 0;
        Collection<String> allClientId = clientManager.allClientId();
        for (String clientId : allClientId) {
            if (clientId.contains(IpPortBasedClient.ID_DELIMITER)) {
                if (clientId.endsWith(ClientConstants.PERSISTENT_SUFFIX)) {
                    persistentIpPortClient += 1;
                } else {
                    ephemeralIpPortClient += 1;
                }
            } else {
                connectionBasedClient += 1;
            }
            if (clientManager.isResponsibleClient(clientManager.getClient(clientId))) {
                responsibleClientCount += 1;
            }
        }
        
        metricsInfoVo.setServiceCount(MetricsMonitor.getDomCountMonitor().get());
        metricsInfoVo.setInstanceCount(MetricsMonitor.getIpCountMonitor().get());
        metricsInfoVo.setSubscribeCount(MetricsMonitor.getSubscriberCount().get());
        metricsInfoVo.setClientCount(allClientId.size());
        metricsInfoVo.setConnectionBasedClientCount(connectionBasedClient);
        metricsInfoVo.setEphemeralIpPortClientCount(ephemeralIpPortClient);
        metricsInfoVo.setPersistentIpPortClientCount(persistentIpPortClient);
        metricsInfoVo.setResponsibleClientCount(responsibleClientCount);
        metricsInfoVo.setCpu(EnvUtil.getCpu());
        metricsInfoVo.setLoad(EnvUtil.getLoad());
        metricsInfoVo.setMem(EnvUtil.getMem());
        
        return Result.success(metricsInfoVo);
    }
    
    @PutMapping("/log")
    @Secured(resource = UtilsAndCommons.OPERATOR_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> setLogLevel(@RequestParam String logName, @RequestParam String logLevel) {
        Loggers.setLogLevel(logName, logLevel);
        
        return Result.success("ok");
    }
}