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

package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * Use simplified Raft protocol to maintain the consistency status of Nacos cluster.
 *
 * @author nkorange
 * @since 1.0.0
 * @deprecated will remove in 1.4.x
 */
@Deprecated
@DependsOn("ProtocolManager")
@Service
public class RaftConsistencyServiceImpl implements PersistentConsistencyService {
    
    private final RaftCore raftCore;
    
    private final RaftPeerSet peers;
    
    private final SwitchDomain switchDomain;
    
    private volatile boolean stopWork = false;
    
    private String errorMsg;
    
    public RaftConsistencyServiceImpl(ClusterVersionJudgement versionJudgement, RaftCore raftCore,
            SwitchDomain switchDomain) {
        this.raftCore = raftCore;
        this.peers = raftCore.getPeerSet();
        this.switchDomain = switchDomain;
        versionJudgement.registerObserver(isAllNewVersion -> {
            stopWork = isAllNewVersion;
            if (stopWork) {
                try {
                    this.raftCore.shutdown();
                } catch (NacosException e) {
                    throw new NacosRuntimeException(NacosException.SERVER_ERROR, e);
                }
            }
        }, 1000);
    }
    
    @PostConstruct
    protected void init() throws Exception {
        if (EnvUtil.getProperty(Constants.NACOS_NAMING_USE_NEW_RAFT_FIRST, Boolean.class, false)) {
            this.raftCore.shutdown();
        }
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        checkIsStopWork();
        try {
            raftCore.signalPublish(key, value);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft put failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft put failed, key:" + key + ", value:" + value,
                    e);
        }
    }
    
    @Override
    public void remove(String key) throws NacosException {
        checkIsStopWork();
        try {
            if (KeyBuilder.matchInstanceListKey(key) && !raftCore.isLeader()) {
                raftCore.onDelete(key, peers.getLeader());
            } else {
                raftCore.signalDelete(key);
            }
            raftCore.unListenAll(key);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft remove failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft remove failed, key:" + key, e);
        }
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        checkIsStopWork();
        return raftCore.getDatum(key);
    }
    
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        raftCore.listen(key, listener);
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        raftCore.unListen(key, listener);
    }
    
    @Override
    public boolean isAvailable() {
        return raftCore.isInitialized() || ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus());
    }
    
    @Override
    public Optional<String> getErrorMsg() {
        String errorMsg;
        if (!raftCore.isInitialized() && !ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus())) {
            errorMsg = "The old raft protocol node is not initialized";
        } else {
            errorMsg = null;
        }
        return Optional.ofNullable(errorMsg);
    }
    
    /**
     * Put a new datum from other server.
     *
     * @param datum  datum
     * @param source source server
     * @throws NacosException nacos exception
     */
    public void onPut(Datum datum, RaftPeer source) throws NacosException {
        try {
            raftCore.onPublish(datum, source);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft onPut failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Raft onPut failed, datum:" + datum + ", source: " + source, e);
        }
    }
    
    /**
     * Remove a new datum from other server.
     *
     * @param datum  datum
     * @param source source server
     * @throws NacosException nacos exception
     */
    public void onRemove(Datum datum, RaftPeer source) throws NacosException {
        try {
            raftCore.onDelete(datum.key, source);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft onRemove failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR,
                    "Raft onRemove failed, datum:" + datum + ", source: " + source, e);
        }
    }
    
    private void checkIsStopWork() {
        if (stopWork) {
            throw new IllegalStateException("old raft protocol already stop work");
        }
    }
}
