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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.trace.event.naming.DeregisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.RegisterServiceTraceEvent;
import com.alibaba.nacos.common.trace.event.naming.UpdateServiceTraceEvent;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.CatalogServiceV2Impl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.model.form.ServiceListForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
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
    
    private final CatalogServiceV2Impl catalogServiceV2;
    
    public ServiceControllerV3(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager,
            CatalogServiceV2Impl catalogServiceV2) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
        this.catalogServiceV2 = catalogServiceV2;
    }
    
    /**
     * Create a new service. This API will create a persistence service.
     */
    @PostMapping()
    @TpsControl(pointName = "NamingServiceRegister", name = "HttpNamingServiceRegister")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> create(ServiceForm serviceForm) throws Exception {
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
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> remove(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        String namespaceId = serviceForm.getNamespaceId();
        String groupName = serviceForm.getGroupName();
        String serviceName = serviceForm.getServiceName();
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
    @Secured(action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ServiceDetailInfo> detail(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        ServiceDetailInfo result = serviceOperatorV2.queryService(
                Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                        serviceForm.getServiceName()));
        
        return Result.success(result);
    }
    
    /**
     * List all service names.
     *
     * <ul>
     *     <li>
     *         if {@link ServiceListForm#isWithInstances()} is {@code true}, will return page {@link ServiceDetailInfo }
     *     </li>
     *     <li>
     *         if {@link ServiceListForm#isWithInstances()} is {@code false}, will return list {@link ServiceView }
     *     </li>
     * </ul>
     */
    @GetMapping("/list")
    @TpsControl(pointName = "NamingServiceListQuery", name = "HttpNamingServiceListQuery")
    @Secured(action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<Object> list(ServiceListForm serviceListForm, PageForm pageForm) throws Exception {
        serviceListForm.validate();
        pageForm.validate();
        String namespaceId = serviceListForm.getNamespaceId();
        String serviceName = serviceListForm.getServiceNameParam();
        String groupName = serviceListForm.getGroupNameParam();
        boolean hasIpCount = serviceListForm.isIgnoreEmptyService();
        boolean withInstances = serviceListForm.isWithInstances();
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        
        if (withInstances) {
            return Result.success(
                    catalogServiceV2.pageListServiceDetail(namespaceId, groupName, serviceName, pageNo, pageSize));
        }
        return Result.success(
                catalogServiceV2.listService(namespaceId, groupName, serviceName, pageNo, pageSize, hasIpCount));
    }
    
    /**
     * Update service.
     */
    @PutMapping()
    @TpsControl(pointName = "NamingServiceUpdate", name = "HttpNamingServiceUpdate")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> update(ServiceForm serviceForm) throws Exception {
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
     * get subscriber list.
     */
    @GetMapping("/subscribers")
    @Secured(action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<Page<SubscriberInfo>> subscribers(ServiceForm serviceForm, PageForm pageForm,
            AggregationForm aggregationForm) throws Exception {
        serviceForm.validate();
        pageForm.validate();
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        String namespaceId = serviceForm.getNamespaceId();
        String serviceName = serviceForm.getServiceName();
        String groupName = serviceForm.getGroupName();
        boolean aggregation = aggregationForm.isAggregation();
        return Result.success(
                serviceOperatorV2.getSubscribers(namespaceId, serviceName, groupName, aggregation, pageNo, pageSize));
    }
    
    /**
     * Get all {@link Selector} types.
     */
    @GetMapping("/selector/types")
    @Secured(action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<String>> listSelectorTypes() {
        return Result.success(selectorManager.getAllSelectorTypes());
    }
}

