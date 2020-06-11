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

import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.alibaba.nacos.consistency.snapshot.LocalFileMeta;
import com.alibaba.nacos.consistency.snapshot.Reader;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.consistency.snapshot.Writer;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftUtils;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RouteTable;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.entity.LeaderChangeContext;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.google.protobuf.Message;
import org.apache.commons.lang3.BooleanUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
class NacosStateMachine extends StateMachineAdapter {

	protected final JRaftServer server;
	protected final LogProcessor processor;
	private final AtomicBoolean isLeader = new AtomicBoolean(false);
	private final String groupId;
	private Collection<JSnapshotOperation> operations;
	private Node node;
	private volatile long term = -1;
	private volatile String leaderIp = "unknown";

	NacosStateMachine(JRaftServer server, LogProcessor4CP processor) {
		this.server = server;
		this.processor = processor;
		this.groupId = processor.group();
		adapterToJRaftSnapshot(processor.loadSnapshotOperate());
	}

	@Override
	public void onApply(Iterator iter) {
		int index = 0;
		int applied = 0;
		Message message = null;
		NacosClosure closure = null;
		try {
			while (iter.hasNext()) {
				Status status = Status.OK();
				try {
					if (iter.done() != null) {
						closure = (NacosClosure) iter.done();
						message = closure.getMessage();
					}
					else {
						final ByteBuffer data = iter.getData();
						message = parse(data.array());
					}

					LoggerUtils.printIfDebugEnabled(Loggers.RAFT, "receive log : {}", message);

					if (message instanceof Log) {
						Response response = processor.onApply((Log) message);
						postProcessor(response, closure);
					}

					if (message instanceof GetRequest) {
						Response response = processor.onRequest((GetRequest) message);
						postProcessor(response, closure);
					}
				}
				catch (Throwable e) {
					index++;
					status.setError(RaftError.UNKNOWN, e.toString());
					Optional.ofNullable(closure)
							.ifPresent(closure1 -> closure1.setThrowable(e));
					throw e;
				}
				finally {
					Optional.ofNullable(closure)
							.ifPresent(closure1 -> closure1.run(status));
				}

				applied++;
				index++;
				iter.next();
			}
		}
		catch (Throwable t) {
			Loggers.RAFT.error("processor : {}, stateMachine meet critical error: {}.",
					processor, t);
			iter.setErrorAndRollback(index - applied, new Status(RaftError.ESTATEMACHINE,
					"StateMachine meet critical error: %s.", ExceptionUtil.getStackTrace(t)));
		}
	}

	private Message parse(byte[] bytes) {
		Message result = null;
		try {
			result = Log.parseFrom(bytes);
			return result;
		} catch (Throwable ignore) { }

		try {
			result = GetRequest.parseFrom(bytes);
			return result;
		} catch (Throwable ignore) {}

		throw new ConsistencyException("The current array cannot be serialized to the corresponding object");
	}

	public void setNode(Node node) {
		this.node = node;
	}

	@Override
	public void onSnapshotSave(SnapshotWriter writer, Closure done) {
		for (JSnapshotOperation operation : operations) {
			try {
				operation.onSnapshotSave(writer, done);
			}
			catch (Throwable t) {
				Loggers.RAFT
						.error("There was an error saving the snapshot , error : {}, operation : {}",
								t, operation.info());
				throw t;
			}
		}
	}

	@Override
	public boolean onSnapshotLoad(SnapshotReader reader) {
		for (JSnapshotOperation operation : operations) {
			try {
				if (!operation.onSnapshotLoad(reader)) {
					Loggers.RAFT.error("Snapshot load failed on : {}", operation.info());
					return false;
				}
			}
			catch (Throwable t) {
				Loggers.RAFT.error("Snapshot load failed on : {}, has error : {}",
						operation.info(), t);
				return false;
			}
		}
		return true;
	}

	@Override
	public void onLeaderStart(final long term) {
		super.onLeaderStart(term);
		this.term = term;
		this.isLeader.set(true);
		this.leaderIp = node.getNodeId().getPeerId().getEndpoint().toString();
		NotifyCenter.publishEvent(
				RaftEvent.builder().groupId(groupId).leader(leaderIp).term(term)
						.raftClusterInfo(allPeers()).build());
	}

