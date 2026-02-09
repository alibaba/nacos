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

import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.Beta;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.controller.compatibility.Compatibility;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.model.request.LogUpdateRequest;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy kernel modules operate and maintain HTTP interfaces v2 (v2/core/ops).
 *
 * @author wuzhiguo
 */
@Beta
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/ops")
@Deprecated
public class CoreOpsV2Controller {
    
    private final ProtocolManager protocolManager;
    
    private final IdGeneratorManager idGeneratorManager;
    
    public CoreOpsV2Controller(ProtocolManager protocolManager, IdGeneratorManager idGeneratorManager) {
        this.protocolManager = protocolManager;
        this.idGeneratorManager = idGeneratorManager;
    }
    
    /**
     * Execute raft operations.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @PostMapping(value = "/raft")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "POST {contextPath:nacos}/v3/admin/core/ops/raft")
    public RestResult<String> raftOps(@RequestBody Map<String, String> commands) {
        return protocolManager.getCpProtocol().execute(commands);
    }
    
    /**
     * Get ID generator info list.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @GetMapping(value = "/ids")
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "GET {contextPath:nacos}/v3/admin/core/ops/ids")
    public RestResult<List<IdGeneratorInfo>> ids() {
        List<IdGeneratorInfo> result = new ArrayList<>();
        idGeneratorManager.getGeneratorMap().forEach((resource, idGenerator) -> {
            IdGeneratorInfo vo = new IdGeneratorInfo();
            vo.setResource(resource);
            
            IdGeneratorInfo.IdInfo info = new IdGeneratorInfo.IdInfo();
            info.setCurrentId(idGenerator.currentId());
            info.setWorkerId(idGenerator.workerId());
            vo.setInfo(info);
            
            result.add(vo);
        });
        
        return RestResultUtils.success(result);
    }
    
    /**
     * Update log level.
     *
     * @deprecated This API is deprecated and no longer maintained. Please use the v3 API instead.
     */
    @PutMapping(value = "/log")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    @Compatibility(apiType = ApiType.ADMIN_API, alternatives = "PUT {contextPath:nacos}/v3/admin/core/ops/log")
    public RestResult<Void> updateLog(@RequestBody LogUpdateRequest logUpdateRequest) {
        Loggers.setLogLevel(logUpdateRequest.getLogName(), logUpdateRequest.getLogLevel());
        return RestResultUtils.success();
    }
    
}
