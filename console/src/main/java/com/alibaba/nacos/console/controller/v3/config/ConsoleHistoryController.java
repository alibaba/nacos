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

package com.alibaba.nacos.console.controller.v3.config;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.config.HistoryProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for handling HTTP requests related to history operations.
 *
 * @author zhangyukun on:2024/8/16
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/cs/history")
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
@Tag(name = "nacos.console.config.history.api.controller.name", description = "nacos.console.config.history.api.controller.description", extensions = {
        @Extension(name = RemoteConstants.LABEL_MODULE,
                properties = @ExtensionProperty(name = RemoteConstants.LABEL_MODULE, value = RemoteConstants.LABEL_MODULE_CONFIG))})
public class ConsoleHistoryController {
    
    private final HistoryProxy historyProxy;
    
    @Autowired
    public ConsoleHistoryController(HistoryProxy historyProxy) {
        this.historyProxy = historyProxy;
    }
    
    /**
     * Query the detailed configuration history information. notes:
     *
     * @param nid         history_config_info nid
     * @param configForm  config form
     * @return history config info
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.config.history.api.get.summary", description = "nacos.console.config.history.api.get.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.config.history.api.get.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", required = true, example = "DEFAULT_GROUP"),
            @Parameter(name = "dataId", required = true, example = "test"),
            @Parameter(name = "nid", required = true, example = "1"), @Parameter(name = "configForm", hidden = true)})
    public Result<ConfigHistoryDetailInfo> getConfigHistoryInfo(ConfigFormV3 configForm, @RequestParam("nid") Long nid)
            throws NacosException {
        configForm.validate();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        return Result.success(historyProxy.getConfigHistoryInfo(dataId, groupName, namespaceId, nid));
    }
    
    /**
     * Query the list history config. notes:
     *
     * @param configForm  config form
     * @param pageForm    page form
     * @return the page of history config.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.config.history.api.list.summary", description = "nacos.console.config.history.api.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.config.history.api.list.example")))
    @Parameters(value = {@Parameter(name = "pageNo", required = true, example = "1"),
            @Parameter(name = "pageSize", required = true, example = "100"),
            @Parameter(name = "namespaceId", example = "public"), @Parameter(name = "groupName", required = true),
            @Parameter(name = "dataId", required = true), @Parameter(name = "configForm", hidden = true),
            @Parameter(name = "pageForm", hidden = true)})
    public Result<Page<ConfigHistoryBasicInfo>> listConfigHistory(ConfigFormV3 configForm, PageForm pageForm)
            throws NacosException {
        configForm.validate();
        pageForm.validate();
        int pageSize = Math.min(500, pageForm.getPageSize());
        int pageNo = pageForm.getPageNo();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        return Result.success(historyProxy.listConfigHistory(dataId, groupName, namespaceId, pageNo, pageSize));
    }
    
    /**
     * Query previous config history information. notes:
     *
     * @param id          config_info id
     * @param configForm  config form
     * @return history config info
     */
    @GetMapping(value = "/previous")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.config.history.api.previous.summary", description = "nacos.console.config.history.api.previous.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.config.history.api.previous.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", required = true, example = "DEFAULT_GROUP"),
            @Parameter(name = "dataId", required = true, example = "test"),
            @Parameter(name = "id", required = true, example = "1"), @Parameter(name = "configForm", hidden = true)})
    public Result<ConfigHistoryDetailInfo> getPreviousConfigHistoryInfo(ConfigFormV3 configForm,
            @RequestParam("id") Long id) throws NacosException {
        configForm.validate();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        return Result.success(historyProxy.getPreviousConfigHistoryInfo(dataId, groupName, namespaceId, id));
    }
    
    /**
     * Query configs list by namespace.
     *
     * @param namespaceId config_info namespace
     * @return list
     */
    @GetMapping(value = "/configs")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.config.history.api.names.summary", description = "nacos.console.config.history.api.names.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.config.history.api.names.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public")})
    public Result<List<ConfigBasicInfo>> getConfigsByTenant(@RequestParam("namespaceId") String namespaceId)
            throws NacosException {
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        return Result.success(historyProxy.getConfigsByTenant(namespaceId));
    }
    
}
