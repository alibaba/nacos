/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.naming;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.enums.ApiType;
import com.alibaba.nacos.console.proxy.naming.InstanceProxy;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling HTTP requests related to instance operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/ns/instance")
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class ConsoleInstanceController {
    
    private final SwitchDomain switchDomain;
    
    private final InstanceProxy instanceProxy;
    
    /**
     * Constructs a new ConsoleInstanceController with the provided InstanceProxy.
     *
     * @param instanceProxy the proxy used for handling instance-related operations
     */
    public ConsoleInstanceController(InstanceProxy instanceProxy, SwitchDomain switchDomain) {
        this.instanceProxy = instanceProxy;
        this.switchDomain = switchDomain;
    }
    
    /**
     * List instances of special service.
     *
     * @param serviceForm service form
     * @param pageForm Page form
     * @param healthyOnly whether only return health instance
     * @param enabledOnly wheter only return enabled instance
     * @return instances information
     */
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @RequestMapping("/list")
    public Result<ObjectNode> getInstanceList(ServiceForm serviceForm, PageForm pageForm,
            @RequestParam(required = false) Boolean healthyOnly, @RequestParam(required = false) Boolean enabledOnly)
            throws NacosApiException {
        serviceForm.validate();
        String namespaceId = serviceForm.getNamespaceId();
        String groupName = serviceForm.getGroupName();
        String serviceName = serviceForm.getServiceName();
        ObjectNode result = instanceProxy.listInstances(namespaceId, serviceName, groupName, pageForm.getPageNo(),
                pageForm.getPageSize(), healthyOnly, enabledOnly);
        return Result.success(result);
    }
    
    /**
     * Update instance.
     */
    @CanDistro
    @PutMapping
    @TpsControl(pointName = "NamingInstanceUpdate", name = "HttpNamingInstanceUpdate")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    public Result<String> updateInstance(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceProxy.updateInstance(instanceForm, instance);
        return Result.success("ok");
    }
    
    private void checkWeight(Double weight) throws NacosException {
        if (weight > com.alibaba.nacos.naming.constants.Constants.MAX_WEIGHT_VALUE
                || weight < com.alibaba.nacos.naming.constants.Constants.MIN_WEIGHT_VALUE) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.WEIGHT_ERROR,
                    "instance format invalid: The weights range from "
                            + com.alibaba.nacos.naming.constants.Constants.MIN_WEIGHT_VALUE + " to "
                            + com.alibaba.nacos.naming.constants.Constants.MAX_WEIGHT_VALUE);
        }
    }
    
    private Instance buildInstance(InstanceForm instanceForm) throws NacosException {
        Instance instance = InstanceBuilder.newBuilder().setServiceName(buildCompositeServiceName(instanceForm))
                .setIp(instanceForm.getIp()).setClusterName(instanceForm.getClusterName())
                .setPort(instanceForm.getPort()).setHealthy(instanceForm.getHealthy())
                .setWeight(instanceForm.getWeight()).setEnabled(instanceForm.getEnabled())
                .setMetadata(UtilsAndCommons.parseMetadata(instanceForm.getMetadata()))
                .setEphemeral(instanceForm.getEphemeral()).build();
        if (instanceForm.getEphemeral() == null) {
            instance.setEphemeral((switchDomain.isDefaultInstanceEphemeral()));
        }
        return instance;
    }
    
    private String buildCompositeServiceName(InstanceForm instanceForm) {
        return NamingUtils.getGroupedName(instanceForm.getServiceName(), instanceForm.getGroupName());
    }
    
}
