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

package com.alibaba.nacos.core.controller.v2;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.common.Beta;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.core.model.request.LogUpdateRequest;
import com.alibaba.nacos.core.model.vo.IdGeneratorVO;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.Loggers;
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
 * Kernel modules operate and maintain HTTP interfaces v2.
 *
 * @author wuzhiguo
 */
@Beta
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/ops")
public class CoreOpsV2Controller {
    
    private final ProtocolManager protocolManager;
    
    private final IdGeneratorManager idGeneratorManager;
    
    public CoreOpsV2Controller(ProtocolManager protocolManager, IdGeneratorManager idGeneratorManager) {
        this.protocolManager = protocolManager;
        this.idGeneratorManager = idGeneratorManager;
    }
    
    /**
     * Temporarily overpassed the raft operations interface.
     * <p>
     *      {
     *           "groupId": "xxx",
     *           "command": "transferLeader or doSnapshot or resetRaftCluster or removePeer"
     *           "value": "ip:{raft_port}"
     *      }
     * </p>
     * @param commands transferLeader or doSnapshot or resetRaftCluster or removePeer
     * @return {@link RestResult}
     */
    @PostMapping(value = "/raft")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    public RestResult<String> raftOps(@RequestBody Map<String, String> commands) {
        return protocolManager.getCpProtocol().execute(commands);
    }
    
    /**
     * Gets the current health of the ID generator.
     *
     * @return {@link RestResult}
     */
    @GetMapping(value = "/ids")
    public RestResult<List<IdGeneratorVO>> ids() {
        List<IdGeneratorVO> result = new ArrayList<>();
        idGeneratorManager.getGeneratorMap().forEach((resource, idGenerator) -> {
            IdGeneratorVO vo = new IdGeneratorVO();
            vo.setResource(resource);
            
            IdGeneratorVO.IdInfo info = new IdGeneratorVO.IdInfo();
            info.setCurrentId(idGenerator.currentId());
            info.setWorkerId(idGenerator.workerId());
            vo.setInfo(info);
            
            result.add(vo);
        });
        
        return RestResultUtils.success(result);
    }
    
    @PutMapping(value = "/log")
    @Secured(action = ActionTypes.WRITE, resource = "nacos/admin", signType = SignType.CONSOLE)
    public RestResult<Void> updateLog(@RequestBody LogUpdateRequest logUpdateRequest) {
        Loggers.setLogLevel(logUpdateRequest.getLogName(), logUpdateRequest.getLogLevel());
        return RestResultUtils.success();
    }
    
}
