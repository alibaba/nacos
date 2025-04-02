/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceListForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceListHttpParamExtractor;
import com.alibaba.nacos.naming.pojo.instance.BeatInfoInstanceBuilder;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Nacos naming module client used HTTP Open API controller.
 *
 * <p>
 * This open API is used for some program language which not support gRPC request and want to develop a application used
 * client to register/deregister self to Nacos, or get remote call service instance list from Nacos. So this client used
 * open API only provide specified feature APIs to register instance/deregister instance/get instance list for specified
 * service. Not support subscribe service instances with HTTP, please use gRPC request to subscribe service.
 * </p>
 *
 * @author xiweng.yy
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.INSTANCE_V3_CLIENT_API_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
@Tag(name = "nacos.client.naming.instance.api.controller.name", description = "nacos.client.naming.instance.api.controller.description")
public class InstanceOpenApiController {
    
    private final InstanceOperator instanceOperator;
    
    private final SwitchDomain switchDomain;
    
    public InstanceOpenApiController(InstanceOperator instanceOperator, SwitchDomain switchDomain) {
        this.instanceOperator = instanceOperator;
        this.switchDomain = switchDomain;
    }
    
    /**
     * Register or heart beat instance to Nacos.
     *
     * @param instanceForm instance form
     * @param heartBeat    whether is heart beat request
     * @return register or heart beat result. If is heartBeat request(heartBeat=true) and instance not found, return
     * code `21003` to indicate caller should register again with heartBeat=false.
     * @throws NacosException register or heart beat with exception.
     */
    @CanDistro
    @PostMapping
    @TpsControl(pointName = "NamingInstanceRegister", name = "HttpNamingInstanceRegister")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.OPEN_API)
    @Operation(summary = "nacos.client.naming.instance.api.register.summary", description = "nacos.client.naming.instance.api.register.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.client.naming.instance.api.register.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"),
            @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"), @Parameter(name = "weight", example = "1.0"),
            @Parameter(name = "healthy", example = "true"), @Parameter(name = "ephemeral", example = "true"),
            @Parameter(name = "enabled", example = "true"), @Parameter(name = "metadata", example = "{\"zone\":\"a\"}"),
            @Parameter(name = "heartBeat", example = "false"), @Parameter(name = "instanceForm", hidden = true)})
    public Result<String> register(InstanceForm instanceForm, @RequestParam(defaultValue = "false") boolean heartBeat)
            throws NacosException {
        // check param
        instanceForm.validate();
        if (heartBeat) {
            if (ResponseCode.OK != doHeartBeat(instanceForm)) {
                return Result.failure(ErrorCode.INSTANCE_NOT_FOUND, null);
            }
        } else {
            doRegisterInstance(instanceForm);
        }
        return Result.success("ok");
    }
    
    /**
     * Deregister instance from Nacos.
     *
     * @param instanceForm instance form
     * @return deregister result, if instance not found, also return remove success.
     * @throws NacosException deregister with exception.
     */
    @CanDistro
    @DeleteMapping
    @TpsControl(pointName = "NamingInstanceDeregister", name = "HttpNamingInstanceDeregister")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.OPEN_API)
    @Operation(summary = "nacos.client.naming.instance.api.deregister.summary",
            description = "nacos.client.naming.instance.api.deregister.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.client.naming.instance.api.deregister.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"),
            @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"),
            @Parameter(name = "instanceForm", hidden = true)})
    public Result<String> deregister(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        Instance instance = InstanceUtil.buildInstance(instanceForm, switchDomain.isDefaultInstanceEphemeral());
        instanceOperator.removeInstance(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                instanceForm.getServiceName(), instance);
        NotifyCenter.publishEvent(
                new DeregisterInstanceTraceEvent(System.currentTimeMillis(), NamingRequestUtil.getSourceIp(), false,
                        DeregisterInstanceReason.REQUEST, instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                        instanceForm.getServiceName(), instance.getIp(), instance.getPort()));
        return Result.success("ok");
    }
    
    /**
     * Get all instances for specified service.
     *
     * <p>
     * This API will not return `enabled=false` instances, because of this api is used by custom client. instances with
     * `enabled=false` means instance has been offline, so client should not found these service instances.
     * </p>
     *
     * @param instanceForm instance form of subscriber. The ip and port is subscriber info. The service name, group name
     *                     and cluster name is the target service info.
     * @return all instances for specified service without `enabled=false`.
     * @throws Exception any exception during get instances.
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceSubscribe", name = "HttpNamingServiceSubscribe")
    @Secured(action = ActionTypes.READ, apiType = ApiType.OPEN_API)
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceListHttpParamExtractor.class)
    @Operation(summary = "nacos.client.naming.instance.api.list.summary", description = "nacos.client.naming.instance.api.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.client.naming.instance.api.list.example")))
    @Parameters(value = {@Parameter(name = "pageNo", required = true, example = "1"),
            @Parameter(name = "pageSize", required = true, example = "100"),
            @Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"), @Parameter(name = "instanceForm", hidden = true),
            @Parameter(name = "pageForm", hidden = true)})
    public Result<List<Instance>> list(InstanceListForm instanceForm) throws Exception {
        // check param
        instanceForm.validate();
        String namespaceId = instanceForm.getNamespaceId();
        String groupName = instanceForm.getGroupName();
        String serviceName = instanceForm.getServiceName();
        ServiceInfo serviceInfo = instanceOperator.listInstance(namespaceId, groupName, serviceName, null,
                instanceForm.getClusterName(), false);
        return Result.success(serviceInfo.getHosts());
    }
    
    private int doHeartBeat(InstanceForm instanceForm) throws NacosException {
        BeatInfoInstanceBuilder builder = BeatInfoInstanceBuilder.newBuilder();
        return instanceOperator.handleBeat(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                instanceForm.getServiceName(), instanceForm.getIp(), instanceForm.getPort(),
                instanceForm.getClusterName(), null, builder);
    }
    
    private void doRegisterInstance(InstanceForm instanceForm) throws NacosException {
        NamingRequestUtil.checkWeight(instanceForm.getWeight());
        Instance instance = InstanceUtil.buildInstance(instanceForm, switchDomain.isDefaultInstanceEphemeral());
        String namespaceId = instanceForm.getNamespaceId();
        String groupName = instanceForm.getGroupName();
        String serviceName = instanceForm.getServiceName();
        instanceOperator.registerInstance(namespaceId, groupName, serviceName, instance);
        NotifyCenter.publishEvent(
                new RegisterInstanceTraceEvent(System.currentTimeMillis(), NamingRequestUtil.getSourceIp(), false,
                        namespaceId, groupName, serviceName, instance.getIp(), instance.getPort()));
    }
}
