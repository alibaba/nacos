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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ClearInvalidNodeTask extends Task {

    // 1 minutes

    private static final long EXPIRE_TIME = 60_000L;

    // 2 minute

    private static final long REMOVAL_TIME = EXPIRE_TIME << 1;

    private int cleanCnt = 0;

    private static final int MAX_CLEAN_CNT = 15;

    @Override
    protected void executeBody() {
        Map<String, Long> lastRefreshRecord = nodeManager.getLastRefreshTimeRecord();
        Map<String, Member> nodeMap = nodeManager.getServerListHealth();
        Set<Member> unHealthMembers = nodeManager.getServerListUnHealth();

        long currentTime = System.currentTimeMillis();

        lastRefreshRecord.forEach((address, lastRefresh) -> {
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

        cleanCnt ++;

        if (cleanCnt > MAX_CLEAN_CNT) {
            nodeManager.memberLeave(unHealthMembers);
            unHealthMembers.clear();
            cleanCnt = 0;
        }

    }

    @Override
    public TaskType[] types() {
        return new TaskType[]{TaskType.SCHEDULE_TASK};
    }

    @Override
    public TaskInfo scheduleInfo() {
        return new TaskInfo(REMOVAL_TIME, EXPIRE_TIME, TimeUnit.MILLISECONDS);
    }
}
