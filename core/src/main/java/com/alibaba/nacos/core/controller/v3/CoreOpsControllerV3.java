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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.model.form.v3.RaftCommandForm;
import com.alibaba.nacos.core.model.request.LogUpdateRequest;
import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
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

import static com.alibaba.nacos.core.utils.Commons.NACOS_ADMIN_CORE_CONTEXT_V3;

/**
 * Kernel modules operate and maintain HTTP interfaces v3.
 *
 * @author yunye
 * @since 3.0.0
 */
@NacosApi
@RestController
@RequestMapping(NACOS_ADMIN_CORE_CONTEXT_V3 + "/ops")
public class CoreOpsControllerV3 {
    
    private final ProtocolManager protocolManager;
    
    private final IdGeneratorManager idGeneratorManager;
    
    public CoreOpsControllerV3(ProtocolManager protocolManager, IdGeneratorManager idGeneratorManager) {
        this.protocolManager = protocolManager;
        this.idGeneratorManager = idGeneratorManager;
    }
    
    /**
     * Temporarily overpassed the raft operations interface.
     * <p>
     * { "groupId": "xxx", "command": "transferLeader or doSnapshot or resetRaftCluster or removePeer" "value":
     * "ip:{raft_port}" }
     * </p>
     *
     * @param form RaftCommandForm
     * @return {@link RestResult}
     */
    @PostMapping(value = "/raft")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/ops", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<String> raftOps(@RequestBody RaftCommandForm form) {
        return Result.success(protocolManager.getCpProtocol().execute(form.toMap()).getData());
    }
    
    /**
     * Gets the current health of the ID generator.
     *
     * @return {@link RestResult}
     */
    @GetMapping(value = "/ids")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/ops", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<List<IdGeneratorInfo>> ids() {
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
        
        return Result.success(result);
    }
    
    @PutMapping(value = "/log")
    @Secured(resource = Commons.NACOS_ADMIN_CORE_CONTEXT_V3
            + "/ops", action = ActionTypes.WRITE, signType = SignType.CONSOLE, apiType = ApiType.ADMIN_API)
    public Result<Void> updateLog(@RequestBody LogUpdateRequest logUpdateRequest) {
        Loggers.setLogLevel(logUpdateRequest.getLogName(), logUpdateRequest.getLogLevel());
        return Result.success();
    }
}
