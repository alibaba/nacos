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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces",
            action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API, tags = Constants.Tag.ONLY_IDENTITY)
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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX
            + "namespaces", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API)
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
    @Secured(resource = Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces",
            action = ActionTypes.READ, signType = SignType.CONSOLE, apiType = ApiType.CONSOLE_API, tags = Constants.Tag.ONLY_IDENTITY)
    public Result<Boolean> checkNamespaceIdExist(@RequestParam("customNamespaceId") String namespaceId)
            throws NacosException {
        // customNamespaceId if blank means create new namespace with uuid.
        if (StringUtils.isBlank(namespaceId)) {
            return Result.success(false);
        }
        return Result.success(namespaceProxy.checkNamespaceIdExist(namespaceId));
    }
}
