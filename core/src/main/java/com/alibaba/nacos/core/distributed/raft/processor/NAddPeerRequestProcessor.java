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
package com.alibaba.nacos.core.distributed.raft.processor;

import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.RpcRequestClosure;
import com.alipay.sofa.jraft.rpc.impl.cli.AddPeerRequestProcessor;
import com.google.protobuf.Message;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class NAddPeerRequestProcessor extends AddPeerRequestProcessor {

	public NAddPeerRequestProcessor(Executor executor) {
		super(executor);
	}

	@Override
	protected Message processRequest0(CliRequestContext ctx, CliRequests.AddPeerRequest request, RpcRequestClosure done) {
		String addingPeerIdStr = request.getPeerId();
		PeerId addingPeer = PeerId.parsePeer(addingPeerIdStr);
		String groupId = request.getGroupId();
		Configuration conf = RouteTable.getInstance().getConfiguration(groupId);
		if (conf.contains(addingPeer)) {
			CliRequests.AddPeerResponse.Builder rb = CliRequests.AddPeerResponse.newBuilder();
			done.sendResponse(rb.build());
		} else {
			return super.processRequest0(ctx, request, done);
		}
		return null;
	}

}
