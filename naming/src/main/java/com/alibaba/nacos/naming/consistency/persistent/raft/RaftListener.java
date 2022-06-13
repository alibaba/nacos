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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.constants.Constants;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Inject the raft information from the naming module into the outlier information of the node.
 *
 * @deprecated will remove in 1.4.x
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Deprecated
@Component
public class RaftListener implements SmartApplicationListener {
    
    private final ServerMemberManager memberManager;
    
    private final ClusterVersionJudgement versionJudgement;
    
    private volatile boolean stopUpdate = false;
    
    /**
     * Avoid multithreading mode. Old Raft information data cannot be properly removed.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    public RaftListener(ServerMemberManager memberManager, ClusterVersionJudgement versionJudgement) {
        this.memberManager = memberManager;
        this.versionJudgement = versionJudgement;
        this.init();
    }
    
    private void init() {
        this.versionJudgement.registerObserver(isAllNewVersion -> {
            final Lock lock = this.writeLock;
            lock.lock();
            try {
                stopUpdate = isAllNewVersion;
                if (stopUpdate) {
                    removeOldRaftMetadata();
                }
            } finally {
                lock.unlock();
            }
        }, -2);
    }
    
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return BaseRaftEvent.class.isAssignableFrom(eventType);
    }
    
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        final Lock lock = readLock;
        lock.lock();
        try {
            if (event instanceof BaseRaftEvent && !stopUpdate) {
                BaseRaftEvent raftEvent = (BaseRaftEvent) event;
                RaftPeer local = raftEvent.getLocal();
                String json = JacksonUtils.toJson(local);
                Map map = JacksonUtils.toObj(json, HashMap.class);
                Member self = memberManager.getSelf();
                self.setExtendVal(Constants.OLD_NAMING_RAFT_GROUP, map);
                memberManager.update(self);
            }
            if (stopUpdate) {
                removeOldRaftMetadata();
            }
        } finally {
            lock.unlock();
        }
    }
    
    void removeOldRaftMetadata() {
        Loggers.RAFT.warn("start to move old raft protocol metadata");
        Member self = memberManager.getSelf();
        self.delExtendVal(Constants.OLD_NAMING_RAFT_GROUP);
        memberManager.update(self);
    }
}