	@Override
	public void onLeaderStop(final Status status) {
		super.onLeaderStop(status);
		this.isLeader.set(false);
	}

	@Override
	public void onStartFollowing(LeaderChangeContext ctx) {
		this.term = ctx.getTerm();
		this.leaderIp = ctx.getLeaderId().getEndpoint().toString();
		NotifyCenter.publishEvent(
				RaftEvent.builder().groupId(groupId).leader(leaderIp).term(ctx.getTerm())
						.raftClusterInfo(allPeers()).build());
	}

	@Override
	public void onConfigurationCommitted(Configuration conf) {
		NotifyCenter.publishEvent(
				RaftEvent.builder().groupId(groupId)
						.raftClusterInfo(JRaftUtils.toStrings(conf.getPeers())).build());
	}

	@Override
	public void onError(RaftException e) {
		super.onError(e);
		processor.onError(e);
	}

	public boolean isLeader() {
		return isLeader.get();
	}

	private List<String> allPeers() {
		if (node == null) {
			return Collections.emptyList();
		}

		if (node.isLeader()) {
			return JRaftUtils.toStrings(node.listPeers());
		}

		return JRaftUtils.toStrings(
				RouteTable.getInstance().getConfiguration(node.getGroupId()).getPeers());
	}

	private void postProcessor(Response data, NacosClosure closure) {
		if (Objects.nonNull(closure)) {
			closure.setResponse(data);
		}
	}

	public long getTerm() {
		return term;
	}

	private void adapterToJRaftSnapshot(Collection<SnapshotOperation> userOperates) {
		List<JSnapshotOperation> tmp = new ArrayList<>();

		for (SnapshotOperation item : userOperates) {

			if (item == null) {
				Loggers.RAFT.error("Existing SnapshotOperation for null");
				continue;
			}

			tmp.add(new JSnapshotOperation() {

				@Override
				public void onSnapshotSave(SnapshotWriter writer, Closure done) {
					final Writer wCtx = new Writer(writer.getPath());

					// Do a layer of proxy operation to shield different Raft
					// components from implementing snapshots

					final BiConsumer<Boolean, Throwable> callFinally = (result, t) -> {
						boolean[] results = new boolean[wCtx.listFiles().size()];
						int[] index = new int[] { 0 };
						wCtx.listFiles().forEach((file, meta) -> {
							try {
								results[index[0]++] = writer
										.addFile(file, buildMetadata(meta));
							}
							catch (Exception e) {
								throw new ConsistencyException(e);
							}
						});
						final Status status = result && BooleanUtils.and(results) ?
								Status.OK() :
								new Status(RaftError.EIO,
										"Fail to compress snapshot at %s, error is %s",
										writer.getPath(),
										t == null ? "" : t.getMessage());
						done.run(status);
					};
					item.onSnapshotSave(wCtx, callFinally);
				}

				@Override
				public boolean onSnapshotLoad(SnapshotReader reader) {
					final Map<String, LocalFileMeta> metaMap = new HashMap<>(
							reader.listFiles().size());
					for (String fileName : reader.listFiles()) {
						final LocalFileMetaOutter.LocalFileMeta meta = (LocalFileMetaOutter.LocalFileMeta) reader
								.getFileMeta(fileName);

						byte[] bytes = meta.getUserMeta().toByteArray();

						final LocalFileMeta fileMeta;
						if (bytes == null || bytes.length == 0) {
							fileMeta = new LocalFileMeta();
						}
						else {
							fileMeta = JacksonUtils.toObj(bytes, LocalFileMeta.class);
						}

						metaMap.put(fileName, fileMeta);
					}
					final Reader rCtx = new Reader(reader.getPath(), metaMap);
					return item.onSnapshotLoad(rCtx);
				}

				@Override
				public String info() {
					return item.toString();
				}
			});
		}

		this.operations = Collections.unmodifiableList(tmp);
	}

}
