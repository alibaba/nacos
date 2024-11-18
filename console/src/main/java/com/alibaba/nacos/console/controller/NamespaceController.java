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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.paramcheck.ConsoleDefaultHttpParamExtractor;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.namespace.model.Namespace;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * namespace service.
 *
 * @author Nacos
 */
@RestController
@RequestMapping("/v1/console/namespaces")
@ExtractorManager.Extractor(httpExtractor = ConsoleDefaultHttpParamExtractor.class)
public class NamespaceController {
    
    @Autowired
    private NamespacePersistService namespacePersistService;
    
    @Autowired
    private NamespaceOperationService namespaceOperationService;
    
    private final Pattern namespaceIdCheckPattern = Pattern.compile("^[\\w-]+");

    private final Pattern namespaceNameCheckPattern = Pattern.compile("^[^@#$%^&*]+$");
    
    private static final int NAMESPACE_ID_MAX_LENGTH = 128;
    
    /**
     * Get namespace list.
     *
     * @return namespace list
     */
    @GetMapping
    public RestResult<List<Namespace>> getNamespaces() {
        return RestResultUtils.success(namespaceOperationService.getNamespaceList());
    }
    
    /**
     * get namespace all info by namespace id.
     *
     * @param namespaceId namespaceId
     * @return namespace all info
     */
    @GetMapping(params = "show=all")
    public Namespace getNamespace(@RequestParam("namespaceId") String namespaceId) throws NacosException {
        return namespaceOperationService.getNamespace(namespaceId);
    }
    
    /**
     * create namespace.
     *
     * @param namespaceName namespace Name
     * @param namespaceDesc namespace Desc
     * @return whether create ok
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean createNamespace(@RequestParam("customNamespaceId") String namespaceId,
            @RequestParam("namespaceName") String namespaceName,
            @RequestParam(value = "namespaceDesc", required = false) String namespaceDesc) {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = UUID.randomUUID().toString();
        } else {
            namespaceId = namespaceId.trim();
            if (!namespaceIdCheckPattern.matcher(namespaceId).matches()) {
                return false;
            }
            if (namespaceId.length() > NAMESPACE_ID_MAX_LENGTH) {
                return false;
            }
            // check unique
            if (namespacePersistService.tenantInfoCountByTenantId(namespaceId) > 0) {
                return false;
            }
        }
        // contains illegal chars
        if (!namespaceNameCheckPattern.matcher(namespaceName).matches()) {
            return false;
        }
        try {
            return namespaceOperationService.createNamespace(namespaceId, namespaceName, namespaceDesc);
        } catch (NacosException e) {
            return false;
        }
    }
    
    /**
     * check namespaceId exist.
     *
     * @param namespaceId namespace id
     * @return true if exist, otherwise false
     */
    @GetMapping(params = "checkNamespaceIdExist=true")
    public Boolean checkNamespaceIdExist(@RequestParam("customNamespaceId") String namespaceId) {
        if (StringUtils.isBlank(namespaceId)) {
            return false;
        }
        return (namespacePersistService.tenantInfoCountByTenantId(namespaceId) > 0);
    }
    
    /**
     * edit namespace.
     *
     * @param namespace         namespace
     * @param namespaceShowName namespace ShowName
     * @param namespaceDesc     namespace Desc
     * @return whether edit ok
     */
    @PutMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean editNamespace(@RequestParam("namespace") String namespace,
            @RequestParam("namespaceShowName") String namespaceShowName,
            @RequestParam(value = "namespaceDesc", required = false) String namespaceDesc) {
        // contains illegal chars
        if (!namespaceNameCheckPattern.matcher(namespaceShowName).matches()) {
            return false;
        }
        return namespaceOperationService.editNamespace(namespace, namespaceShowName, namespaceDesc);
    }
    
    /**
     * del namespace by id.
     *
     * @param namespaceId namespace Id
     * @return whether del ok
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean deleteNamespace(@RequestParam("namespaceId") String namespaceId) {
        return namespaceOperationService.removeNamespace(namespaceId);
    }
    
}
