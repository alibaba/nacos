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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnEmbeddedStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.distributed.raft.exception.NoSuchRaftGroupException;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedDumpService extends DumpService {

	private final ProtocolManager protocolManager;

	final String[] failedMsgs = new String[] {
			"The conformance protocol is temporarily unavailable for reading"
	};

	/**
	 * Here you inject the dependent objects constructively, ensuring that some
	 * of the dependent functionality is initialized ahead of time
	 *
	 * @param persistService  {@link PersistService}
	 * @param memberManager   {@link ServerMemberManager}
	 * @param protocolManager {@link ProtocolManager}
	 */
	public EmbeddedDumpService(PersistService persistService,
			ServerMemberManager memberManager, ProtocolManager protocolManager) {
		super(persistService, memberManager);
		this.protocolManager = protocolManager;
	}

	@PostConstruct
	@Override
	protected void init() throws Throwable {
		CPProtocol protocol = protocolManager.getCpProtocol();

		LogUtil.dumpLog
				.info("With embedded distributed storage, you need to wait for "
						+ "the underlying master to complete before you can perform the dump operation.");

		AtomicReference<Throwable> errorReference = new AtomicReference<>(null);
		CountDownLatch waitDumpFinish = new CountDownLatch(1);

		// watch path => /nacos_config/leader/ has value ?
		Observer observer = new Observer() {

			@Override
			public void update(Observable o, Object arg) {
				GlobalExecutor.executeByCommon(() -> {
					// must make sure that there is a value here to perform the correct operation that follows
					if (Objects.isNull(arg)) {
						return;
					}
					// Remove your own listening to avoid task accumulation
					for ( ; ; ) {
						try {
							dumpOperate(processor, dumpAllProcessor,
									dumpAllBetaProcessor, dumpAllTagProcessor);
							waitDumpFinish.countDown();
							protocol.protocolMetaData()
									.unSubscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
											MetadataKey.LEADER_META_DATA,
											this);
							return;
						}
						catch (Throwable ex) {
							if (!shouldRetry(ex)) {
								waitDumpFinish.countDown();
								errorReference.set(ex);
								break;
							}
						}
						ThreadUtils.sleep(500L);
					}
				});
			}
		};

		protocol.protocolMetaData().subscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
				MetadataKey.LEADER_META_DATA,
				observer);

		// We must wait for the dump task to complete the callback operation before
		// continuing with the initialization
		ThreadUtils.latchAwait(waitDumpFinish);

		// If an exception occurs during the execution of the dump task, the exception
		// needs to be thrown, triggering the node to start the failed process
		final Throwable ex = errorReference.get();
		if (Objects.nonNull(ex)) {
			throw ex;
		}
	}

	private boolean shouldRetry(Throwable ex) {
		final String errMsg = ex.getMessage();
		for (final String failedMsg : failedMsgs) {
			if (StringUtils.containsIgnoreCase(errMsg, failedMsg)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean canExecute() {
		try {
			// if is derby + raft mode, only leader can execute
			CPProtocol protocol = protocolManager.getCpProtocol();
			return protocol.isLeader(Constants.CONFIG_MODEL_RAFT_GROUP);
		}
		catch (NoSuchRaftGroupException e) {
			return true;
		}
		catch (Throwable e) {
			// It's impossible to get to this point
			throw new RuntimeException(e);
		}
	}
}
