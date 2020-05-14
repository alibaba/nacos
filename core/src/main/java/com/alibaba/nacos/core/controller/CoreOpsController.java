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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.utils.Commons;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/ops")
public class CoreOpsController {

	private final ProtocolManager protocolManager;

	public CoreOpsController(ProtocolManager protocolManager) {
		this.protocolManager = protocolManager;
	}

	// Temporarily overpassed the raft operations interface
	// {
	//		"groupId": "xxx",
	//		"transferLeader": "ip:{raft_port}"
	//		"doSnapshot": "ip:{raft_port}"
	//		"resetRaftCluster": "ip:{raft_port},ip:{raft_port},ip:{raft_port},ip:{raft_port}"
	//		"removePeer": "ip:{raft_port}"
	// }

	@PostMapping(value = "/raft")
	public RestResult<String> raftOps(@RequestBody Map<String, String> commands) {
		CPProtocol protocol = protocolManager.getCpProtocol();
		return protocol.execute(commands);
	}

}
