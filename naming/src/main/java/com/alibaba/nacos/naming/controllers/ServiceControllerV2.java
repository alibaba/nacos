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
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.Beta;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.ServiceDetailInfo;
import com.alibaba.nacos.naming.pojo.ServiceNameView;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.naming.utils.ServiceUtil;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Service operation controller.
 *
 * @author nkorange
 */
@Beta
@RestController
@RequestMapping(UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_SERVICE_CONTEXT)
public class ServiceControllerV2 {
    
    private final ServiceOperatorV2Impl serviceOperatorV2;
    
    private final SelectorManager selectorManager;
    
    public ServiceControllerV2(ServiceOperatorV2Impl serviceOperatorV2, SelectorManager selectorManager) {
        this.serviceOperatorV2 = serviceOperatorV2;
        this.selectorManager = selectorManager;
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
    @PostMapping(value = "/{serviceName}")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public RestResult<String> create(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @PathVariable String serviceName, @RequestParam(defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(required = false, defaultValue = "false") boolean ephemeral,
            @RequestParam(required = false, defaultValue = "0.0F") float protectThreshold,
            @RequestParam(defaultValue = StringUtils.EMPTY) String metadata,
            @RequestParam(defaultValue = StringUtils.EMPTY) String selector) throws Exception {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(protectThreshold);
        serviceMetadata.setSelector(parseSelector(selector));
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(metadata));
        serviceMetadata.setEphemeral(ephemeral);
        serviceOperatorV2.create(Service.newService(namespaceId, groupName, serviceName, ephemeral), serviceMetadata);
        return RestResultUtils.success("ok");
    }
    
    /**
     * Remove service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @return 'ok' if success
     * @throws Exception exception
     */
    @DeleteMapping(value = "/{serviceName}")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public RestResult<String> remove(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @PathVariable String serviceName, @RequestParam(defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        serviceOperatorV2.delete(Service.newService(namespaceId, groupName, serviceName));
        return RestResultUtils.success("ok");
    }
    
    /**
     * Get detail of service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @return detail information of service
     * @throws NacosException nacos exception
     */
    @GetMapping(value = "/{serviceName}")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public RestResult<ServiceDetailInfo> detail(
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @PathVariable String serviceName, @RequestParam(defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        ServiceDetailInfo result = serviceOperatorV2
                .queryService(Service.newService(namespaceId, groupName, serviceName));
        return RestResultUtils.success(result);
    }
    
    /**
     * List all service names.
     *
     * @param request http request
     * @return all service names
     * @throws Exception exception
     */
    @GetMapping("/list")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
    public RestResult<ServiceNameView> list(HttpServletRequest request) throws Exception {
        final int pageNo = NumberUtils.toInt(WebUtils.required(request, "pageNo"));
        final int pageSize = NumberUtils.toInt(WebUtils.required(request, "pageSize"));
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String groupName = WebUtils.optional(request, CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String selectorString = WebUtils.optional(request, "selector", StringUtils.EMPTY);
        ServiceNameView result = new ServiceNameView();
        Collection<String> serviceNameList = serviceOperatorV2.listService(namespaceId, groupName, selectorString);
        result.setCount(serviceNameList.size());
        result.setServices(ServiceUtil.pageServiceName(pageNo, pageSize, serviceNameList));
        return RestResultUtils.success(result);
        
    }
    
    /**
     * Update service.
     *
     * @param namespaceId      namespace id
     * @param serviceName      service name
     * @param protectThreshold protect threshold
     * @param metadata         service metadata
     * @param selector         selector
     * @return 'ok' if success
     * @throws Exception exception
     */
    @PutMapping(value = "/{serviceName}")
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public RestResult<String> update(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @PathVariable String serviceName, @RequestParam(defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(required = false, defaultValue = "0.0F") float protectThreshold,
            @RequestParam(defaultValue = StringUtils.EMPTY) String metadata,
            @RequestParam(defaultValue = StringUtils.EMPTY) String selector) throws Exception {
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(protectThreshold);
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(metadata));
        serviceMetadata.setSelector(parseSelector(selector));
        Service service = Service.newService(namespaceId, groupName, serviceName);
        serviceOperatorV2.update(service, serviceMetadata);
        return RestResultUtils.success("ok");
    }
    
    private Selector parseSelector(String selectorJsonString) throws Exception {
        if (StringUtils.isBlank(selectorJsonString)) {
            return new NoneSelector();
        }
        
        JsonNode selectorJson = JacksonUtils.toObj(URLDecoder.decode(selectorJsonString, "UTF-8"));
        String type = Optional.ofNullable(selectorJson.get("type"))
                .orElseThrow(() -> new NacosException(NacosException.INVALID_PARAM, "not match any type of selector!"))
                .asText();
        String expression = Optional.ofNullable(selectorJson.get("expression")).map(JsonNode::asText).orElse(null);
        Selector selector = selectorManager.parseSelector(type, expression);
        if (Objects.isNull(selector)) {
            throw new NacosException(NacosException.INVALID_PARAM, "not match any type of selector!");
        }
        return selector;
    }
}
