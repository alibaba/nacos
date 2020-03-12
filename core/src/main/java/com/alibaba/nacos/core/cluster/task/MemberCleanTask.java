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

package com.alibaba.nacos.core.cluster.task;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Expect to be out of action in 30 seconds
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberCleanTask extends Task {

    // 15 second

    private static final long EXPIRE_TIME = 15_000L;

    private static final int MAX_CLEAN_CNT = 2;
    private int cleanCnt = 0;

    private Set<Member> waitRemove = new HashSet<>();

    public MemberCleanTask(ServerMemberManager memberManager) {
        super(memberManager);
    }

    @Override
    public void executeBody() {
        Map<String, Member> memberMap = memberManager.getServerList();
        long currentTime = System.currentTimeMillis();

        memberMap.forEach((address, member) -> {
            if (member.getState() == NodeState.DOWN && cleanCnt == 2) {
                waitRemove.add(member);
                return;
            }

            final long lastRefresh = member.getLastRefreshTime();
            final long expireTime = lastRefresh + EXPIRE_TIME;

            boolean isHealth = filter.apply(expireTime - currentTime, member);
            if (isHealth) {
                member.setState(NodeState.UP);
            } else {
                if (member.getState() == NodeState.SUSPICIOUS && cleanCnt == 2) {
                    member.setState(NodeState.DOWN);
                    return;
                }
                member.setState(NodeState.SUSPICIOUS);
            }
        });
    }

    @Override
    protected void after() {
        cleanCnt++;
        if (cleanCnt > MAX_CLEAN_CNT) {
            if (!waitRemove.isEmpty()) {
                Loggers.CLUSTER.warn("clean job lead some member leave : {}", waitRemove);
                memberManager.memberLeave(new HashSet<>(waitRemove));
                waitRemove.clear();
            }
            cleanCnt = 0;
        }
        GlobalExecutor.scheduleCleanJob(this, 10_000L);
    }

    private final BiFunction<Long, Member, Boolean> filter = (leftTime, member) -> {
        if (memberManager.isSelf(member)) {
            return true;
        }
        return leftTime >= 0;
    };

}
