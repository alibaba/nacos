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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.core.model.request.LookupUpdateRequest;
import com.alibaba.nacos.core.service.NacosClusterOperationService;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static com.alibaba.nacos.core.utils.Commons.NACOS_ADMIN_CORE_CONTEXT_V3;

/**
 * Cluster communication interface v3.
 *
 * @author yunye
 * @since 3.0.0
 */
@NacosApi
@RestController
@RequestMapping(NACOS_ADMIN_CORE_CONTEXT_V3 + "/cluster")
public class NacosClusterControllerV3 {
    
    private final NacosClusterOperationService nacosClusterOperationService;
    
    public NacosClusterControllerV3(NacosClusterOperationService nacosClusterOperationService) {
        this.nacosClusterOperationService = nacosClusterOperationService;
    }
    
    @GetMapping(value = "/node/self")
    @Secured(action = ActionTypes.READ, resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/cluster", signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<Member> self() {
        return Result.success(nacosClusterOperationService.self());
    }
    
    /**
     * The console displays the list of cluster members.
     *
     * @param address match address
     * @param state   match state
     * @return members that matches condition
     */
    @GetMapping(value = "/node/list")
    @Secured(action = ActionTypes.READ, resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/cluster", signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
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
    
    @GetMapping(value = "/node/self/health")
    @Secured(action = ActionTypes.READ, resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/cluster", signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<String> selfHealth() {
        return Result.success(nacosClusterOperationService.selfHealth());
    }
    
    // The client can get all the nacos node information in the current
    // cluster according to this interface
    
    /**
     * Other nodes return their own metadata information.
     *
     * @param nodes List of {@link Member}
     * @return {@link RestResult}
     */
    @PutMapping(value = "/node/list")
    @Secured(action = ActionTypes.WRITE, resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/cluster", signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<Boolean> updateNodes(@RequestBody List<Member> nodes) throws NacosApiException {
        if (nodes == null || nodes.size() == 0) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "required parameter 'nodes' is missing");
        }
        return Result.success(nacosClusterOperationService.updateNodes(nodes));
    }
    
    /**
     * Addressing mode switch.
     *
     * @param request {@link LookupUpdateRequest}
     * @return {@link RestResult}
     */
    @PutMapping(value = "/lookup")
    @Secured(action = ActionTypes.WRITE, resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/cluster", signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<Boolean> updateLookup(LookupUpdateRequest request) throws NacosException {
        if (request == null || request.getType() == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "required parameter 'type' is missing");
        }
        return Result.success(nacosClusterOperationService.updateLookup(request));
    }
    
}
