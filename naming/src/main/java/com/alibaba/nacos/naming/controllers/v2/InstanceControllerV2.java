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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
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
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import com.alibaba.nacos.naming.model.form.InstanceMetadataBatchOperationForm;
import com.alibaba.nacos.naming.model.vo.InstanceDetailInfoVo;
import com.alibaba.nacos.naming.model.vo.InstanceMetadataBatchOperationVo;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceBeatHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceListHttpParamExtractor;
import com.alibaba.nacos.naming.paramcheck.NamingInstanceMetadataBatchHttpParamExtractor;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.instance.BeatInfoInstanceBuilder;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.DEFAULT_CLUSTER_NAME;

/**
 * Instance operation controller for v2.x.
 *
 * @author hujun
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class InstanceControllerV2 {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private InstanceOperatorClientImpl instanceServiceV2;
    
    /**
     * Register new instance.
     */
    @CanDistro
    @PostMapping
    @TpsControl(pointName = "NamingInstanceRegister", name = "HttpNamingInstanceRegister")
    @Secured(action = ActionTypes.WRITE)
    public Result<String> register(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceServiceV2.registerInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
        NotifyCenter.publishEvent(
                new RegisterInstanceTraceEvent(System.currentTimeMillis(), "", false, instanceForm.getNamespaceId(),
                        instanceForm.getGroupName(), instanceForm.getServiceName(), instance.getIp(),
                        instance.getPort()));
        return Result.success("ok");
    }
    
    /**
     * Deregister instances.
     */
    @CanDistro
    @DeleteMapping
    @TpsControl(pointName = "NamingInstanceDeregister", name = "HttpNamingInstanceDeregister")
    @Secured(action = ActionTypes.WRITE)
    public Result<String> deregister(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceServiceV2.removeInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
        NotifyCenter.publishEvent(new DeregisterInstanceTraceEvent(System.currentTimeMillis(), "", false,
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
    @Secured(action = ActionTypes.WRITE)
    public Result<String> update(InstanceForm instanceForm) throws NacosException {
        // check param
        instanceForm.validate();
        checkWeight(instanceForm.getWeight());
        // build instance
        Instance instance = buildInstance(instanceForm);
        instanceServiceV2.updateInstance(instanceForm.getNamespaceId(), buildCompositeServiceName(instanceForm),
                instance);
        NotifyCenter.publishEvent(
                new UpdateInstanceTraceEvent(System.currentTimeMillis(), "", instanceForm.getNamespaceId(),
                        instanceForm.getGroupName(), instanceForm.getServiceName(), instance.getIp(),
                        instance.getPort(), instance.getMetadata()));
        return Result.success("ok");
    }
    
    /**
     * Batch update instance's metadata. old key exist = update, old key not exist = add.
     */
    @CanDistro
    @PutMapping(value = "/metadata/batch")
    @TpsControl(pointName = "NamingInstanceMetadataUpdate", name = "HttpNamingInstanceMetadataBatchUpdate")
    @Secured(action = ActionTypes.WRITE)
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceMetadataBatchHttpParamExtractor.class)
    public Result<InstanceMetadataBatchOperationVo> batchUpdateInstanceMetadata(InstanceMetadataBatchOperationForm form)
            throws NacosException {
        form.validate();
        
        List<Instance> targetInstances = parseBatchInstances(form.getInstances());
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(form.getMetadata());
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(buildCompositeServiceName(form),
                form.getConsistencyType(), targetInstances);
        
        List<String> operatedInstances = instanceServiceV2.batchUpdateMetadata(form.getNamespaceId(),
                instanceOperationInfo, targetMetadata);
        
        ArrayList<String> ipList = new ArrayList<>(operatedInstances);
        return Result.success(new InstanceMetadataBatchOperationVo(ipList));
    }
    
    /**
     * Batch delete instance's metadata. old key exist = delete, old key not exist = not operate
     */
    @CanDistro
    @DeleteMapping("/metadata/batch")
    @TpsControl(pointName = "NamingInstanceMetadataUpdate", name = "HttpNamingInstanceMetadataBatchUpdate")
    @Secured(action = ActionTypes.WRITE)
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceMetadataBatchHttpParamExtractor.class)
    public Result<InstanceMetadataBatchOperationVo> batchDeleteInstanceMetadata(InstanceMetadataBatchOperationForm form)
            throws NacosException {
        form.validate();
        List<Instance> targetInstances = parseBatchInstances(form.getInstances());
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(form.getMetadata());
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(buildCompositeServiceName(form),
                form.getConsistencyType(), targetInstances);
        List<String> operatedInstances = instanceServiceV2.batchDeleteMetadata(form.getNamespaceId(),
                instanceOperationInfo, targetMetadata);
        ArrayList<String> ipList = new ArrayList<>(operatedInstances);
        return Result.success(new InstanceMetadataBatchOperationVo(ipList));
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
     * Patch instance.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param metadata    service metadata
     * @param cluster     service cluster
     * @param ip          instance ip
     * @param port        instance port
     * @param weight      instance weight
     * @param enabled     instance enabled
     * @return 'ok' if success
     * @throws Exception any error during patch
     */
    @CanDistro
    @PatchMapping
    @Secured(action = ActionTypes.WRITE)
    public String patch(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String cluster,
            @RequestParam Integer port, @RequestParam Double weight, @RequestParam Boolean enabled,
            @RequestParam String metadata) throws Exception {
        NamingUtils.checkServiceNameFormat(serviceName);
        
        InstancePatchObject patchObject = new InstancePatchObject(cluster, ip, port);
        if (StringUtils.isNotBlank(metadata)) {
            patchObject.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }
        if (weight != null) {
            checkWeight(weight);
            patchObject.setWeight(weight);
        }
        if (enabled != null) {
            patchObject.setEnabled(enabled);
        }
        instanceServiceV2.patchInstance(namespaceId, serviceName, patchObject);
        return "ok";
    }
    
    /**
     * Get all instance of input service.
     *
     * @param namespaceId   namespace id
     * @param groupName     group name
     * @param serviceName   service name
     * @param clusterName   service clusterName
     * @param ip            ip
     * @param port          port
     * @param healthyOnly   healthyOnly
     * @param app           app
     * @param userAgent     [header] userAgent
     * @param clientVersion [header] clientVersion
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceSubscribe", name = "HttpNamingServiceSubscribe")
    @Secured(action = ActionTypes.READ)
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceListHttpParamExtractor.class)
    public Result<ServiceInfo> list(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "clusterName", defaultValue = StringUtils.EMPTY) String clusterName,
            @RequestParam(value = "ip", defaultValue = StringUtils.EMPTY) String ip,
            @RequestParam(value = "port", defaultValue = "0") Integer port,
            @RequestParam(value = "healthyOnly", defaultValue = "false") Boolean healthyOnly,
            @RequestParam(value = "app", defaultValue = StringUtils.EMPTY) String app,
            @RequestHeader(value = HttpHeaderConsts.USER_AGENT_HEADER, required = false) String userAgent,
            @RequestHeader(value = HttpHeaderConsts.CLIENT_VERSION_HEADER, required = false) String clientVersion) {
        if (StringUtils.isEmpty(userAgent)) {
            userAgent = StringUtils.defaultIfEmpty(clientVersion, StringUtils.EMPTY);
        }
        String compositeServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        Subscriber subscriber = new Subscriber(ip + ":" + port, userAgent, app, ip, namespaceId, compositeServiceName,
                port, clusterName);
        return Result.success(instanceServiceV2.listInstance(namespaceId, compositeServiceName, subscriber, clusterName,
                healthyOnly));
    }
    
    /**
     * Get detail information of specified instance.
     *
     * @param namespaceId service namespaceId
     * @param serviceName service serviceName
     * @param clusterName service clusterName
     * @param ip          instance ip
     * @param port        instance port
     * @return detail information of instance
     * @throws NacosException any error during get
     */
    @GetMapping
    @TpsControl(pointName = "NamingInstanceQuery", name = "HttpNamingInstanceQuery")
    @Secured(action = ActionTypes.READ)
    public Result<InstanceDetailInfoVo> detail(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "clusterName", defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String clusterName,
            @RequestParam("ip") String ip, @RequestParam("port") Integer port) throws NacosException {
        
        String compositeServiceName = NamingUtils.getGroupedName(serviceName, groupName);
        
        Instance instance = instanceServiceV2.getInstance(namespaceId, compositeServiceName, clusterName, ip, port);
        
        InstanceDetailInfoVo instanceDetailInfoVo = new InstanceDetailInfoVo();
        instanceDetailInfoVo.setServiceName(compositeServiceName);
        instanceDetailInfoVo.setIp(ip);
        instanceDetailInfoVo.setPort(port);
        instanceDetailInfoVo.setClusterName(clusterName);
        instanceDetailInfoVo.setWeight(instance.getWeight());
        instanceDetailInfoVo.setHealthy(instance.isHealthy());
        instanceDetailInfoVo.setInstanceId(instance.getInstanceId());
        instanceDetailInfoVo.setMetadata(instance.getMetadata());
        return Result.success(instanceDetailInfoVo);
    }
    
    /**
     * Create a beat for instance.
     *
     * @param namespaceId service namespaceId
     * @param serviceName service serviceName
     * @param ip          instance ip
     * @param clusterName service clusterName
     * @param port        instance port
     * @param beat        instance beat info
     * @return detail information of instance
     * @throws Exception any error during handle
     */
    @CanDistro
    @PutMapping("/beat")
    @TpsControl(pointName = "HttpHealthCheck", name = "HttpHealthCheck")
    @Secured(action = ActionTypes.WRITE)
    @ExtractorManager.Extractor(httpExtractor = NamingInstanceBeatHttpParamExtractor.class)
    public ObjectNode beat(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam(defaultValue = StringUtils.EMPTY) String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String clusterName,
            @RequestParam(defaultValue = "0") Integer port, @RequestParam(defaultValue = StringUtils.EMPTY) String beat)
            throws Exception {
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put(SwitchEntry.CLIENT_BEAT_INTERVAL, switchDomain.getClientBeatInterval());
        RsInfo clientBeat = null;
        if (StringUtils.isNotBlank(beat)) {
            clientBeat = JacksonUtils.toObj(beat, RsInfo.class);
        }
        if (clientBeat != null) {
            if (StringUtils.isNotBlank(clientBeat.getCluster())) {
                clusterName = clientBeat.getCluster();
            } else {
                // fix #2533
                clientBeat.setCluster(clusterName);
            }
            ip = clientBeat.getIp();
            port = clientBeat.getPort();
        }
        
        NamingUtils.checkServiceNameFormat(serviceName);
        Loggers.SRV_LOG.debug("[CLIENT-BEAT] full arguments: beat: {}, serviceName: {}, namespaceId: {}", clientBeat,
                serviceName, namespaceId);
        BeatInfoInstanceBuilder builder = BeatInfoInstanceBuilder.newBuilder();
        int resultCode = instanceServiceV2.handleBeat(namespaceId, serviceName, ip, port, clusterName, clientBeat,
                builder);
        result.put(CommonParams.CODE, resultCode);
        result.put(SwitchEntry.CLIENT_BEAT_INTERVAL,
                instanceServiceV2.getHeartBeatInterval(namespaceId, serviceName, ip, port, clusterName));
        result.put(SwitchEntry.LIGHT_BEAT_ENABLED, switchDomain.isLightBeatEnabled());
        return result;
    }
    
    /**
     * List all instance with health status.
     *
     * @param key (namespace##)?serviceName
     * @return list of instance
     * @throws NacosException any error during handle
     */
    @RequestMapping("/statuses/{key}")
    public ObjectNode listWithHealthStatus(@PathVariable String key) throws NacosException {
        
        String serviceName;
        String namespaceId;
        
        if (key.contains(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)) {
            namespaceId = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[0];
            serviceName = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[1];
        } else {
            namespaceId = Constants.DEFAULT_NAMESPACE_ID;
            serviceName = key;
        }
        NamingUtils.checkServiceNameFormat(serviceName);
        
        List<? extends Instance> ips = instanceServiceV2.listAllInstances(namespaceId, serviceName);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        ArrayNode ipArray = JacksonUtils.createEmptyArrayNode();
        for (Instance ip : ips) {
            ipArray.add(ip.toInetAddr() + "_" + ip.isHealthy());
        }
        result.replace("ips", ipArray);
        return result;
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
