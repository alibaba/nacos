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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.SubscribeManager;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service controller.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class ServiceControllerV3 {
    
    private final ServiceOperatorV2Impl serviceOperatorV2;
    
    private final SelectorManager selectorManager;
    
    private final SubscribeManager subscribeManager;
    
    public ServiceControllerV3(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager, SubscribeManager subscribeManager) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
        this.subscribeManager = subscribeManager;
    }
    
    /**
     * Create a new service. This API will create a persistence service.
     */
    @PostMapping()
    @TpsControl(pointName = "NamingServiceRegister", name = "HttpNamingServiceRegister")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> create(@RequestBody ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(serviceForm.getMetadata()));
        serviceMetadata.setEphemeral(serviceForm.getEphemeral());
        serviceOperatorV2.create(Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName(), serviceForm.getEphemeral()), serviceMetadata);
        NotifyCenter.publishEvent(
                new RegisterServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                        serviceForm.getGroupName(), serviceForm.getServiceName()));
        
        return Result.success("ok");
    }
    
    /**
     * Remove service.
     */
    @DeleteMapping()
    @TpsControl(pointName = "NamingServiceDeregister", name = "HttpNamingServiceDeregister")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> remove(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        serviceOperatorV2.delete(Service.newService(namespaceId, groupName, serviceName));
        NotifyCenter.publishEvent(
                new DeregisterServiceTraceEvent(System.currentTimeMillis(), namespaceId, groupName, serviceName));
        
        return Result.success("ok");
    }
    
    /**
     * Get detail of service.
     */
    @GetMapping()
    @TpsControl(pointName = "NamingServiceQuery", name = "HttpNamingServiceQuery")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ServiceDetailInfo> detail(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        ServiceDetailInfo result = serviceOperatorV2.queryService(
                Service.newService(namespaceId, groupName, serviceName));
        
        return Result.success(result);
    }
    
    /**
     * List all service names.
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceListQuery", name = "HttpNamingServiceListQuery")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ServiceNameView> list(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "selector", required = false, defaultValue = StringUtils.EMPTY) String selector,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize)
            throws Exception {
        pageSize = Math.min(500, pageSize);
        ServiceNameView result = new ServiceNameView();
        Collection<String> serviceNameList = serviceOperatorV2.listService(namespaceId, groupName, selector);
        result.setCount(serviceNameList.size());
        result.setServices(ServiceUtil.pageServiceName(pageNo, pageSize, serviceNameList));
        
        return Result.success(result);
    }
    
    /**
     * Update service.
     */
    @PutMapping()
    @TpsControl(pointName = "NamingServiceUpdate", name = "HttpNamingServiceUpdate")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> update(@RequestBody ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        Map<String, String> metadata = UtilsAndCommons.parseMetadata(serviceForm.getMetadata());
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setExtendData(metadata);
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        Service service = Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName());
        serviceOperatorV2.update(service, serviceMetadata);
        NotifyCenter.publishEvent(new UpdateServiceTraceEvent(System.currentTimeMillis(), serviceForm.getNamespaceId(),
                serviceForm.getGroupName(), serviceForm.getServiceName(), metadata));
        
        return Result.success("ok");
    }
    
    private Selector parseSelector(String selectorJsonString) throws Exception {
        if (StringUtils.isBlank(selectorJsonString)) {
            return new NoneSelector();
        }
        
        JsonNode selectorJson = JacksonUtils.toObj(URLDecoder.decode(selectorJsonString, "UTF-8"));
        String type = Optional.ofNullable(selectorJson.get("type")).orElseThrow(
                () -> new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SELECTOR_ERROR,
                        "not match any type of selector!")).asText();
        String expression = Optional.ofNullable(selectorJson.get("expression")).map(JsonNode::asText).orElse(null);
        Selector selector = selectorManager.parseSelector(type, expression);
        if (Objects.isNull(selector)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.SELECTOR_ERROR,
                    "not match any type of selector!");
        }
        return selector;
    }
    
    
    /**
     * Search service names.
     */
    @GetMapping("/names")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ObjectNode> searchService(@RequestParam(defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(defaultValue = StringUtils.EMPTY) String expr) throws NacosException {
        Map<String, Collection<String>> serviceNameMap = new HashMap<>(16);
        int totalCount = 0;
        if (StringUtils.isNotBlank(namespaceId)) {
            Collection<String> names = serviceOperatorV2.searchServiceName(namespaceId, expr);
            serviceNameMap.put(namespaceId, names);
            totalCount = names.size();
        } else {
            for (String each : serviceOperatorV2.listAllNamespace()) {
                Collection<String> names = serviceOperatorV2.searchServiceName(each, expr);
                serviceNameMap.put(each, names);
                totalCount += names.size();
            }
        }
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.replace("META-INF/services", JacksonUtils.transferToJsonNode(serviceNameMap));
        result.put("count", totalCount);
        
        return Result.success(result);
    }
    
    /**
     * get subscriber list.
     */
    @GetMapping("/subscribers")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ObjectNode> subscribers(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "aggregation", required = false, defaultValue = "true") boolean aggregation,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        
        int count = 0;
        
        try {
            List<Subscriber> subscribers = subscribeManager.getSubscribers(serviceName, namespaceId, aggregation);
            
            int start = (pageNo - 1) * pageSize;
            if (start < 0) {
                start = 0;
            }
            
            int end = start + pageSize;
            count = subscribers.size();
            if (end > count) {
                end = count;
            }
            
            result.replace("subscribers", JacksonUtils.transferToJsonNode(subscribers.subList(start, end)));
            result.put("count", count);
            
            return Result.success(result);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("query subscribers failed!", e);
            result.replace("subscribers", JacksonUtils.createEmptyArrayNode());
            result.put("count", count);
            return Result.failure(ErrorCode.SERVER_ERROR, result);
        }
    }
    
    /**
     * Get all {@link Selector} types.
     */
    @GetMapping("/selector/types")
    @Secured(resource = UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<String>> listSelectorTypes() {
        return Result.success(selectorManager.getAllSelectorTypes());
    }
}

