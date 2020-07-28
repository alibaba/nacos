/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * An automated task that determines whether all nodes in the current cluster meet the requirements of a particular
 * version.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class ClusterVersionJudgement {
    
    private volatile boolean allMemberIsNewVersion = false;
    
    private final ServerMemberManager memberManager;
    
    private final Collection<Consumer<Boolean>> observers = new CopyOnWriteArrayList<>();
    
    public ClusterVersionJudgement(ServerMemberManager memberManager) {
        this.memberManager = memberManager;
        GlobalExecutor.submitClusterVersionJudge(this::judge, TimeUnit.SECONDS.toMillis(5));
    }
    
    public void registerObserver(Consumer<Boolean> observer) {
        observers.add(observer);
    }
    
    private void judge() {
        Collection<Member> members = memberManager.allMembers();
        final String oldVersion = "1.3.1";
        for (Member member : members) {
            final String curV = (String) member.getExtendVal(MemberMetaDataConstants.VERSION);
            if (StringUtils.isNotBlank(curV) && VersionUtils.compareVersion(oldVersion, curV) < 0) {
                allMemberIsNewVersion = true;
            }
        }
        if (allMemberIsNewVersion) {
            for (Consumer<Boolean> consumer : observers) {
                consumer.accept(allMemberIsNewVersion);
            }
            observers.clear();
            return;
        }
        GlobalExecutor.submitClusterVersionJudge(this::judge, TimeUnit.SECONDS.toMillis(5));
    }
    
    public boolean isAllMemberIsNewVersion() {
        return allMemberIsNewVersion;
    }
}
