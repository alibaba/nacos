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

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An automated task that determines whether all nodes in the current cluster meet the requirements of a particular
 * version.
 *
 * <p>This will be removed in a future release, just to smooth the transition.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class ClusterVersionJudgement {
    
    private volatile boolean allMemberIsNewVersion = false;
    
    private final ServerMemberManager memberManager;
    
    private final List<ConsumerWithPriority> observers = new CopyOnWriteArrayList<>();
    
    public ClusterVersionJudgement(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        GlobalExecutor.submitClusterVersionJudge(this::runVersionListener, TimeUnit.SECONDS.toMillis(5));
    }
    
    /**
     * register member version watcher.
     *
     * @param observer Listens for the latest version of all current nodes
     * @param priority The higher the priority, the first to be notified
     */
    public void registerObserver(Consumer<Boolean> observer, int priority) {
        observers.add(new ConsumerWithPriority(observer, priority));
    }
    
    protected void runVersionListener() {
        // Single machine mode, do upgrade operation directly.
        if (EnvUtil.getStandaloneMode()) {
            notifyAllListener();
            return;
        }
        try {
            judge();
        } finally {
            GlobalExecutor.submitClusterVersionJudge(this::runVersionListener, TimeUnit.SECONDS.toMillis(5));
        }
    }
    
    protected void judge() {
        
        Collection<Member> members = memberManager.allMembers();
        final String oldVersion = "1.4.0";
        boolean allMemberIsNewVersion = true;
        for (Member member : members) {
            final String curV = (String) member.getExtendVal(MemberMetaDataConstants.VERSION);
            if (StringUtils.isBlank(curV) || VersionUtils.compareVersion(oldVersion, curV) > 0) {
                allMemberIsNewVersion = false;
            }
        }
        // can only trigger once
        if (allMemberIsNewVersion && !this.allMemberIsNewVersion) {
            notifyAllListener();
        }
    }
    
    private void notifyAllListener() {
        this.allMemberIsNewVersion = true;
        Collections.sort(observers);
        for (ConsumerWithPriority consumer : observers) {
            consumer.consumer.accept(true);
        }
    }
    
    public boolean allMemberIsNewVersion() {
        return allMemberIsNewVersion;
    }
    
    /**
     * Only used for upgrade to 2.0.0
     */
    public void reset() {
        allMemberIsNewVersion = false;
    }
    
    private static class ConsumerWithPriority implements Comparable<ConsumerWithPriority> {
        
        private final Consumer<Boolean> consumer;
        
        private final int priority;
        
        public ConsumerWithPriority(Consumer<Boolean> consumer, int priority) {
            this.consumer = consumer;
            this.priority = priority;
        }
        
        @Override
        public int compareTo(ConsumerWithPriority o) {
            return o.priority - this.priority;
        }
    }
}
