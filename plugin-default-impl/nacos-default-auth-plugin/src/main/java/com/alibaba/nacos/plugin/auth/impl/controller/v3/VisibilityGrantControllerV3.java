/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.visibility.VisibilityGrantInfo;
import com.alibaba.nacos.plugin.auth.impl.visibility.VisibilityGrantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Plugin-owned visibility grant API.
 *
 * @author Zhengcy05
 */
@RestController
@RequestMapping(AuthConstants.VISIBILITY_PATH)
public class VisibilityGrantControllerV3 {
    
    private final VisibilityGrantService visibilityGrantService;
    
    public VisibilityGrantControllerV3(VisibilityGrantService visibilityGrantService) {
        this.visibilityGrantService = visibilityGrantService;
    }
    
    /**
     * Grant one visibility action to a user for a resource.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @param username grantee username
     * @param action grant action
     * @return success result
     * @throws NacosException if validation or grant management fails
     */
    @Since("3.3.0")
    @PostMapping
    @Secured(resource = AuthConstants.VISIBILITY_RESOURCE, action = ActionTypes.WRITE,
        apiType = ApiType.ADMIN_API, tags = Constants.Tag.ONLY_IDENTITY)
    public Result<String> grant(@RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName,
        @RequestParam String username, @RequestParam String action) throws NacosException {
        visibilityGrantService.grant(namespaceId, resourceType, resourceName, username, action);
        return Result.success("grant visibility permission ok!");
    }
    
    /**
     * Revoke one visibility action from a user for a resource.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @param username grantee username
     * @param action grant action
     * @return success result
     * @throws NacosException if validation or grant management fails
     */
    @Since("3.3.0")
    @DeleteMapping
    @Secured(resource = AuthConstants.VISIBILITY_RESOURCE, action = ActionTypes.WRITE,
        apiType = ApiType.ADMIN_API, tags = Constants.Tag.ONLY_IDENTITY)
    public Result<String> revoke(@RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName,
        @RequestParam String username, @RequestParam String action) throws NacosException {
        visibilityGrantService.revoke(namespaceId, resourceType, resourceName, username, action);
        return Result.success("revoke visibility permission ok!");
    }
    
    /**
     * List grants attached to one resource.
     *
     * @param namespaceId namespace ID, blank for the default namespace
     * @param resourceType resource type
     * @param resourceName resource name
     * @return current grants
     * @throws NacosException if validation or grant management fails
     */
    @Since("3.3.0")
    @GetMapping("/list")
    @Secured(resource = AuthConstants.VISIBILITY_RESOURCE, action = ActionTypes.READ,
        apiType = ApiType.ADMIN_API, tags = Constants.Tag.ONLY_IDENTITY)
    public Result<List<VisibilityGrantInfo>> list(
        @RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName)
        throws NacosException {
        return Result.success(visibilityGrantService.list(namespaceId, resourceType, resourceName));
    }
}
