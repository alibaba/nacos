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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftOps;
import com.alipay.sofa.jraft.CliService;
import com.alipay.sofa.jraft.Node;

import java.util.Map;
import java.util.Objects;

/**
 * JRaft operations interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JRaftMaintainService {
    
    private final JRaftServer raftServer;
    
    public JRaftMaintainService(JRaftServer raftServer) {
        this.raftServer = raftServer;
    }
    
    public RestResult<String> execute(String[] args) {
        return RestResultUtils.failed("not support yet");
    }
    
    /**
     * Execute relevant commands.
     *
     * @param args {@link Map}
     * @return {@link RestResult}
     */
    public RestResult<String> execute(Map<String, String> args) {
        final CliService cliService = raftServer.getCliService();
        if (args.containsKey(JRaftConstants.GROUP_ID)) {
            final String groupId = args.get(JRaftConstants.GROUP_ID);
            final Node node = raftServer.findNodeByGroup(groupId);
            return single(cliService, groupId, node, args);
        }
        Map<String, JRaftServer.RaftGroupTuple> tupleMap = raftServer.getMultiRaftGroup();
        for (Map.Entry<String, JRaftServer.RaftGroupTuple> entry : tupleMap.entrySet()) {
            final String group = entry.getKey();
            final Node node = entry.getValue().getNode();
            RestResult<String> result = single(cliService, group, node, args);
            if (!result.ok()) {
                return result;
            }
        }
        return RestResultUtils.success();
    }
    
    private RestResult<String> single(CliService cliService, String groupId, Node node, Map<String, String> args) {
        try {
            if (node == null) {
                return RestResultUtils.failed("not this raft group : " + groupId);
            }
            final String command = args.get(JRaftConstants.COMMAND_NAME);
            JRaftOps ops = JRaftOps.sourceOf(command);
            if (Objects.isNull(ops)) {
                return RestResultUtils.failed("Not support command : " + command);
            }
            return ops.execute(cliService, groupId, node, args);
        } catch (Throwable ex) {
            return RestResultUtils.failed(ex.getMessage());
        }
    }
    
}
