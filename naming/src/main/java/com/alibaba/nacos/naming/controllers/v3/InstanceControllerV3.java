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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
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
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.utils.NamingRequestUtil;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
public class InstanceControllerV3 {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private InstanceOperatorClientImpl instanceService;
    
    /**
     * Register new instance.
     */
    @CanDistro
    @PostMapping
    @TpsControl(pointName = "NamingInstanceRegister", name = "HttpNamingInstanceRegister")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> register(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
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
    public Result<String> deregister(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceService.removeInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
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
    public Result<String> update(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceService.updateInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
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
            checkWeight(weight);
            patchObject.setWeight(weight);
        }
        Boolean enabled = instanceForm.getEnabled();
        if (enabled != null) {
            patchObject.setEnabled(enabled);
        }
        String serviceName = NamingUtils.getGroupedName(instanceForm.getServiceName(), instanceForm.getGroupName());
        instanceService.patchInstance(instanceForm.getNamespaceId(), serviceName, patchObject);
        return Result.success("ok");
    }
    
    /**
     * Get all instance of input service.
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceSubscribe", name = "HttpNamingServiceSubscribe")
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceListHttpParamExtractor.class)
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ServiceInfo> list(InstanceListForm instanceListForm) throws NacosApiException {
        instanceListForm.validate();
        String compositeServiceName = NamingUtils.getGroupedName(instanceListForm.getServiceName(),
                instanceListForm.getGroupName());
        String namespaceId = instanceListForm.getNamespaceId();
        String clusterName = instanceListForm.getClusterName();
        // TODO Deprecated, the subscriber is used by client 1.0 to subs service, admin api don't need it,
        //  InstanceOperator should support no subscribe api.
        Subscriber subscriber = new Subscriber("Deprecated", "Deprecated", "Deprecated", "Deprecated", namespaceId,
                compositeServiceName, 0, clusterName);
        return Result.success(instanceService.listInstance(namespaceId, compositeServiceName, subscriber, clusterName,
                instanceListForm.getHealthyOnly()));
    }
    
    /**
     * Get detail information of specified instance.
     */
    @GetMapping
    @TpsControl(pointName = "NamingInstanceQuery", name = "HttpNamingInstanceQuery")
    @Secured(resource = UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<Instance> detail(InstanceForm instanceForm) throws NacosException {
        instanceForm.validate();
        String compositeServiceName = NamingUtils.getGroupedName(instanceForm.getServiceName(),
                instanceForm.getGroupName());
        String namespaceId = instanceForm.getNamespaceId();
        String clusterName = instanceForm.getClusterName();
        String ip = instanceForm.getIp();
        int port = instanceForm.getPort();
        Instance instance = instanceService.getInstance(namespaceId, compositeServiceName, clusterName, ip, port);
        return Result.success(instance);
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
    
    private String buildCompositeServiceName(InstanceMetadataBatchOperationForm form) {
        return NamingUtils.getGroupedName(form.getServiceName(), form.getGroupName());
    }
    
}
