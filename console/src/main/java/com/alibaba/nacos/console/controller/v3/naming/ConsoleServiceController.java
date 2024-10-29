/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.controller.v3.naming;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.enums.ApiType;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.naming.ServiceProxy;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for handling HTTP requests related to service operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/ns/service")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class ConsoleServiceController {
    
    private final ServiceProxy serviceProxy;
    
    private final SelectorManager selectorManager;
    
    public ConsoleServiceController(ServiceProxy serviceProxy, SelectorManager selectorManager) {
        this.serviceProxy = serviceProxy;
        this.selectorManager = selectorManager;
    }
    
    /**
     * Create a new service. This API will create a persistence service.
     */
    @PostMapping()
    @TpsControl(pointName = "NamingServiceRegister", name = "HttpNamingServiceRegister")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    public Result<String> createService(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        serviceMetadata.setExtendData(UtilsAndCommons.parseMetadata(serviceForm.getMetadata()));
        serviceMetadata.setEphemeral(serviceForm.getEphemeral());
        
        serviceProxy.createService(serviceForm, serviceMetadata);
        return Result.success("ok");
    }
    
    /**
     * Remove service.
     */
    @DeleteMapping()
    @TpsControl(pointName = "NamingServiceDeregister", name = "HttpNamingServiceDeregister")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteService(
            @RequestParam(value = "namespaceId", defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws Exception {
        checkServiceName(serviceName);
        serviceProxy.deleteService(namespaceId, serviceName, groupName);
        return Result.success("ok");
    }
    
    /**
     * Update service.
     */
    @PutMapping()
    @TpsControl(pointName = "NamingServiceUpdate", name = "HttpNamingServiceUpdate")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    public Result<String> updateService(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        
        Map<String, String> metadata = UtilsAndCommons.parseMetadata(serviceForm.getMetadata());
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setExtendData(metadata);
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        Service service = Service.newService(serviceForm.getNamespaceId(), serviceForm.getGroupName(),
                serviceForm.getServiceName());
        
        serviceProxy.updateService(serviceForm, service, serviceMetadata, metadata);
        return Result.success("ok");
    }
    
    /**
     * Get all {@link Selector} types.
     *
     * @return {@link Selector} types.
     */
    @GetMapping("/selector/types")
    public Result<List<String>> getSelectorTypeList() {
        return Result.success(serviceProxy.getSelectorTypeList());
    }
    
    /**
     * get subscriber list.
     *
     * @param request http request
     * @return Jackson object node
     */
    @GetMapping("/subscribers")
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    public Result<ObjectNode> subscribers(HttpServletRequest request) throws Exception {
        
        int pageNo = NumberUtils.toInt(WebUtils.optional(request, "pageNo", "1"));
        int pageSize = NumberUtils.toInt(WebUtils.optional(request, "pageSize", "1000"));
        
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        String groupName = WebUtils.optional(request, CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        
        boolean aggregation = Boolean.parseBoolean(
                WebUtils.optional(request, "aggregation", String.valueOf(Boolean.TRUE)));
        
        return Result.success(
                serviceProxy.getSubscribers(pageNo, pageSize, namespaceId, serviceName, groupName, aggregation));
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
     * List service detail information.
     *
     * @param withInstances     whether return instances
     * @param namespaceId       namespace id
     * @param pageNo            number of page
     * @param pageSize          size of each page
     * @param serviceName       service name
     * @param groupName         group name
     * @param containedInstance instance name pattern which will be contained in detail
     * @param hasIpCount        whether filter services with empty instance
     * @return list service detail
     */
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @GetMapping("/list")
    public Object getServiceList(@RequestParam(required = false) boolean withInstances,
            @RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(required = false) int pageNo, @RequestParam(required = false) int pageSize,
            @RequestParam(name = "serviceNameParam", defaultValue = StringUtils.EMPTY) String serviceName,
            @RequestParam(name = "groupNameParam", defaultValue = StringUtils.EMPTY) String groupName,
            @RequestParam(name = "instance", defaultValue = StringUtils.EMPTY) String containedInstance,
            @RequestParam(required = false) boolean hasIpCount) throws NacosException {
        return Result.success(
                serviceProxy.getServiceList(withInstances, namespaceId, pageNo, pageSize, serviceName, groupName,
                        containedInstance, hasIpCount));
    }
    
    /**
     * Get service detail.
     *
     * @param namespaceId namespace id
     * @param serviceName service name
     * @return service detail information
     * @throws NacosException nacos exception
     */
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @GetMapping()
    public Object getServiceDetail(@RequestParam(defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "groupName", defaultValue = Constants.DEFAULT_GROUP) String groupName)
            throws NacosException {
        checkServiceName(serviceName);
        return Result.success(serviceProxy.getServiceDetail(namespaceId, serviceName, groupName));
    }
    
    /**
     * Update cluster.
     *
     * @param request http request
     * @return 'ok' if success
     * @throws Exception if failed
     */
    @PutMapping("/cluster")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    public Result<String> updateCluster(HttpServletRequest request) throws Exception {
        final String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
                Constants.DEFAULT_NAMESPACE_ID);
        final String clusterName = WebUtils.required(request, CommonParams.CLUSTER_NAME);
        final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(NumberUtils.toInt(WebUtils.required(request, "checkPort")));
        clusterMetadata.setUseInstancePortForCheck(
                ConvertUtils.toBoolean(WebUtils.required(request, "useInstancePort4Check")));
        AbstractHealthChecker healthChecker = HealthCheckerFactory.deserialize(
                WebUtils.required(request, "healthChecker"));
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(
                UtilsAndCommons.parseMetadata(WebUtils.optional(request, "metadata", StringUtils.EMPTY)));
        
        serviceProxy.updateClusterMetadata(namespaceId, serviceName, clusterName, clusterMetadata);
        return Result.success("ok");
    }
    
    private void checkServiceName(String serviceName) throws NacosApiException {
        if (StringUtils.isBlank(serviceName)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'serviceName' type String is not present");
        }
    }
    
}
