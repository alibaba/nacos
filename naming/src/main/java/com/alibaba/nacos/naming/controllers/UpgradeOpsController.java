/*
 * Copyright (c) 1999-2021 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.InstanceOperator;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.InstanceOperatorServiceImpl;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.ServiceOperator;
import com.alibaba.nacos.naming.core.ServiceOperatorV1Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;
import static com.alibaba.nacos.api.common.Constants.SERVICE_INFO_SPLITER;
import static com.alibaba.nacos.naming.misc.UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR;

/**
 * Some API for v2 data ops when upgrading to v2.
 *
 * <p>Helping to resolve some unexpected problems when upgrading.
 *
 * @author gengtuo.ygt
 * on 2021/5/14
 * @deprecated will be removed at 2.1.x
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/upgrade/ops")
public class UpgradeOpsController {

    private final SwitchDomain switchDomain;

    private final ServiceManager serviceManager;

    private final ServiceOperatorV1Impl serviceOperatorV1;

    private final ServiceOperatorV2Impl serviceOperatorV2;

    private final InstanceOperatorServiceImpl instanceServiceV1;

    private final InstanceOperatorClientImpl instanceServiceV2;

    private final ServiceStorage serviceStorage;
    
    private final DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine;

    private final UpgradeJudgement upgradeJudgement;

    public UpgradeOpsController(SwitchDomain switchDomain,
                                ServiceManager serviceManager,
                                ServiceOperatorV1Impl serviceOperatorV1,
                                ServiceOperatorV2Impl serviceOperatorV2,
                                InstanceOperatorServiceImpl instanceServiceV1,
                                InstanceOperatorClientImpl instanceServiceV2,
                                ServiceStorage serviceStorage,
                                DoubleWriteDelayTaskEngine doubleWriteDelayTaskEngine,
                                UpgradeJudgement upgradeJudgement) {
        this.switchDomain = switchDomain;
        this.serviceManager = serviceManager;
        this.serviceOperatorV1 = serviceOperatorV1;
        this.serviceOperatorV2 = serviceOperatorV2;
        this.instanceServiceV1 = instanceServiceV1;
        this.instanceServiceV2 = instanceServiceV2;
        this.serviceStorage = serviceStorage;
        this.doubleWriteDelayTaskEngine = doubleWriteDelayTaskEngine;
        this.upgradeJudgement = upgradeJudgement;
    }

    /**
     * Get metrics information for upgrading view.
     *
     * @param json return json format
     * @return metrics about services and instances
     */
    @GetMapping("/metrics")
    public String metrics(@RequestParam(required = false, defaultValue = "false") boolean json) throws NacosException {
        ObjectNode result = getMetrics();
        if (json) {
            return JacksonUtils.toJson(result);
        } else {
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = result.fields();
            fields.forEachRemaining(e -> {
                sb.append(String.format("%-30s = ", e.getKey()));
                JsonNode value = e.getValue();
                if (value.isIntegralNumber()) {
                    sb.append(String.format("%5d", value.longValue()));
                } else if (value.isFloatingPointNumber()) {
                    sb.append(String.format("%.3f", value.doubleValue()));
                } else if (value.isTextual()) {
                    sb.append(value.textValue());
                } else {
                    sb.append(value.toString());
                }
                sb.append('\n');
            });
            return sb.toString();
        }
    }

    private ObjectNode getMetrics() throws NacosException {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        Set<String> serviceNamesV2 = new HashSet<>();
        Set<String> persistentServiceNamesV2 = new HashSet<>();
        Set<String> ephemeralServiceNamesV2 = new HashSet<>();
        int persistentInstanceCountV2 = 0;
        int ephemeralInstanceCountV2 = 0;
        Set<String> allNamespaces = com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().getAllNamespaces();
        for (String ns : allNamespaces) {
            Set<com.alibaba.nacos.naming.core.v2.pojo.Service> services
                    = com.alibaba.nacos.naming.core.v2.ServiceManager.getInstance().getSingletons(ns);
            for (com.alibaba.nacos.naming.core.v2.pojo.Service service : services) {
                String nameWithNs = service.getNamespace()
                        + NAMESPACE_SERVICE_CONNECTOR + service.getGroupedServiceName();
                serviceNamesV2.add(nameWithNs);
                if (service.isEphemeral()) {
                    ephemeralServiceNamesV2.add(nameWithNs);
                } else {
                    persistentServiceNamesV2.add(nameWithNs);
                }
                ServiceInfo data = serviceStorage.getPushData(service);
                for (com.alibaba.nacos.api.naming.pojo.Instance instance : data.getHosts()) {
                    if (instance.isEphemeral()) {
                        ephemeralInstanceCountV2 += 1;
                    } else {
                        persistentInstanceCountV2 += 1;
                    }
                }
            }
        }
        result.put("upgraded", upgradeJudgement.isUseGrpcFeatures());
        result.put("isAll20XVersion", upgradeJudgement.isAll20XVersion());
        result.put("isDoubleWriteEnabled", switchDomain.isDoubleWriteEnabled());
        result.put("doubleWriteDelayTaskCount", doubleWriteDelayTaskEngine.size());
        result.put("serviceCountV1", serviceManager.getServiceCount());
        result.put("instanceCountV1", serviceManager.getInstanceCount());
        result.put("serviceCountV2", MetricsMonitor.getDomCountMonitor().get());
        result.put("instanceCountV2", MetricsMonitor.getIpCountMonitor().get());
        result.put("subscribeCountV2", MetricsMonitor.getSubscriberCount().get());
        result.put("responsibleServiceCountV1", serviceManager.getResponsibleServiceCount());
        result.put("responsibleInstanceCountV1", serviceManager.getResponsibleInstanceCount());
        result.put("ephemeralServiceCountV2", ephemeralServiceNamesV2.size());
        result.put("persistentServiceCountV2", persistentServiceNamesV2.size());
        result.put("ephemeralInstanceCountV2", ephemeralInstanceCountV2);
        result.put("persistentInstanceCountV2", persistentInstanceCountV2);

        Set<String> serviceNamesV1 = serviceManager.getAllServiceNames().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(name -> {
                    if (!name.contains(SERVICE_INFO_SPLITER)) {
                        name = NamingUtils.getGroupedName(name, DEFAULT_GROUP);
                    }
                    return e.getKey() + NAMESPACE_SERVICE_CONNECTOR + name;
                }))
                .collect(Collectors.toSet());
        result.put("service.V1.not.in.V2", String.join("\n",
                (Collection<String>) CollectionUtils.subtract(serviceNamesV1, serviceNamesV2)));
        result.put("service.V2.not.in.V1", String.join("\n",
                (Collection<String>) CollectionUtils.subtract(serviceNamesV2, serviceNamesV1)));
        return result;
    }

    /**
     * Create a new service. This API will create a persistence service.
     *
     * @param namespaceId      namespace id
     * @param serviceName      service name
     * @param protectThreshold protect threshold
     * @param metadata         service metadata
     * @param selector         selector
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PostMapping("/service")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String createService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                HttpServletRequest request,
                                @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                @RequestParam String serviceName,
                                @RequestParam(required = false, defaultValue = "0.0F") float protectThreshold,
                                @RequestParam(defaultValue = StringUtils.EMPTY) String metadata,
                                @RequestParam(defaultValue = StringUtils.EMPTY) String selector) throws Exception {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(protectThreshold);
        serviceMetadata.setSelector(parseSelector(selector));
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(metadata));
        boolean ephemeral = BooleanUtils.toBoolean(
                WebUtils.optional(request, "ephemeral", String.valueOf(switchDomain.isDefaultInstanceEphemeral())));
        serviceMetadata.setEphemeral(ephemeral);
        getServiceOperator(ver).create(namespaceId, serviceName, serviceMetadata);
        return "ok";
    }

    /**
     * Remove service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @return 'ok' if success
     * @throws Exception exception
     */
    @DeleteMapping("/service")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String removeService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                @RequestParam String serviceName) throws Exception {
        getServiceOperator(ver).delete(namespaceId, serviceName);
        return "ok";
    }

    private ServiceOperator getServiceOperator(String ver) {
        return "v2".equals(ver) ? serviceOperatorV2 : serviceOperatorV1;
    }

    /**
     * Get detail of service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @return detail information of service
     * @throws NacosException nacos exception
     */
    @GetMapping("/service")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode detailService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                    @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                                    @RequestParam String serviceName) throws NacosException {
        return getServiceOperator(ver).queryService(namespaceId, serviceName);
    }

    /**
     * List all service names.
     *
     * @param request http request
     * @return all service names
     * @throws Exception exception
     */
    @GetMapping("/service/list")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode listService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                  HttpServletRequest request) throws Exception {
        final int pageNo = NumberUtils.toInt(WebUtils.required(request, "pageNo"));
        final int pageSize = NumberUtils.toInt(WebUtils.required(request, "pageSize"));
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String groupName = WebUtils.optional(request, CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String selectorString = WebUtils.optional(request, "selector", StringUtils.EMPTY);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        List<String> serviceNameList = getServiceOperator(ver)
                .listService(namespaceId, groupName, selectorString, pageSize, pageNo);
        result.replace("doms", JacksonUtils.transferToJsonNode(serviceNameList));
        result.put("count", serviceNameList.size());
        return result;
    }

    /**
     * Update service.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PutMapping("/service")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String updateService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(NumberUtils.toFloat(WebUtils.required(request, "protectThreshold")));
        serviceMetadata.setExtendData(
                UtilsAndCommons.parseMetadata(WebUtils.optional(request, "metadata", StringUtils.EMPTY)));
        serviceMetadata.setSelector(parseSelector(WebUtils.optional(request, "selector", StringUtils.EMPTY)));
        com.alibaba.nacos.naming.core.v2.pojo.Service service = com.alibaba.nacos.naming.core.v2.pojo.Service
                .newService(namespaceId, NamingUtils.getGroupName(serviceName),
                        NamingUtils.getServiceName(serviceName));
        getServiceOperator(ver).update(service, serviceMetadata);
        return "ok";
    }

    /**
     * Search service names.
     *
     * @param namespaceId     namespace
     * @param expr            search pattern
     * @param responsibleOnly whether only search responsible service
     * @return search result
     */
    @RequestMapping("/service/names")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode searchService(@RequestParam(defaultValue = "v2", required = false) String ver,
                                    @RequestParam(defaultValue = StringUtils.EMPTY) String namespaceId,
                                    @RequestParam(defaultValue = StringUtils.EMPTY) String expr,
                                    @RequestParam(required = false) boolean responsibleOnly) throws NacosException {
        Map<String, Collection<String>> serviceNameMap = new HashMap<>(16);
        int totalCount = 0;
        ServiceOperator serviceOperator = getServiceOperator(ver);
        if (StringUtils.isNotBlank(namespaceId)) {
            Collection<String> names = serviceOperator.searchServiceName(namespaceId, expr, responsibleOnly);
            serviceNameMap.put(namespaceId, names);
            totalCount = names.size();
        } else {
            for (String each : serviceOperator.listAllNamespace()) {
                Collection<String> names = serviceOperator.searchServiceName(each, expr, responsibleOnly);
                serviceNameMap.put(each, names);
                totalCount += names.size();
            }
        }
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("services", JacksonUtils.transferToJsonNode(serviceNameMap));
        result.put("count", totalCount);
        return result;
    }

    private Selector parseSelector(String selectorJsonString) throws Exception {

        if (StringUtils.isBlank(selectorJsonString)) {
            return new NoneSelector();
        }

        JsonNode selectorJson = JacksonUtils.toObj(URLDecoder.decode(selectorJsonString, "UTF-8"));
        switch (SelectorType.valueOf(selectorJson.get("type").asText())) {
            case none:
                return new NoneSelector();
            case label:
                String expression = selectorJson.get("expression").asText();
                Set<String> labels = LabelSelector.parseExpression(expression);
                LabelSelector labelSelector = new LabelSelector();
                labelSelector.setExpression(expression);
                labelSelector.setLabels(labels);
                return labelSelector;
            default:
                throw new NacosException(NacosException.INVALID_PARAM, "not match any type of selector!");
        }
    }


    /**
     * Register new instance.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception any error during register
     */
    @CanDistro
    @PostMapping("/instance")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String registerInstance(@RequestParam(defaultValue = "v2", required = false) String ver,
                                   HttpServletRequest request) throws Exception {

        final String namespaceId = WebUtils
                .optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        NamingUtils.checkServiceNameFormat(serviceName);

        final Instance instance = parseInstance(request);

        getInstanceOperator(ver).registerInstance(namespaceId, serviceName, instance);
        return "ok";
    }

    /**
     * Deregister instances.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception any error during deregister
     */
    @CanDistro
    @DeleteMapping("/instance")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String deregisterInstance(@RequestParam(defaultValue = "v2", required = false) String ver,
                                     HttpServletRequest request) throws Exception {
        Instance instance = getIpAddress(request);
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        NamingUtils.checkServiceNameFormat(serviceName);

        getInstanceOperator(ver).removeInstance(namespaceId, serviceName, instance);
        return "ok";
    }

    /**
     * Update instance.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception any error during update
     */
    @CanDistro
    @PutMapping("/instance")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String updateInstance(@RequestParam(defaultValue = "v2", required = false) String ver,
                                 HttpServletRequest request) throws Exception {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        NamingUtils.checkServiceNameFormat(serviceName);
        getInstanceOperator(ver).updateInstance(namespaceId, serviceName, parseInstance(request));
        return "ok";
    }


    /**
     * Get all instance of input service.
     *
     * @param request http request
     * @return list of instance
     * @throws Exception any error during list
     */
    @GetMapping("/instance/list")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public Object listInstance(@RequestParam(defaultValue = "v2", required = false) String ver,
                               HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        NamingUtils.checkServiceNameFormat(serviceName);

        String agent = WebUtils.getUserAgent(request);
        String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
        String clientIP = WebUtils.optional(request, "clientIP", StringUtils.EMPTY);
        int udpPort = Integer.parseInt(WebUtils.optional(request, "udpPort", "0"));
        boolean healthyOnly = Boolean.parseBoolean(WebUtils.optional(request, "healthyOnly", "false"));

        boolean isCheck = Boolean.parseBoolean(WebUtils.optional(request, "isCheck", "false"));

        String app = WebUtils.optional(request, "app", StringUtils.EMPTY);
        String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
        String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);

        Subscriber subscriber = new Subscriber(clientIP + ":" + udpPort, agent, app, clientIP, namespaceId, serviceName,
                udpPort, clusters);
        return getInstanceOperator(ver).listInstance(namespaceId, serviceName, subscriber, clusters, healthyOnly);
    }

    /**
     * Get detail information of specified instance.
     *
     * @param request http request
     * @return detail information of instance
     * @throws Exception any error during get
     */
    @GetMapping("/instance")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode detailInstance(@RequestParam(defaultValue = "v2", required = false) String ver,
                                     HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        NamingUtils.checkServiceNameFormat(serviceName);
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        String ip = WebUtils.required(request, "ip");
        int port = Integer.parseInt(WebUtils.required(request, "port"));

        com.alibaba.nacos.api.naming.pojo.Instance instance = getInstanceOperator(ver)
                .getInstance(namespaceId, serviceName, cluster, ip, port);
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put("service", serviceName);
        result.put("ip", ip);
        result.put("port", port);
        result.put("clusterName", cluster);
        result.put("weight", instance.getWeight());
        result.put("healthy", instance.isHealthy());
        result.put("instanceId", instance.getInstanceId());
        result.set("metadata", JacksonUtils.transferToJsonNode(instance.getMetadata()));
        return result;
    }

    private Instance parseInstance(HttpServletRequest request) throws Exception {

        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String app = WebUtils.optional(request, "app", "DEFAULT");
        Instance instance = getIpAddress(request);
        instance.setApp(app);
        instance.setServiceName(serviceName);
        // Generate simple instance id first. This value would be updated according to
        // INSTANCE_ID_GENERATOR.
        instance.setInstanceId(instance.generateInstanceId());
        instance.setLastBeat(System.currentTimeMillis());
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        if (StringUtils.isNotEmpty(metadata)) {
            instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
        }

        instance.validate();

        return instance;
    }

    private Instance getIpAddress(HttpServletRequest request) {

        String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
        boolean enabled;
        if (StringUtils.isBlank(enabledString)) {
            enabled = BooleanUtils.toBoolean(WebUtils.optional(request, "enable", "true"));
        } else {
            enabled = BooleanUtils.toBoolean(enabledString);
        }

        String weight = WebUtils.optional(request, "weight", "1");
        boolean healthy = BooleanUtils.toBoolean(WebUtils.optional(request, "healthy", "true"));

        Instance instance = getBasicIpAddress(request);
        instance.setWeight(Double.parseDouble(weight));
        instance.setHealthy(healthy);
        instance.setEnabled(enabled);

        return instance;
    }

    private Instance getBasicIpAddress(HttpServletRequest request) {

        final String ip = WebUtils.required(request, "ip");
        final String port = WebUtils.required(request, "port");
        String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
        if (StringUtils.isBlank(cluster)) {
            cluster = WebUtils.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        }
        boolean ephemeral = BooleanUtils.toBoolean(
                WebUtils.optional(request, "ephemeral", String.valueOf(switchDomain.isDefaultInstanceEphemeral())));

        Instance instance = new Instance();
        instance.setPort(Integer.parseInt(port));
        instance.setIp(ip);
        instance.setEphemeral(ephemeral);
        instance.setClusterName(cluster);

        return instance;
    }

    private InstanceOperator getInstanceOperator(String ver) {
        return "v2".equals(ver) ? instanceServiceV2 : instanceServiceV1;
    }

}
