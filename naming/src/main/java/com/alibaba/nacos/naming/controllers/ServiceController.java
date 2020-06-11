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
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.*;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.LabelSelector;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.Selector;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.*;

/**
 * Service operation controller
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/service")
public class ServiceController {

    @Autowired
    protected ServiceManager serviceManager;

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private ServerMemberManager memberManager;

    @Autowired
    private SubscribeManager subscribeManager;

    @PostMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String create(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                         @RequestParam String serviceName,
                         @RequestParam(required = false) float protectThreshold,
                         @RequestParam(defaultValue = StringUtils.EMPTY) String metadata,
                         @RequestParam(defaultValue = StringUtils.EMPTY) String selector) throws Exception {

        if (serviceManager.getService(namespaceId, serviceName) != null) {
            throw new IllegalArgumentException("specified service already exists, serviceName : " + serviceName);
        }

        Map<String, String> metadataMap = new HashMap<>(16);
        if (StringUtils.isNotBlank(metadata)) {
            metadataMap = UtilsAndCommons.parseMetadata(metadata);
        }

        Service service = new Service(serviceName);
        service.setProtectThreshold(protectThreshold);
        service.setEnabled(true);
        service.setMetadata(metadataMap);
        service.setSelector(parseSelector(selector));
        service.setNamespaceId(namespaceId);


        // now valid the service. if failed, exception will be thrown
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        service.validate();

        serviceManager.addOrReplaceService(service);

        return "ok";
    }

    @DeleteMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String remove(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                         @RequestParam String serviceName) throws Exception {

        serviceManager.easyRemoveService(namespaceId, serviceName);

        return "ok";
    }

    @GetMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode detail(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
                             @RequestParam String serviceName) throws NacosException {

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND, "service " + serviceName + " is not found!");
        }

        ObjectNode res = JacksonUtils.createEmptyJsonNode();
        res.put("name", NamingUtils.getServiceName(serviceName));
        res.put("namespaceId", service.getNamespaceId());
        res.put("protectThreshold", service.getProtectThreshold());
        res.replace("metadata", JacksonUtils.transferToJsonNode(service.getMetadata()));
        res.replace("selector", JacksonUtils.transferToJsonNode(service.getSelector()));
        res.put("groupName", NamingUtils.getGroupName(serviceName));

        ArrayNode clusters = JacksonUtils.createEmptyArrayNode();
        for (Cluster cluster : service.getClusterMap().values()) {
            ObjectNode clusterJson = JacksonUtils.createEmptyJsonNode();
            clusterJson.put("name", cluster.getName());
            clusterJson.replace("healthChecker", JacksonUtils.transferToJsonNode(cluster.getHealthChecker()));
            clusterJson.replace("metadata", JacksonUtils.transferToJsonNode(cluster.getMetadata()));
            clusters.add(clusterJson);
        }

        res.replace("clusters", clusters);

        return res;
    }

    @GetMapping("/list")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode list(HttpServletRequest request) throws Exception {

        int pageNo = NumberUtils.toInt(WebUtils.required(request, "pageNo"));
        int pageSize = NumberUtils.toInt(WebUtils.required(request, "pageSize"));
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String groupName = WebUtils.optional(request, CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String selectorString = WebUtils.optional(request, "selector", StringUtils.EMPTY);

        List<String> serviceNameList = serviceManager.getAllServiceNameList(namespaceId);

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        if (serviceNameList == null || serviceNameList.isEmpty()) {
            result.replace("doms", JacksonUtils.transferToJsonNode(Collections.emptyList()));
            result.put("count", 0);
            return result;
        }

        serviceNameList.removeIf(serviceName -> !serviceName.startsWith(groupName + Constants.SERVICE_INFO_SPLITER));

        if (StringUtils.isNotBlank(selectorString)) {

            JsonNode selectorJson = JacksonUtils.toObj(selectorString);

            SelectorType selectorType = SelectorType.valueOf(selectorJson.get("type").asText());
            String expression = selectorJson.get("expression").asText();

            if (SelectorType.label.equals(selectorType) && StringUtils.isNotBlank(expression)) {
                expression = StringUtils.deleteWhitespace(expression);
                // Now we only support the following expression:
                // INSTANCE.metadata.xxx = 'yyy' or
                // SERVICE.metadata.xxx = 'yyy'
                String[] terms = expression.split("=");
                String[] factors = terms[0].split("\\.");
                switch (factors[0]) {
                    case "INSTANCE":
                        serviceNameList = filterInstanceMetadata(namespaceId, serviceNameList, factors[factors.length - 1], terms[1].replace("'", ""));
                        break;
                    case "SERVICE":
                        serviceNameList = filterServiceMetadata(namespaceId, serviceNameList, factors[factors.length - 1], terms[1].replace("'", ""));
                        break;
                    default:
                        break;
                }
            }
        }

        int start = (pageNo - 1) * pageSize;
        int end = start + pageSize;

        if (start < 0) {
            start = 0;
        }

        if (end > serviceNameList.size()) {
            end = serviceNameList.size();
        }

        for (int i = start; i < end; i++) {
            serviceNameList.set(i, serviceNameList.get(i).replace(groupName + Constants.SERVICE_INFO_SPLITER, ""));
        }

        result.replace("doms", JacksonUtils.transferToJsonNode(serviceNameList.subList(start, end)));
        result.put("count", serviceNameList.size());

        return result;

    }

    @PutMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public String update(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        float protectThreshold = NumberUtils.toFloat(WebUtils.required(request, "protectThreshold"));
        String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
        String selector = WebUtils.optional(request, "selector", StringUtils.EMPTY);

        Service service = serviceManager.getService(namespaceId, serviceName);
        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "service " + serviceName + " not found!");
        }

        service.setProtectThreshold(protectThreshold);

        Map<String, String> metadataMap = UtilsAndCommons.parseMetadata(metadata);
        service.setMetadata(metadataMap);
        service.setSelector(parseSelector(selector));
        service.setLastModifiedMillis(System.currentTimeMillis());
        service.recalculateChecksum();
        service.validate();

        serviceManager.addOrReplaceService(service);

        return "ok";
    }

    @RequestMapping("/names")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode searchService(@RequestParam(defaultValue = StringUtils.EMPTY) String namespaceId,
                                    @RequestParam(defaultValue = StringUtils.EMPTY) String expr,
                                    @RequestParam(required = false) boolean responsibleOnly) {

        Map<String, List<Service>> services = new HashMap<>(16);
        if (StringUtils.isNotBlank(namespaceId)) {
            services.put(namespaceId, serviceManager.searchServices(namespaceId, Constants.ANY_PATTERN + expr + Constants.ANY_PATTERN));
        } else {
            for (String namespace : serviceManager.getAllNamespaces()) {
                services.put(namespace, serviceManager.searchServices(namespace, Constants.ANY_PATTERN + expr + Constants.ANY_PATTERN));
            }
        }

        Map<String, Set<String>> serviceNameMap = new HashMap<>(16);
        for (String namespace : services.keySet()) {
            serviceNameMap.put(namespace, new HashSet<>());
            for (Service service : services.get(namespace)) {
                if (distroMapper.responsible(service.getName()) || !responsibleOnly) {
                    serviceNameMap.get(namespace).add(NamingUtils.getServiceName(service.getName()));
                }
            }
        }

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        result.replace("services", JacksonUtils.transferToJsonNode(serviceNameMap));
        result.put("count", services.size());

        return result;
    }

    @PostMapping("/status")
    public String serviceStatus(HttpServletRequest request) throws Exception {

        String entity = IoUtils.toString(request.getInputStream(), "UTF-8");
        String value = URLDecoder.decode(entity, "UTF-8");
        JsonNode json = JacksonUtils.toObj(value);

        //format: service1@@checksum@@@service2@@checksum
        String statuses = json.get("statuses").asText();
        String serverIp = json.get("clientIP").asText();

        if (!memberManager.hasMember(serverIp)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "ip: " + serverIp + " is not in serverlist");
        }

        try {
            ServiceManager.ServiceChecksum checksums = JacksonUtils.toObj(statuses, ServiceManager.ServiceChecksum.class);
            if (checksums == null) {
                Loggers.SRV_LOG.warn("[DOMAIN-STATUS] receive malformed data: null");
                return "fail";
            }

            for (Map.Entry<String, String> entry : checksums.serviceName2Checksum.entrySet()) {
                if (entry == null || StringUtils.isEmpty(entry.getKey()) || StringUtils.isEmpty(entry.getValue())) {
                    continue;
                }
                String serviceName = entry.getKey();
                String checksum = entry.getValue();
                Service service = serviceManager.getService(checksums.namespaceId, serviceName);

                if (service == null) {
                    continue;
                }

                service.recalculateChecksum();

                if (!checksum.equals(service.getChecksum())) {
                    if (Loggers.SRV_LOG.isDebugEnabled()) {
                        Loggers.SRV_LOG.debug("checksum of {} is not consistent, remote: {}, checksum: {}, local: {}",
                            serviceName, serverIp, checksum, service.getChecksum());
                    }
                    serviceManager.addUpdatedService2Queue(checksums.namespaceId, serviceName, serverIp, checksum);
                }
            }
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[DOMAIN-STATUS] receive malformed data: " + statuses, e);
        }

        return "ok";
    }

    @PutMapping("/checksum")
    public ObjectNode checksum(HttpServletRequest request) throws Exception {

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        Service service = serviceManager.getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.NOT_FOUND,
                "serviceName not found: " + serviceName);
        }

        service.recalculateChecksum();

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        result.put("checksum", service.getChecksum());

        return result;
    }

    /**
     * get subscriber list
     *
     * @param request
     * @return
     */
    @GetMapping("/subscribers")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public ObjectNode subscribers(HttpServletRequest request) {

        int pageNo = NumberUtils.toInt(WebUtils.required(request, "pageNo"));
        int pageSize = NumberUtils.toInt(WebUtils.required(request, "pageSize"));

        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        boolean aggregation = Boolean.parseBoolean(WebUtils.optional(request, "aggregation", String.valueOf(Boolean.TRUE)));

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

        try {
            List<Subscriber> subscribers = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);

            int start = (pageNo - 1) * pageSize;
            int end = start + pageSize;

            int count = subscribers.size();

            if (start < 0) {
                start = 0;
            }

            if (end > count) {
                end = count;
            }

            result.replace("subscribers", JacksonUtils.transferToJsonNode(subscribers.subList(start, end)));
            result.put("count", count);

            return result;
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("query subscribers failed!", e);
            result.replace("subscribers", JacksonUtils.createEmptyArrayNode());
            result.put("count", 0);
            return result;
        }
    }

    private List<String> filterInstanceMetadata(String namespaceId, List<String> serviceNames, String key, String value) {

        List<String> filteredServiceNames = new ArrayList<>();
        for (String serviceName : serviceNames) {
            Service service = serviceManager.getService(namespaceId, serviceName);
            if (service == null) {
                continue;
            }
            for (Instance address : service.allIPs()) {
                if (address.getMetadata() != null && value.equals(address.getMetadata().get(key))) {
                    filteredServiceNames.add(serviceName);
                    break;
                }
            }
        }
        return filteredServiceNames;
    }

    private List<String> filterServiceMetadata(String namespaceId, List<String> serviceNames, String key, String value) {

        List<String> filteredServices = new ArrayList<>();
        for (String serviceName : serviceNames) {
            Service service = serviceManager.getService(namespaceId, serviceName);
            if (service == null) {
                continue;
            }
            if (value.equals(service.getMetadata().get(key))) {
                filteredServices.add(serviceName);
            }

        }
        return filteredServices;
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
}
