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

import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.GetResponse;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftLogOperation;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosStateMachine extends AbstractStateMachine {

	private Serializer serializer;

	public NacosStateMachine(Serializer serializer, JRaftServer server,
			LogProcessor4CP processor) {
		super(server, processor);
		this.serializer = serializer;
	}

	@Override
    public void onApply(Iterator iter) {
		int index = 0;
		int applied = 0;
		Log log = null;
		NacosClosure closure = null;
		try {
			while (iter.hasNext()) {
				Status status = Status.OK();
				try {
					if (iter.done() != null) {
						closure = (NacosClosure) iter.done();
						log = closure.getLog();
					}
					else {
						final ByteBuffer data = iter.getData();
						log = Log.parseFrom(data.array());
					}

					Loggers.RAFT.debug("receive log : {}", log);

					final String type = log.getExtendInfoOrDefault(
							JRaftConstants.JRAFT_EXTEND_INFO_KEY, JRaftLogOperation.READ_OPERATION);

					switch (type) {
					case JRaftLogOperation.READ_OPERATION:
						raftRead(closure, log);
						break;
					case JRaftLogOperation.MODIFY_OPERATION:
						LogFuture future = processor.onApply(log);
						futurePostProcessor(future, closure);
						break;
					default:
						// It's impossible to get to this process
						throw new UnsupportedOperationException(type);
					}
				}
				catch (Throwable e) {
					index++;
					status.setError(RaftError.UNKNOWN, e.getMessage());
					Optional.ofNullable(closure).ifPresent(closure1 -> closure1.setThrowable(e));
					throw e;
				} finally {
                    Optional.ofNullable(closure).ifPresent(closure1 -> closure1.run(status));
                }

				applied++;
				index++;
				iter.next();
			}

		}
		catch (Throwable t) {
			Loggers.RAFT.error("processor : {}, stateMachine meet critical error: {}.", processor, t);
			iter.setErrorAndRollback(index - applied, new Status(RaftError.ESTATEMACHINE,
					"StateMachine meet critical error: %s.", t.getMessage()));
		}
	}

	private void raftRead(NacosClosure closure, Log log) {
		final GetRequest request = GetRequest.newBuilder().setGroup(processor.group())
				.setData(log.getData()).build();
		try {
			GetResponse result = processor.getData(request);
			if (Objects.nonNull(closure)) {
				closure.setObject(result);
			}
		}
		catch (Throwable t) {
			Loggers.RAFT
					.error("There is an exception to the data acquisition : processor : {}, request : {}, error : {}",
							processor, request, t);
		}
	}

	private void futurePostProcessor(LogFuture future, NacosClosure closure) {
		if (Objects.nonNull(closure)) {
			closure.setObject(future);
		}
	}

}
