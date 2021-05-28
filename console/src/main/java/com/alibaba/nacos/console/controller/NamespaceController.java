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

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.console.enums.NamespaceTypeEnum;
import com.alibaba.nacos.console.model.Namespace;
import com.alibaba.nacos.console.model.NamespaceAllInfo;
import com.alibaba.nacos.console.security.nacos.NacosAuthConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
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
public class NamespaceController {
    
    @Autowired
    private PersistService persistService;
    
    private final Pattern namespaceIdCheckPattern = Pattern.compile("^[\\w-]+");
    
    private static final int NAMESPACE_ID_MAX_LENGTH = 128;
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final int DEFAULT_QUOTA = 200;
    
    private static final String DEFAULT_CREATE_SOURCE = "nacos";
    
    private static final String DEFAULT_NAMESPACE_SHOW_NAME = "Public";
    
    private static final String DEFAULT_NAMESPACE_DESCRIPTION = "Public Namespace";
    
    private static final String DEFAULT_TENANT = "";
    
    private static final String DEFAULT_KP = "1";
    
    /**
     * Get namespace list.
     *
     * @param request  request
     * @param response response
     * @return namespace list
     */
    @GetMapping
    public RestResult<List<Namespace>> getNamespaces(HttpServletRequest request, HttpServletResponse response) {
        // TODO 获取用kp
        List<TenantInfo> tenantInfos = persistService.findTenantByKp(DEFAULT_KP);
        Namespace namespace0 = new Namespace("", DEFAULT_NAMESPACE, DEFAULT_QUOTA, persistService.configInfoCount(DEFAULT_TENANT),
                NamespaceTypeEnum.GLOBAL.getType());
        List<Namespace> namespaces = new ArrayList<Namespace>();
        namespaces.add(namespace0);
        for (TenantInfo tenantInfo : tenantInfos) {
            int configCount = persistService.configInfoCount(tenantInfo.getTenantId());
            Namespace namespaceTmp = new Namespace(tenantInfo.getTenantId(), tenantInfo.getTenantName(), DEFAULT_QUOTA,
                    configCount, NamespaceTypeEnum.CUSTOM.getType());
            namespaces.add(namespaceTmp);
        }
        return RestResultUtils.success(namespaces);
    }
    
    /**
     * get namespace all info by namespace id.
     *
     * @param request     request
     * @param response    response
     * @param namespaceId namespaceId
     * @return namespace all info
     */
    @GetMapping(params = "show=all")
    public NamespaceAllInfo getNamespace(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("namespaceId") String namespaceId) {
        // TODO 获取用kp
        if (StringUtils.isBlank(namespaceId)) {
            return new NamespaceAllInfo(namespaceId, DEFAULT_NAMESPACE_SHOW_NAME, DEFAULT_QUOTA, persistService.configInfoCount(DEFAULT_TENANT),
                    NamespaceTypeEnum.GLOBAL.getType(), DEFAULT_NAMESPACE_DESCRIPTION);
        } else {
            TenantInfo tenantInfo = persistService.findTenantByKp(DEFAULT_KP, namespaceId);
            int configCount = persistService.configInfoCount(namespaceId);
            return new NamespaceAllInfo(namespaceId, tenantInfo.getTenantName(), DEFAULT_QUOTA, configCount, NamespaceTypeEnum.CUSTOM.getType(),
                    tenantInfo.getTenantDesc());
        }
    }
    
    /**
     * create namespace.
     *
     * @param request       request
     * @param response      response
     * @param namespaceName namespace Name
     * @param namespaceDesc namespace Desc
     * @return whether create ok
     */
    @PostMapping
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean createNamespace(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("customNamespaceId") String namespaceId, @RequestParam("namespaceName") String namespaceName,
            @RequestParam(value = "namespaceDesc", required = false) String namespaceDesc) {
        // TODO 获取用kp
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
            if (persistService.tenantInfoCountByTenantId(namespaceId) > 0) {
                return false;
            }
        }
        persistService.insertTenantInfoAtomic(DEFAULT_KP, namespaceId, namespaceName, namespaceDesc, DEFAULT_CREATE_SOURCE,
                System.currentTimeMillis());
        return true;
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
        return (persistService.tenantInfoCountByTenantId(namespaceId) > 0);
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
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean editNamespace(@RequestParam("namespace") String namespace,
            @RequestParam("namespaceShowName") String namespaceShowName,
            @RequestParam(value = "namespaceDesc", required = false) String namespaceDesc) {
        // TODO 获取用kp
        persistService.updateTenantNameAtomic(DEFAULT_KP, namespace, namespaceShowName, namespaceDesc);
        return true;
    }
    
    /**
     * del namespace by id.
     *
     * @param request     request
     * @param response    response
     * @param namespaceId namespace Id
     * @return whether del ok
     */
    @DeleteMapping
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Boolean deleteConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("namespaceId") String namespaceId) {
        persistService.removeTenantInfoAtomic(DEFAULT_KP, namespaceId);
        return true;
    }
    
}
