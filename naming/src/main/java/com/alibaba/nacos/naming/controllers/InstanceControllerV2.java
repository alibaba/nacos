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
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.InstancePatchObject;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.InstanceOperationInfo;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.instance.BeatInfoInstanceBuilder;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.naming.misc.UtilsAndCommons.DEFAULT_CLUSTER_NAME;

/**
 * Instance operation controller for v2.x.
 *
 * @author hujun
 */
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT)
public class InstanceControllerV2 {
    
    @Autowired
    private SwitchDomain switchDomain;
    
    @Autowired
    private InstanceOperatorClientImpl instanceServiceV2;
    
    /**
     * Register new instance.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param metadata    service metadata
     * @param cluster     service cluster
     * @param ip          instance ip
     * @param port        instance port
     * @param healthy     instance healthy
     * @param weight      instance weight
     * @param enabled     instance enabled
     * @param ephemeral   instance ephemeral
     * @return 'ok' if success
     * @throws Exception any error during register
     */
    @CanDistro
    @PostMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String register(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String cluster,
            @RequestParam Integer port, @RequestParam(defaultValue = "true") Boolean healthy,
            @RequestParam(defaultValue = "1") Double weight, @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam String metadata, @RequestParam Boolean ephemeral) throws Exception {
        
        NamingUtils.checkServiceNameFormat(serviceName);
        checkWeight(weight);
        final Instance instance = InstanceBuilder.newBuilder().setServiceName(serviceName).setIp(ip)
                .setClusterName(cluster).setPort(port).setHealthy(healthy).setWeight(weight).setEnabled(enabled)
                .setMetadata(UtilsAndCommons.parseMetadata(metadata)).setEphemeral(ephemeral).build();
        if (ephemeral == null) {
            instance.setEphemeral((switchDomain.isDefaultInstanceEphemeral()));
        }
        instanceServiceV2.registerInstance(namespaceId, serviceName, instance);
        return "ok";
    }
    
    /**
     * Deregister instances.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param metadata    service metadata
     * @param cluster     service cluster
     * @param ip          instance ip
     * @param port        instance port
     * @param healthy     instance healthy
     * @param weight      instance weight
     * @param enabled     instance enabled
     * @param ephemeral   instance ephemeral
     * @return 'ok' if success
     * @throws Exception any error during deregister
     */
    @CanDistro
    @DeleteMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String deregister(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String cluster,
            @RequestParam Integer port, @RequestParam(defaultValue = "true") Boolean healthy,
            @RequestParam(defaultValue = "1") Double weight, @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam String metadata, @RequestParam Boolean ephemeral) throws Exception {
        NamingUtils.checkServiceNameFormat(serviceName);
        checkWeight(weight);
        final Instance instance = InstanceBuilder.newBuilder().setServiceName(serviceName).setIp(ip)
                .setClusterName(cluster).setPort(port).setHealthy(healthy).setWeight(weight).setEnabled(enabled)
                .setMetadata(UtilsAndCommons.parseMetadata(metadata)).setEphemeral(ephemeral).build();
        if (ephemeral == null) {
            instance.setEphemeral((switchDomain.isDefaultInstanceEphemeral()));
        }
        
        instanceServiceV2.removeInstance(namespaceId, serviceName, instance);
        return "ok";
    }
    
    /**
     * Update instance.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param metadata    service metadata
     * @param cluster     service cluster
     * @param ip          instance ip
     * @param port        instance port
     * @param healthy     instance healthy
     * @param weight      instance weight
     * @param enabled     instance enabled
     * @param ephemeral   instance ephemeral
     * @return 'ok' if success
     * @throws Exception any error during update
     */
    @CanDistro
    @PutMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String update(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String cluster,
            @RequestParam Integer port, @RequestParam(defaultValue = "true") Boolean healthy,
            @RequestParam(defaultValue = "1") Double weight, @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestParam String metadata, @RequestParam Boolean ephemeral) throws Exception {
        
        NamingUtils.checkServiceNameFormat(serviceName);
        checkWeight(weight);
        final Instance instance = InstanceBuilder.newBuilder().setServiceName(serviceName).setIp(ip)
                .setClusterName(cluster).setPort(port).setHealthy(healthy).setWeight(weight).setEnabled(enabled)
                .setMetadata(UtilsAndCommons.parseMetadata(metadata)).setEphemeral(ephemeral).build();
        if (ephemeral == null) {
            instance.setEphemeral((switchDomain.isDefaultInstanceEphemeral()));
        }
        instanceServiceV2.updateInstance(namespaceId, serviceName, instance);
        return "ok";
    }
    
