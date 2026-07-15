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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.visibility.AiVisibilityGrantInfo;
import com.alibaba.nacos.plugin.auth.impl.visibility.AiVisibilityGrantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Plugin-owned AI visibility grant API.
 *
 * @author Zhengcy05
 */
@RestController
@RequestMapping(AuthConstants.AI_VISIBILITY_PATH)
public class AiVisibilityGrantControllerV3 {
    
    private final AiVisibilityGrantService aiVisibilityGrantService;
    
    public AiVisibilityGrantControllerV3(AiVisibilityGrantService aiVisibilityGrantService) {
        this.aiVisibilityGrantService = aiVisibilityGrantService;
    }
    
    @Since("3.3.0")
    @PostMapping
    @Secured(resource = AuthConstants.AI_VISIBILITY_RESOURCE, action = ActionTypes.WRITE,
        tags = Constants.Tag.ONLY_IDENTITY)
    public Result<String> grant(@RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName,
        @RequestParam String username, @RequestParam String action) throws NacosException {
        aiVisibilityGrantService.grant(namespaceId, resourceType, resourceName, username, action);
        return Result.success("grant ai visibility permission ok!");
    }
    
    @Since("3.3.0")
    @DeleteMapping
    @Secured(resource = AuthConstants.AI_VISIBILITY_RESOURCE, action = ActionTypes.WRITE,
        tags = Constants.Tag.ONLY_IDENTITY)
    public Result<String> revoke(@RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName,
        @RequestParam String username, @RequestParam String action) throws NacosException {
        aiVisibilityGrantService.revoke(namespaceId, resourceType, resourceName, username, action);
        return Result.success("revoke ai visibility permission ok!");
    }
    
    @Since("3.3.0")
    @GetMapping("/list")
    @Secured(resource = AuthConstants.AI_VISIBILITY_RESOURCE, action = ActionTypes.READ,
        tags = Constants.Tag.ONLY_IDENTITY)
    public Result<List<AiVisibilityGrantInfo>> list(
        @RequestParam(required = false) String namespaceId,
        @RequestParam String resourceType, @RequestParam String resourceName)
        throws NacosException {
        return Result
            .success(aiVisibilityGrantService.list(namespaceId, resourceType, resourceName));
    }
}
