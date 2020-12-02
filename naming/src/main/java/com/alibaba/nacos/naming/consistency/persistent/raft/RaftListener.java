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
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Inject the raft information from the naming module into the outlier information of the node.
 *
 * @deprecated will remove in 1.4.x
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Deprecated
@Component
public class RaftListener implements SmartApplicationListener {
    
    private static final String GROUP = "naming";
    
    private final ServerMemberManager memberManager;
    
    private final ClusterVersionJudgement versionJudgement;
    
    private volatile boolean stopUpdate = false;
    
    public RaftListener(ServerMemberManager memberManager, ClusterVersionJudgement versionJudgement) {
        this.memberManager = memberManager;
        this.versionJudgement = versionJudgement;
        this.init();
    }
    
    private void init() {
        this.versionJudgement.registerObserver(isAllNewVersion -> {
            stopUpdate = isAllNewVersion;
            if (stopUpdate) {
                Loggers.RAFT.warn("start to move old raft protocol metadata");
                Member self = memberManager.getSelf();
                self.delExtendVal(GROUP);
                memberManager.update(self);
            }
        }, -2);
    }
    
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return BaseRaftEvent.class.isAssignableFrom(eventType);
    }
    
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof BaseRaftEvent && !stopUpdate) {
            BaseRaftEvent raftEvent = (BaseRaftEvent) event;
            RaftPeer local = raftEvent.getLocal();
            String json = JacksonUtils.toJson(local);
            Map map = JacksonUtils.toObj(json, HashMap.class);
            Member self = memberManager.getSelf();
            self.setExtendVal(GROUP, map);
            memberManager.update(self);
        }
    }
}