    /**
     * Batch update instance's metadata. old key exist = update, old key not exist = add.
     *
     * @param namespaceId     namespace id
     * @param serviceName     service name
     * @param metadata        service metadata
     * @param consistencyType consistencyType
     * @param instances       instances info
     * @return success updated instances. such as '{"updated":["2.2.2.2:8080:unknown:xxxx-cluster:ephemeral"}'.
     * @throws Exception any error during update
     * @since 1.4.0
     */
    @CanDistro
    @PutMapping(value = "/metadata/batch")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public ObjectNode batchUpdateInstanceMetadata(
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam(defaultValue = "") String consistencyType,
            @RequestParam(defaultValue = "") String instances, @RequestParam String metadata) throws Exception {
        
        List<Instance> targetInstances = parseBatchInstances(instances);
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(metadata);
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(serviceName, consistencyType, targetInstances);
        
        List<String> operatedInstances = instanceServiceV2.batchUpdateMetadata(namespaceId, instanceOperationInfo,
                targetMetadata);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        ArrayNode ipArray = JacksonUtils.createEmptyArrayNode();
        for (String ip : operatedInstances) {
            ipArray.add(ip);
        }
        result.replace("updated", ipArray);
        return result;
    }
    
    /**
     * Batch delete instance's metadata. old key exist = delete, old key not exist = not operate
     *
     * @param namespaceId     namespace id
     * @param serviceName     service name
     * @param metadata        service metadata
     * @param consistencyType consistencyType
     * @param instances       instances info
     * @return success updated instances. such as '{"updated":["2.2.2.2:8080:unknown:xxxx-cluster:ephemeral"}'.
     * @throws Exception any error during update
     * @since 1.4.0
     */
    @CanDistro
    @DeleteMapping("/metadata/batch")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public ObjectNode batchDeleteInstanceMetadata(
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam(defaultValue = "") String consistencyType,
            @RequestParam(defaultValue = "") String instances, @RequestParam String metadata) throws Exception {
        
        List<Instance> targetInstances = parseBatchInstances(instances);
        Map<String, String> targetMetadata = UtilsAndCommons.parseMetadata(metadata);
        InstanceOperationInfo instanceOperationInfo = buildOperationInfo(serviceName, consistencyType, targetInstances);
        List<String> operatedInstances = instanceServiceV2.batchDeleteMetadata(namespaceId, instanceOperationInfo,
                targetMetadata);
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        ArrayNode ipArray = JacksonUtils.createEmptyArrayNode();
        for (String ip : operatedInstances) {
            ipArray.add(ip);
        }
        result.replace("updated", ipArray);
        return result;
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
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
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
     * @param namespaceId namespace id
     * @param serviceName service name
     * @param clusters    service clusters
     * @param clientIP    service clientIP
     * @param udpPort     udpPort
     * @param healthyOnly healthyOnly
     * @param app         app
     * @param request     http request
     * @return list of instance
     * @throws Exception any error during list
     */
    @GetMapping("/list")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public Object list(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam(defaultValue = StringUtils.EMPTY) String clusters,
            @RequestParam(defaultValue = StringUtils.EMPTY) String clientIP,
            @RequestParam(defaultValue = "0") Integer udpPort,
            @RequestParam(defaultValue = "false") Boolean healthyOnly,
            @RequestParam(defaultValue = StringUtils.EMPTY) String app, HttpServletRequest request) throws Exception {
        
        NamingUtils.checkServiceNameFormat(serviceName);
        String agent = WebUtils.getUserAgent(request);
        Subscriber subscriber = new Subscriber(clientIP + ":" + udpPort, agent, app, clientIP, namespaceId, serviceName,
                udpPort, clusters);
        return instanceServiceV2.listInstance(namespaceId, serviceName, subscriber, clusters, healthyOnly);
    }
    
    
    /**
     * Get detail information of specified instance.
     *
     * @param namespaceId service namespaceId
     * @param serviceName service serviceName
     * @param ip          instance ip
     * @param clusterName service clusterName
     * @param port        instance port
     * @return detail information of instance
     * @throws Exception any error during get
     */
    @GetMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode detail(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam String serviceName, @RequestParam String ip,
            @RequestParam(defaultValue = UtilsAndCommons.DEFAULT_CLUSTER_NAME) String clusterName,
            @RequestParam Integer port) throws Exception {
        
        NamingUtils.checkServiceNameFormat(serviceName);
        
        Instance instance = instanceServiceV2.getInstance(namespaceId, serviceName, clusterName, ip, port);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("service", serviceName);
        result.put("ip", ip);
        result.put("port", port);
        result.put("clusterName", clusterName);
        result.put("weight", instance.getWeight());
        result.put("healthy", instance.isHealthy());
        result.put("instanceId", instance.getInstanceId());
        result.set("metadata", JacksonUtils.transferToJsonNode(instance.getMetadata()));
        return result;
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
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
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
            throw new NacosException(NacosException.INVALID_PARAM, "instance format invalid: The weights range from "
                    + com.alibaba.nacos.naming.constants.Constants.MIN_WEIGHT_VALUE + " to "
                    + com.alibaba.nacos.naming.constants.Constants.MAX_WEIGHT_VALUE);
        }
    }
}
