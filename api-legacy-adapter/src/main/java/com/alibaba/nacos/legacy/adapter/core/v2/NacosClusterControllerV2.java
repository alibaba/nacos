/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.legacy.adapter.core.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.core.service.NacosClusterOperationService;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Legacy cluster communication interface v2 (v2/core/cluster).
 *
 * @author wuzhiguo
 */
@NacosApi
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/cluster")
@Deprecated
public class NacosClusterControllerV2 {
    
    private final NacosClusterOperationService nacosClusterOperationService;
    
    public NacosClusterControllerV2(NacosClusterOperationService nacosClusterOperationService) {
        this.nacosClusterOperationService = nacosClusterOperationService;
    }
    
    /**
     * Get self cluster member.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @GetMapping(value = "/node/self")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET {contextPath:nacos}/v3/admin/core/cluster/node/self")
    public Result<Member> self() {
        return Result.success(nacosClusterOperationService.self());
    }
    
    /**
     * List cluster nodes with optional address and state filter.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @GetMapping(value = "/node/list")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET {contextPath:nacos}/v3/admin/core/cluster/node/list")
    public Result<Collection<Member>> listNodes(@RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "state", required = false) String state) throws NacosException {
        
        NodeState nodeState = null;
        if (StringUtils.isNoneBlank(state)) {
            try {
                nodeState = NodeState.valueOf(state.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.ILLEGAL_STATE,
                        "Illegal state: " + state);
            }
        }
        return Result.success(nacosClusterOperationService.listNodes(address, nodeState));
    }
    
    /**
     * Get self node health.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @GetMapping(value = "/node/self/health")
    @Secured(action = ActionTypes.READ, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET {contextPath:nacos}/v3/admin/core/cluster/node/self/health")
    public Result<String> selfHealth() {
        return Result.success(nacosClusterOperationService.selfHealth());
    }
    
    /**
     * Update cluster node list.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @PutMapping(value = "/node/list")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "PUT {contextPath:nacos}/v3/admin/core/cluster/node/list")
    public Result<Boolean> updateNodes(@RequestBody List<Member> nodes) throws NacosApiException {
        if (nodes == null || nodes.size() == 0) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "required parameter 'nodes' is missing");
        }
        return Result.success(nacosClusterOperationService.updateNodes(nodes));
    }
    
    /**
     * Update lookup type.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @PutMapping(value = "/lookup")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "PUT {contextPath:nacos}/v3/admin/core/cluster/lookup")
    public Result<Boolean> updateLookup(LookupUpdateRequest request) throws NacosException {
        if (request == null || request.getType() == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "required parameter 'type' is missing");
        }
        return Result.success(nacosClusterOperationService.updateLookup(request));
    }
    
    /**
     * Delete cluster nodes (temporarily not allowed).
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @DeleteMapping("/nodes")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API)
    public RestResult<Void> deleteNodes(@RequestParam("addresses") List<String> addresses) throws Exception {
        return RestResultUtils.failed(405, null, "DELETE /v2/core/cluster/nodes API not allow to use temporarily.");
    }
    
}
