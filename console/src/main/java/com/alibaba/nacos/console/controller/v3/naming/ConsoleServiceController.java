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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.proxy.naming.ServiceProxy;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ServiceForm;
import com.alibaba.nacos.naming.model.form.ServiceListForm;
import com.alibaba.nacos.naming.model.form.UpdateClusterForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.naming.selector.NoneSelector;
import com.alibaba.nacos.naming.selector.SelectorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.MediaType;
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
 * Controller for handling HTTP requests related to service operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/ns/service")
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
@Tag(name = "nacos.console.naming.service.api.controller.name", description = "nacos.console.naming.service.api.controller.description", extensions = {
        @Extension(name = RemoteConstants.LABEL_MODULE,
                properties = @ExtensionProperty(name = RemoteConstants.LABEL_MODULE, value = RemoteConstants.LABEL_MODULE_NAMING))})
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
    @Operation(summary = "nacos.console.naming.service.api.create.summary", description = "nacos.console.naming.service.api.create.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.create.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "protectThreshold", example = "0.5"), @Parameter(name = "ephemeral", example = "false"),
            @Parameter(name = "selector", example = "{\"type\":\"none\"}"),
            @Parameter(name = "metadata", example = "{\"version\":\"1.0\"}"),
            @Parameter(name = "serviceForm", hidden = true)})
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
    @Operation(summary = "nacos.console.naming.service.api.delete.summary", description = "nacos.console.naming.service.api.delete.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.delete.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "serviceForm", hidden = true)})
    public Result<String> deleteService(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        serviceProxy.deleteService(serviceForm.getNamespaceId(), serviceForm.getServiceName(),
                serviceForm.getGroupName());
        return Result.success("ok");
    }
    
    /**
     * Update service.
     */
    @PutMapping()
    @TpsControl(pointName = "NamingServiceUpdate", name = "HttpNamingServiceUpdate")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.naming.service.api.update.summary", description = "nacos.console.naming.service.api.update.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.update.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "protectThreshold", example = "0.5"), @Parameter(name = "ephemeral", example = "false"),
            @Parameter(name = "selector", example = "{\"type\":\"none\"}"),
            @Parameter(name = "metadata", example = "{\"version\":\"1.0\"}"),
            @Parameter(name = "serviceForm", hidden = true)})
    public Result<String> updateService(ServiceForm serviceForm) throws Exception {
        serviceForm.validate();
        Map<String, String> metadata = UtilsAndCommons.parseMetadata(serviceForm.getMetadata());
        ServiceMetadata serviceMetadata = new ServiceMetadata();
        serviceMetadata.setProtectThreshold(serviceForm.getProtectThreshold());
        serviceMetadata.setExtendData(metadata);
        serviceMetadata.setSelector(parseSelector(serviceForm.getSelector()));
        serviceProxy.updateService(serviceForm, serviceMetadata);
        return Result.success("ok");
    }
    
    /**
     * Get all {@link Selector} types.
     *
     * @return {@link Selector} types.
     */
    @GetMapping("/selector/types")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX
            + "naming", action = ActionTypes.READ, apiType = ApiType.CONSOLE_API, tags = Constants.Tag.ONLY_IDENTITY)
    @Operation(summary = "nacos.console.naming.service.api.selector.summary", description = "nacos.console.naming.service.api.selector.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.selector.example")))
    public Result<List<String>> getSelectorTypeList() throws NacosException {
        return Result.success(serviceProxy.getSelectorTypeList());
    }
    
    /**
     * get subscriber list.
     *
     * @param serviceForm     service form data
     * @param pageForm        page form data
     * @param aggregationForm whether aggregation form data
     * @return subscribes result data.
     * @throws Exception any exception during get subscriber list.
     */
    @GetMapping("/subscribers")
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.naming.service.api.subscribers.summary",
            description = "nacos.console.naming.service.api.subscribers.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.subscribers.example")))
    @Parameters(value = {@Parameter(name = "pageNo", required = true, example = "1"),
            @Parameter(name = "pageSize", required = true, example = "10"),
            @Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "aggregation", example = "true"), @Parameter(name = "serviceForm", hidden = true),
            @Parameter(name = "pageForm", hidden = true), @Parameter(name = "aggregationForm", hidden = true)})
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
        Page<SubscriberInfo> subscribers = serviceProxy.getSubscribers(pageNo, pageSize, namespaceId, serviceName,
                groupName, aggregation);
        return Result.success(subscribers);
    }
    
    /**
     * List service detail information.
     *
     * @param serviceListForm service list form
     * @param pageForm        page form
     * @return list service detail, depend on withInstances parameters, return ServiceDetailInfo or ServiceView.
     */
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @GetMapping("/list")
    @Operation(summary = "nacos.console.naming.service.api.list.summary",
            description = "nacos.console.naming.service.api.list.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.list.example")))
    @Parameters(value = {@Parameter(name = "pageNo", required = true, example = "1"),
            @Parameter(name = "pageSize", required = true, example = "10"),
            @Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "ignoreEmptyService", example = "true"),
            @Parameter(name = "withInstances", example = "false"), @Parameter(name = "serviceListForm", hidden = true),
            @Parameter(name = "pageForm", hidden = true)})
    public Result<Object> getServiceList(ServiceListForm serviceListForm, PageForm pageForm) throws NacosException {
        serviceListForm.validate();
        pageForm.validate();
        String namespaceId = serviceListForm.getNamespaceId();
        String serviceName = serviceListForm.getServiceNameParam();
        String groupName = serviceListForm.getGroupNameParam();
        boolean hasIpCount = serviceListForm.isIgnoreEmptyService();
        boolean withInstances = serviceListForm.isWithInstances();
        return Result.success(
                serviceProxy.getServiceList(withInstances, namespaceId, pageForm.getPageNo(), pageForm.getPageSize(),
                        serviceName, groupName, hasIpCount));
    }
    
    /**
     * Get service detail.
     *
     * @param serviceForm service form data
     * @return service detail information
     * @throws NacosException nacos exception
     */
    @Secured(action = ActionTypes.READ, apiType = ApiType.CONSOLE_API)
    @GetMapping()
    @Operation(summary = "nacos.console.naming.service.api.get.summary", description = "nacos.console.naming.service.api.get.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.get.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "serviceForm", hidden = true)})
    public Result<ServiceDetailInfo> getServiceDetail(ServiceForm serviceForm) throws NacosException {
        serviceForm.validate();
        ServiceDetailInfo result = serviceProxy.getServiceDetail(serviceForm.getNamespaceId(),
                serviceForm.getServiceName(), serviceForm.getGroupName());
        return Result.success(result);
    }
    
    /**
     * Update cluster.
     *
     * @param updateClusterForm update cluster form.
     * @return 'ok' if success
     * @throws Exception if failed
     */
    @PutMapping("/cluster")
    @Secured(action = ActionTypes.WRITE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.naming.service.api.update.cluster.summary",
            description = "nacos.console.naming.service.api.update.cluster.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.naming.service.api.update.cluster.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "test"),
            @Parameter(name = "clusterName", required = true, example = "DEFAULT"),
            @Parameter(name = "checkPort", example = "8080"), @Parameter(name = "useInstancePort4Check", example = "false"),
            @Parameter(name = "healthChecker", example = "{\"type\":\"none\"}"),
            @Parameter(name = "metadata", example = "{\"version\":\"1.0\"}"),
            @Parameter(name = "updateClusterForm", hidden = true)})
    public Result<String> updateCluster(UpdateClusterForm updateClusterForm) throws Exception {
        updateClusterForm.validate();
        final String namespaceId = updateClusterForm.getNamespaceId();
        final String clusterName = updateClusterForm.getClusterName();
        final String serviceName = updateClusterForm.getServiceName();
        final String groupName = updateClusterForm.getGroupName();
        ClusterMetadata clusterMetadata = new ClusterMetadata();
        clusterMetadata.setHealthyCheckPort(updateClusterForm.getCheckPort());
        clusterMetadata.setUseInstancePortForCheck(updateClusterForm.isUseInstancePort4Check());
        AbstractHealthChecker healthChecker = HealthCheckerFactory.deserialize(updateClusterForm.getHealthChecker());
        clusterMetadata.setHealthChecker(healthChecker);
        clusterMetadata.setHealthyCheckType(healthChecker.getType());
        clusterMetadata.setExtendData(UtilsAndCommons.parseMetadata(updateClusterForm.getMetadata()));
        serviceProxy.updateClusterMetadata(namespaceId, groupName, serviceName, clusterName, clusterMetadata);
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
}
