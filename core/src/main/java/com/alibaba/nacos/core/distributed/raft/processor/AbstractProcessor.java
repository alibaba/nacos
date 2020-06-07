/*
 *
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
 *
 */

package com.alibaba.nacos.core.distributed.raft.processor;

import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.distributed.raft.JRaftServer;
import com.alibaba.nacos.core.distributed.raft.utils.FailoverClosure;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.rpc.RpcContext;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class AbstractProcessor {

	private final Serializer serializer;

	public AbstractProcessor(Serializer serializer) {
		this.serializer = serializer;
	}

	protected void handleRequest(final JRaftServer server, final String group, final RpcContext rpcCtx, Message log) {
		try {
			final JRaftServer.RaftGroupTuple tuple = server.findTupleByGroup(group);
			if (Objects.isNull(tuple)) {
				rpcCtx.sendResponse(Response.newBuilder()
						.setSuccess(false)
						.setErrMsg("Could not find the corresponding Raft Group : " + group)
						.build());
				return;
			}
			if (tuple.getNode().isLeader()) {
				execute(server, rpcCtx, log, tuple);
			}
			else {
				rpcCtx.sendResponse(Response.newBuilder()
						.setSuccess(false)
						.setErrMsg("Could not find leader : " + group)
						.build());
			}
		} catch (Throwable e) {
			Loggers.RAFT.error("handleRequest has error : {}", e);
			rpcCtx.sendResponse(Response.newBuilder()
					.setSuccess(false)
					.setErrMsg(e.toString())
					.build());
		}
	}

	protected void execute(JRaftServer server, final RpcContext asyncCtx, final Message log, final JRaftServer.RaftGroupTuple tuple) {
		FailoverClosure closure = new FailoverClosure() {

			Response data;
			Throwable ex;

			@Override
			public void setResponse(Response data) {
				this.data = data;
			}

			@Override
			public void setThrowable(Throwable throwable) {
				this.ex = throwable;
			}

			@Override
			public void run(Status status) {
				if (Objects.nonNull(ex)) {
					Loggers.RAFT.error("execute has error : {}", ex);
					asyncCtx.sendResponse(Response.newBuilder().setErrMsg(ex.toString())
							.setSuccess(false).build());
				} else {
					asyncCtx.sendResponse(data);
				}
			}
		};

		server.applyOperation(tuple.getNode(), log, closure);
	}

}
