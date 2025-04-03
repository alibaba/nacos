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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.DeregisterInstanceReason;
import com.alibaba.nacos.common.trace.event.naming.DeregisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterInstanceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateInstanceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.CatalogService;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.InstancePatchObject;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceListForm;
import com.alibaba.nacos.naming.model.form.InstanceMetadataBatchOperationForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceListHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceMetadataBatchHttpParamExtractor;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.DEFAULT_CLUSTER_NAME;

/**
 * Instance controller.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
@Tag(name = "nacos.admin.naming.instance.api.controller.name", description = "nacos.admin.naming.instance.api.controller.description", extensions = {
        @Extension(name = RemoteConstants.LABEL_MODULE,
                properties = @ExtensionProperty(name = RemoteConstants.LABEL_MODULE, value = RemoteConstants.LABEL_MODULE_NAMING))})
public class InstanceControllerV3 {
    
    private final SwitchDomain switchDomain;
    
    private final InstanceOperatorClientImpl instanceService;
    
    private final CatalogService catalogService;
    
    public InstanceControllerV3(SwitchDomain switchDomain, InstanceOperatorClientImpl instanceService,
            CatalogService catalogService) {
        this.switchDomain = switchDomain;
        this.instanceService = instanceService;
        this.catalogService = catalogService;
    }
    
    /**
     * Register new instance.
     */
    @CanDistro
    @PostMapping
    @TpsControl(pointName = "NamingInstanceRegister", name = "HttpNamingInstanceRegister")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.register.summary", description = "nacos.admin.naming.instance.api.register.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.register.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"), @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"), @Parameter(name = "weight", example = "1.0"),
            @Parameter(name = "healthy", example = "true"), @Parameter(name = "ephemeral", example = "true"),
            @Parameter(name = "enabled", example = "true"), @Parameter(name = "metadata", example = "{\"zone\":\"a\"}"),
            @Parameter(name = "instanceForm", hidden = true)})
    public Result<String> register(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        NamingRequestUtil.checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = InstanceUtil.buildInstance(instanceForm, switchDomain.isDefaultInstanceEphemeral());
        String namespaceId = instanceForm.getNamespaceId();
        String groupName = instanceForm.getGroupName();
        String serviceName = instanceForm.getServiceName();
        instanceService.registerInstance(namespaceId, groupName, serviceName, instance);
        NotifyCenter.publishEvent(
                new RegisterInstanceTraceEvent(System.currentTimeMillis(), NamingRequestUtil.getSourceIp(), false,
                        namespaceId, groupName, serviceName, instance.getIp(), instance.getPort()));
        
        return Result.success("ok");
    }
    
    /**
     * Deregister instances.
     */
    @CanDistro
    @DeleteMapping
    @TpsControl(pointName = "NamingInstanceDeregister", name = "HttpNamingInstanceDeregister")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.deregister.summary", description = "nacos.admin.naming.instance.api.deregister.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.deregister.example")))
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
        // build instance
        Instance instance = InstanceUtil.buildInstance(instanceForm, switchDomain.isDefaultInstanceEphemeral());
        instanceService.removeInstance(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                instanceForm.getServiceName(), instance);
        NotifyCenter.publishEvent(
                new DeregisterInstanceTraceEvent(System.currentTimeMillis(), NamingRequestUtil.getSourceIp(), false,
                        DeregisterInstanceReason.REQUEST, instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                        instanceForm.getServiceName(), instance.getIp(), instance.getPort()));
        
        return Result.success("ok");
    }
    
    /**
     * Update instance.
     */
    @CanDistro
    @PutMapping
    @TpsControl(pointName = "NamingInstanceUpdate", name = "HttpNamingInstanceUpdate")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.update.summary", description = "nacos.admin.naming.instance.api.update.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.update.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"), @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"), @Parameter(name = "weight", example = "1.0"),
            @Parameter(name = "healthy", example = "true"), @Parameter(name = "ephemeral", example = "true"),
            @Parameter(name = "enabled", example = "true"), @Parameter(name = "metadata", example = "{\"zone\":\"a\"}"),
            @Parameter(name = "instanceForm", hidden = true)})
    public Result<String> update(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        NamingRequestUtil.checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = InstanceUtil.buildInstance(instanceForm, switchDomain.isDefaultInstanceEphemeral());
        instanceService.updateInstance(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                instanceForm.getServiceName(), instance);
        NotifyCenter.publishEvent(
                new UpdateInstanceTraceEvent(System.currentTimeMillis(), NamingRequestUtil.getSourceIp(),
                        instanceForm.getNamespaceId(), instanceForm.getGroupName(), instanceForm.getServiceName(),
                        instance.getIp(), instance.getPort(), instance.getMetadata()));
        
        return Result.success("ok");
    }
    
    /**
     * Batch update instance's metadata. old key exist = update, old key not exist = add.
     */
    @CanDistro
    @PutMapping(value = "/metadata/batch")
    @TpsControl(pointName = "NamingInstanceMetadataUpdate", name = "HttpNamingInstanceMetadataBatchUpdate")
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceMetadataBatchHttpParamExtractor.class)
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.update.batch.summary",
            description = "nacos.admin.naming.instance.api.update.batch.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.update.batch.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "consistencyType", example = "ephemeral"),
            @Parameter(name = "metadata", required = true, example = "{\"zone\":\"a\"}"),
            @Parameter(name = "instances", example = "[]"), @Parameter(name = "form", hidden = true)})
    public Result<InstanceMetadataBatchResult> batchUpdateInstanceMetadata(InstanceMetadataBatchOperationForm form)
            throws NacosException {
        form.validate();
        
        List<Instance> targetInstances = parseBatchInstances(form.getInstances());
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(form.getMetadata());
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(buildCompositeServiceName(form),
                form.getConsistencyType(), targetInstances);
        
        List<String> operatedInstances = instanceService.batchUpdateMetadata(form.getNamespaceId(),
                instanceOperationInfo, targetMetadata);
        ArrayList<String> ipList = new ArrayList<>(operatedInstances);
        
        return Result.success(new InstanceMetadataBatchResult(ipList));
    }
    
    /**
     * Batch delete instance's metadata. old key exist = delete, old key not exist = not operate
     */
    @CanDistro
    @DeleteMapping("/metadata/batch")
    @TpsControl(pointName = "NamingInstanceMetadataUpdate", name = "HttpNamingInstanceMetadataBatchUpdate")
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceMetadataBatchHttpParamExtractor.class)
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.delete.batch.summary",
            description = "nacos.admin.naming.instance.api.delete.batch.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.delete.batch.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "consistencyType", example = "ephemeral"),
            @Parameter(name = "metadata", required = true, example = "{\"zone\":\"a\"}"),
            @Parameter(name = "instances", example = "[]"), @Parameter(name = "form", hidden = true)})
    public Result<InstanceMetadataBatchResult> batchDeleteInstanceMetadata(InstanceMetadataBatchOperationForm form)
            throws NacosException {
        form.validate();
        List<Instance> targetInstances = parseBatchInstances(form.getInstances());
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(form.getMetadata());
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(buildCompositeServiceName(form),
                form.getConsistencyType(), targetInstances);
        List<String> operatedInstances = instanceService.batchDeleteMetadata(form.getNamespaceId(),
                instanceOperationInfo, targetMetadata);
        ArrayList<String> ipList = new ArrayList<>(operatedInstances);
        
        return Result.success(new InstanceMetadataBatchResult(ipList));
    }
    
    private InstanceOperationInfo buildOperationInfo(String serviceName, String consistencyType,
            List<Instance> instances) {
        if (!CollectionUtils.isEmpty(instances)) {
            for (Instance instance : instances) {
                if (StringUtils.isBlank(instance.getClusterName())) {
                    instance.setClusterName(DEFAULT_CLUSTER_NAME);
                }
            }
        }
        return new InstanceOperationInfo(serviceName, consistencyType, instances);
    }
    
    private List<Instance> parseBatchInstances(String instances) {
        try {
            return JacksonUtils.toObj(instances, new TypeReference<List<Instance>>() {
            });
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("UPDATE-METADATA: Param 'instances' is illegal, ignore this operation", e);
        }
        return Collections.emptyList();
    }
    
    /**
     * Partial update instance.
     */
    @CanDistro
    @PutMapping(value = "/partial")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.partial.summary", description = "nacos.admin.naming.instance.api.partial.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.partial.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"), @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"), @Parameter(name = "weight", example = "1.0"),
            @Parameter(name = "healthy", example = "true"), @Parameter(name = "ephemeral", example = "true"),
            @Parameter(name = "enabled", example = "true"), @Parameter(name = "metadata", example = "{\"zone\":\"a\"}"),
            @Parameter(name = "instanceForm", hidden = true)})
    public Result<String> partialUpdateInstance(InstanceForm instanceForm) throws Exception {
        instanceForm.validate();
        InstancePatchObject patchObject = new InstancePatchObject(instanceForm.getClusterName(), instanceForm.getIp(),
                instanceForm.getPort());
        String metadata = instanceForm.getMetadata();
        if (StringUtils.isNotBlank(metadata)) {
            patchObject.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }
        Double weight = instanceForm.getWeight();
        if (weight != null) {
            NamingRequestUtil.checkWeight(weight);
            patchObject.setWeight(weight);
        }
        Boolean enabled = instanceForm.getEnabled();
        if (enabled != null) {
            patchObject.setEnabled(enabled);
        }
        instanceService.patchInstance(instanceForm.getNamespaceId(), instanceForm.getGroupName(),
                instanceForm.getServiceName(), patchObject);
        return Result.success("ok");
    }
    
    /**
     * Get all instance of input service.
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceSubscribe", name = "HttpNamingServiceSubscribe")
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceListHttpParamExtractor.class)
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.list.summary", description = "nacos.admin.naming.instance.api.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.list.example")))
    @Parameters(value = {@Parameter(name = "pageNo", required = true, example = "1"),
            @Parameter(name = "pageSize", required = true, example = "100"),
            @Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"), @Parameter(name = "instanceForm", hidden = true),
            @Parameter(name = "pageForm", hidden = true)})
    public Result<List<? extends Instance>> list(InstanceListForm instanceListForm) throws NacosException {
        instanceListForm.validate();
        List<? extends Instance> instances = catalogService.listInstances(instanceListForm.getNamespaceId(),
                instanceListForm.getGroupName(), instanceListForm.getServiceName(), instanceListForm.getClusterName());
        if (instanceListForm.getHealthyOnly()) {
            instances = instances.stream().filter(Instance::isHealthy).toList();
        }
        return Result.success(instances);
    }
    
    /**
     * Get detail information of specified instance.
     */
    @GetMapping
    @TpsControl(pointName = "NamingInstanceQuery", name = "HttpNamingInstanceQuery")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.instance.api.get.summary", description = "nacos.admin.naming.instance.api.get.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.instance.api.get.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", example = "DEFAULT"),
            @Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080"),
            @Parameter(name = "instanceForm", hidden = true)})
    public Result<Instance> detail(InstanceForm instanceForm) throws NacosException {
        instanceForm.validate();
        String namespaceId = instanceForm.getNamespaceId();
        String clusterName = instanceForm.getClusterName();
        String ip = instanceForm.getIp();
        int port = instanceForm.getPort();
        Instance instance = instanceService.getInstance(namespaceId, instanceForm.getGroupName(),
                instanceForm.getServiceName(), clusterName, ip, port);
        return Result.success(instance);
    }
    
    private String buildCompositeServiceName(InstanceMetadataBatchOperationForm form) {
        return NamingUtils.getGroupedName(form.getServiceName(), form.getGroupName());
    }
    
}
