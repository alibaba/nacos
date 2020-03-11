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
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ClearInvalidNodeTask extends Task {

    // 10 second

    private static final long EXPIRE_TIME = 15_000L;

    // 20 second

    private static final long REMOVAL_TIME = 30_000L;
    private static final int MAX_CLEAN_CNT = 3;
    private int cleanCnt = 0;

    @Override
    public void executeBody() {
        Map<String, Long> lastRefreshRecord = memberManager.getLastRefreshTimeRecord();
        Map<String, Member> memberMap = memberManager.getServerListHealth();
        Set<Member> unHealthMembers = memberManager.getServerListUnHealth();

        long currentTime = System.currentTimeMillis();

        final String self = memberManager.self().address();

        lastRefreshRecord.forEach((address, lastRefresh) -> {

            Member member = memberMap.get(address);

            if (Objects.equals(self, address)) {
                member.setState(NodeState.UP);
                unHealthMembers.remove(member);
                return;
            }

            if (member != null) {
                if (lastRefresh + EXPIRE_TIME >= currentTime) {
                    member.setState(NodeState.UP);
                    unHealthMembers.remove(member);
                    return;
                }

                if (lastRefresh + EXPIRE_TIME < currentTime) {
                    member.setState(NodeState.SUSPICIOUS);
                    unHealthMembers.add(member);
                    return;
                }
                if (lastRefresh + REMOVAL_TIME < currentTime) {
                    Member old = memberMap.remove(address);
                    if (old != null) {
                        if (lastRefreshRecord.get(old.address()) + EXPIRE_TIME > currentTime) {
                            old.setState(NodeState.UP);
                            memberMap.put(address, old);
                            unHealthMembers.remove(old);
                        }
                    }
                } else {
                    member.setState(NodeState.UP);
                    unHealthMembers.remove(member);
                }
            }

        });

        cleanCnt++;

        if (cleanCnt > MAX_CLEAN_CNT) {
            if (!unHealthMembers.isEmpty()) {
                Loggers.CORE.warn("clean job lead some member leave : {}", unHealthMembers);
                memberManager.memberLeave(new HashSet<>(unHealthMembers));
            }
            cleanCnt = 0;
        }

        GlobalExecutor.scheduleCleanJob(this, 10_000L);

    }

}
