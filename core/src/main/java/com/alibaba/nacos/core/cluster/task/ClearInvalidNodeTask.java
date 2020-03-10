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
import com.alibaba.nacos.core.cluster.Task;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ClearInvalidNodeTask extends Task {

    // 10 second

    private static final long EXPIRE_TIME = 10_000L;

    // 20 second

    private static final long REMOVAL_TIME = 20_000L;
    private static final int MAX_CLEAN_CNT = 3;
    private int cleanCnt = 0;

    @Override
    public void executeBody() {
        Map<String, Long> lastRefreshRecord = memberManager.getLastRefreshTimeRecord();
        Map<String, Member> nodeMap = memberManager.getServerListHealth();
        Set<Member> unHealthMembers = memberManager.getServerListUnHealth();

        long currentTime = System.currentTimeMillis();

        final String self = memberManager.self().address();

        lastRefreshRecord.forEach((address, lastRefresh) -> {

            if (Objects.equals(self, address)) {
                return;
            }

            if (lastRefresh + EXPIRE_TIME < currentTime) {
                Member member = nodeMap.get(address);
                if (member != null) {
                    unHealthMembers.add(member);
                }
            }
            if (lastRefresh + REMOVAL_TIME < currentTime) {
                Member old = nodeMap.remove(address);

                if (old != null) {
                    if (lastRefreshRecord.get(old.address()) + EXPIRE_TIME > currentTime) {
                        nodeMap.put(address, old);
                    }
                }
            }
        });

        cleanCnt++;

        if (cleanCnt > MAX_CLEAN_CNT) {
            if (!unHealthMembers.isEmpty()) {
                Loggers.CORE.warn("Node to leave : {}", unHealthMembers);

                memberManager.memberLeave(unHealthMembers);
                cleanCnt = 0;
            }
        }

        GlobalExecutor.scheduleCleanJob(this::executeBody, REMOVAL_TIME);

    }

}
