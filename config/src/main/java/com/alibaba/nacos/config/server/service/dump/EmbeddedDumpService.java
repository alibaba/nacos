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
import com.alibaba.nacos.config.server.service.sql.EmbeddedStorageContextUtils;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embedded dump service.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionOnEmbeddedStorage.class)
@Component
public class EmbeddedDumpService extends DumpService {
    
    private final ProtocolManager protocolManager;
    
    /**
     * If it's just a normal reading failure, it can be resolved by retrying.
     */
    final String[] retryMessages = new String[] {"The conformance protocol is temporarily unavailable for reading"};
    
    /**
     * If the read failed due to an internal problem in the Raft state machine, it cannot be remedied by retrying.
     */
    final String[] errorMessages = new String[] {"FSMCaller is overload.", "STATE_ERROR"};
    
    /**
     * Here you inject the dependent objects constructively, ensuring that some of the dependent functionality is
     * initialized ahead of time.
     *
     * @param persistService  {@link PersistService}
     * @param memberManager   {@link ServerMemberManager}
     * @param protocolManager {@link ProtocolManager}
     */
    public EmbeddedDumpService(PersistService persistService, ServerMemberManager memberManager,
            ProtocolManager protocolManager) {
        super(persistService, memberManager);
        this.protocolManager = protocolManager;
    }
    
    @PostConstruct
    @Override
    protected void init() throws Throwable {
        if (EnvUtil.getStandaloneMode()) {
            dumpOperate(processor, dumpAllProcessor, dumpAllBetaProcessor, dumpAllTagProcessor);
            return;
        }
        
        CPProtocol protocol = protocolManager.getCpProtocol();
        AtomicReference<Throwable> errorReference = new AtomicReference<>(null);
        CountDownLatch waitDumpFinish = new CountDownLatch(1);
        
        // watch path => /nacos_config/leader/ has value ?
        Observer observer = new Observer() {
            
            @Override
            public void update(Observable o) {
                if (!(o instanceof ProtocolMetaData.ValueItem)) {
                    return;
                }
                final Object arg = ((ProtocolMetaData.ValueItem) o).getData();
                GlobalExecutor.executeByCommon(() -> {
                    // must make sure that there is a value here to perform the correct operation that follows
                    if (Objects.isNull(arg)) {
                        return;
                    }
                    // Identify without a timeout mechanism
                    EmbeddedStorageContextUtils.putExtendInfo(Constants.EXTEND_NEED_READ_UNTIL_HAVE_DATA, "true");
                    // Remove your own listening to avoid task accumulation
                    boolean canEnd = false;
                    for (; ; ) {
                        try {
                            dumpOperate(processor, dumpAllProcessor, dumpAllBetaProcessor, dumpAllTagProcessor);
                            protocol.protocolMetaData()
                                    .unSubscribe(Constants.CONFIG_MODEL_RAFT_GROUP, MetadataKey.LEADER_META_DATA, this);
                            canEnd = true;
                        } catch (Throwable ex) {
                            if (!shouldRetry(ex)) {
                                errorReference.set(ex);
                                canEnd = true;
                            }
                        }
                        if (canEnd) {
                            ThreadUtils.countDown(waitDumpFinish);
                            break;
                        }
                        ThreadUtils.sleep(500L);
                    }
                    EmbeddedStorageContextUtils.cleanAllContext();
                });
            }
        };
        
        protocol.protocolMetaData()
                .subscribe(Constants.CONFIG_MODEL_RAFT_GROUP, MetadataKey.LEADER_META_DATA, observer);
        
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
        
        for (String failedMsg : errorMessages) {
            if (StringUtils.containsIgnoreCase(errMsg, failedMsg)) {
                return false;
            }
        }
        for (final String retryMsg : retryMessages) {
            if (StringUtils.containsIgnoreCase(errMsg, retryMsg)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected boolean canExecute() {
        if (EnvUtil.getStandaloneMode()) {
            return true;
        }
        // if is derby + raft mode, only leader can execute
        CPProtocol protocol = protocolManager.getCpProtocol();
        return protocol.isLeader(Constants.CONFIG_MODEL_RAFT_GROUP);
    }
}
