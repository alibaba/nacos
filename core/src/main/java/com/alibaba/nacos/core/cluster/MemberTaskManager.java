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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Cluster node management task center
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MemberTaskManager {

    private final ScheduledExecutorService executorService;

    private final ServerMemberManager nodeManager;

    public MemberTaskManager(final ServerMemberManager nodeManager) {
        this.nodeManager = nodeManager;
        this.executorService = ExecutorFactory.newScheduledExecutorService(
                ServerMemberManager.class.getCanonicalName(),
                2,
                new NameThreadFactory("com.alibaba.nacos.core.cluster"));
    }

    /**
     * Schedule according to the task type list returned by the task
     *
     * @param task {@link Task}
     */
    public void execute(final Task task) {
        if (task.isInExecute()) {
            return;
        }
        task.setNodeManager(nodeManager);
        task.init();
        Task.TaskType[] types = task.types();
        for (Task.TaskType taskType : types) {
            if (taskType == Task.TaskType.SCHEDULE_TASK) {
                Task.TaskInfo taskInfo = task.scheduleInfo();
                executorService.scheduleAtFixedRate(task,
                        taskInfo.getDelay(),
                        taskInfo.getPeriod(),
                        taskInfo.getUnit());
                continue;
            }
            if (taskType == Task.TaskType.DELAY_TASK) {
                Task.TaskInfo taskInfo = task.scheduleInfo();
                executorService.schedule(task, taskInfo.getDelay(), taskInfo.getUnit());
                continue;
            }
            if (taskType == Task.TaskType.IMMEDIATELY_TASK) {
                executorService.execute(task);
                continue;
            }
            task.run();
        }
    }

}
