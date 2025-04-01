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

package com.alibaba.nacos.console.controller.v3.core;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.console.proxy.core.NamespaceProxy;
import com.alibaba.nacos.core.namespace.model.form.CreateNamespaceForm;
import com.alibaba.nacos.core.namespace.model.form.NamespaceForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for handling HTTP requests related to namespace operations.
 *
 * @author zhangyukun on:2024/8/27
 */
@NacosApi
@RestController
@RequestMapping("/v3/console/core/namespace")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
@Tag(name = "nacos.console.core.namespace.api.controller.name", description = "nacos.console.core.namespace.api.controller.description")
public class ConsoleNamespaceController {
    
    private final NamespaceProxy namespaceProxy;
    
    public ConsoleNamespaceController(NamespaceProxy namespaceProxy) {
        this.namespaceProxy = namespaceProxy;
    }
    
    /**
     * Get namespace list.
     *
     * @return namespace list
     */
    @GetMapping("/list")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces",
            action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API, tags = Constants.Tag.ONLY_IDENTITY)
    @Operation(summary = "nacos.console.core.namespace.api.list.summary", description = "nacos.console.core.namespace.api.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.list.example")))
    public Result<List<Namespace>> getNamespaceList() throws NacosException {
        return Result.success(namespaceProxy.getNamespaceList());
    }
    
    /**
     * get namespace all info by namespace id.
     *
     * @param namespaceId namespaceId
     * @return namespace all info
     */
    @GetMapping()
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.core.namespace.api.get.summary", description = "nacos.console.core.namespace.api.get.description",
            security = @SecurityRequirement(name = "nacos", scopes = "ADMIN:READ"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.get.example")))
    @Parameters(value = @Parameter(name = "namespaceId", required = true, example = "public"))
    public Result<Namespace> getNamespaceDetail(@RequestParam("namespaceId") String namespaceId) throws NacosException {
        return Result.success(namespaceProxy.getNamespaceDetail(namespaceId));
    }
    
    /**
     * create namespace.
     *
     * @param namespaceForm create namespace form.
     * @return whether create ok
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.core.namespace.api.create.summary", description = "nacos.console.core.namespace.api.create.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.create.example")))
    @Parameters(value = {@Parameter(name = "customNamespaceId"),
            @Parameter(name = "namespaceName", required = true, example = "test"),
            @Parameter(name = "namespaceDesc", example = "test"), @Parameter(name = "namespaceForm", hidden = true)})
    public Result<Boolean> createNamespace(CreateNamespaceForm namespaceForm) throws NacosException {
        namespaceForm.validate();
        String namespaceId = namespaceForm.getCustomNamespaceId();
        String namespaceName = namespaceForm.getNamespaceName();
        String namespaceDesc = namespaceForm.getNamespaceDesc();
        return Result.success(namespaceProxy.createNamespace(namespaceId, namespaceName, namespaceDesc));
    }
    
    /**
     * edit namespace.
     *
     * @param namespaceForm namespace form
     * @return whether edit ok
     */
    @PutMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.core.namespace.api.update.summary", description = "nacos.console.core.namespace.api.update.description",
             security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.update.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", required = true, example = "test"),
            @Parameter(name = "namespaceName", required = true, example = "test"),
            @Parameter(name = "namespaceDesc", example = "test"), @Parameter(name = "namespaceForm", hidden = true)})
    public Result<Boolean> updateNamespace(NamespaceForm namespaceForm) throws NacosException {
        namespaceForm.validate();
        return Result.success(namespaceProxy.updateNamespace(namespaceForm));
    }
    
    /**
     * delete namespace by id.
     *
     * @param namespaceId namespace ID
     * @return whether delete ok
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
    @Operation(summary = "nacos.console.core.namespace.api.delete.summary", description = "nacos.console.core.namespace.api.delete.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.delete.example")))
    @Parameters(value = @Parameter(name = "namespaceId", required = true, example = "test"))
    public Result<Boolean> deleteNamespace(@RequestParam("namespaceId") String namespaceId) throws NacosException {
        return Result.success(namespaceProxy.deleteNamespace(namespaceId));
    }
    
    /**
     * check namespaceId exist.
     *
     * @param namespaceId namespace id
     * @return true if exist, otherwise false
     */
    @GetMapping("/exist")
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces",
            action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API, tags = Constants.Tag.ONLY_IDENTITY)
    @Operation(summary = "nacos.console.core.namespace.api.check.summary", description = "nacos.console.core.namespace.api.check.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.console.core.namespace.api.check.example")))
    @Parameters(value = @Parameter(name = "customNamespaceId", required = true, example = "public"))
    public Result<Boolean> checkNamespaceIdExist(@RequestParam("customNamespaceId") String namespaceId)
            throws NacosException {
        // customNamespaceId if blank means create new namespace with uuid.
        if (StringUtils.isBlank(namespaceId)) {
            return Result.success(false);
        }
        return Result.success(namespaceProxy.checkNamespaceIdExist(namespaceId));
    }
}
