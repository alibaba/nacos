/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.model.Namespace;
import com.alibaba.nacos.console.model.NamespaceAllInfo;
import com.alibaba.nacos.console.model.vo.NamespaceVo;
import com.alibaba.nacos.console.service.NamespaceOperationService;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * NamespaceControllerV2.
 * @author dongyafei
 * @date 2022/8/16
 */
@NacosApi
@RestController
@RequestMapping(path = "/v2/console/namespace")
public class NamespaceControllerV2 {

    private final NamespaceOperationService namespaceOperationService;
    
    public NamespaceControllerV2(NamespaceOperationService namespaceOperationService) {
        this.namespaceOperationService = namespaceOperationService;
    }
    
    private final Pattern namespaceIdCheckPattern = Pattern.compile("^[\\w-]+");
    
    private static final int NAMESPACE_ID_MAX_LENGTH = 128;
    
    /**
     * Get namespace list.
     *
     * @return namespace list
     */
    @GetMapping("/list")
    public Result<List<Namespace>> getNamespaceList() {
        return Result.success(namespaceOperationService.getNamespaceList());
    }
    
    /**
     * get namespace all info by namespace id.
     *
     * @param namespaceId namespaceId
     * @return namespace all info
     */
    @GetMapping()
    public Result<NamespaceAllInfo> getNamespace(@RequestParam("namespaceId") String namespaceId)
            throws NacosException {
        return Result.success(namespaceOperationService.getNamespace(namespaceId));
    }
    
    /**
     * create namespace.
     *
     * @param namespaceVo namespaceVo.
     * @return whether create ok
     */
    @PostMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Result<Boolean> createNamespace(@RequestBody NamespaceVo namespaceVo) throws NacosException {
        
        namespaceVo.validate();
        
        String namespaceId = namespaceVo.getNamespaceId();
        String namespaceName = namespaceVo.getNamespaceName();
        String namespaceDesc = namespaceVo.getNamespaceDesc();
        
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = UUID.randomUUID().toString();
        } else {
            namespaceId = namespaceId.trim();
            if (!namespaceIdCheckPattern.matcher(namespaceId).matches()) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.ILLEGAL_NAMESPACE,
                        "namespaceId [" + namespaceId + "] mismatch the pattern");
            }
            if (namespaceId.length() > NAMESPACE_ID_MAX_LENGTH) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.ILLEGAL_NAMESPACE,
                        "too long namespaceId, over " + NAMESPACE_ID_MAX_LENGTH);
            }
        }
        return Result.success(namespaceOperationService.createNamespace(namespaceId, namespaceName, namespaceDesc));
    }
    
    /**
     * edit namespace.
     *
     * @param namespaceVo       namespace params
     * @return whether edit ok
     */
    @PutMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Result<Boolean> editNamespace(@RequestBody NamespaceVo namespaceVo) throws NacosException {
        namespaceVo.validate();
        return Result.success(namespaceOperationService.editNamespace(namespaceVo.getNamespaceId(),
                namespaceVo.getNamespaceName(), namespaceVo.getNamespaceDesc()));
    }
    
    /**
     * delete namespace by id.
     *
     * @param namespaceId   namespace ID
     * @return whether delete ok
     */
    @DeleteMapping
    @Secured(resource = AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX + "namespaces", action = ActionTypes.WRITE)
    public Result<Boolean> deleteNamespace(@RequestParam("namespaceId") String namespaceId) {
        return Result.success(namespaceOperationService.removeNamespace(namespaceId));
    }
}
