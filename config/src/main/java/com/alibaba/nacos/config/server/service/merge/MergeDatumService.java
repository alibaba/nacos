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
package com.alibaba.nacos.config.server.service.merge;

import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.utils.SpringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.core.utils.SystemUtils.LOCAL_IP;

/**
 * 数据聚合服务。
 * <p>
 * 启动时做全量聚合 + 修改数据触发的单条聚合
 *
 * @author jiuRen
 */
@Service
public class MergeDatumService {

    static final int INIT_THREAD_COUNT = 40;
    static final AtomicInteger FINISHED = new AtomicInteger();
    static int total = 0;
    final TaskManager mergeTasks;
    private MemberManager memberManager;
    private PersistService persistService;

    @Autowired
    public MergeDatumService(PersistService persistService) {
        this.persistService = persistService;
        this.memberManager = SpringUtils.getBean(MemberManager.class);
        mergeTasks = new TaskManager("com.alibaba.nacos.MergeDatum");
        mergeTasks.setDefaultTaskProcessor(new MergeTaskProcessor(persistService, this));
    }

    static List<List<ConfigInfoChanged>> splitList(List<ConfigInfoChanged> list, int count) {
        List<List<ConfigInfoChanged>> result = new ArrayList<List<ConfigInfoChanged>>(count);
        for (int i = 0; i < count; i++) {
            result.add(new ArrayList<ConfigInfoChanged>());
        }
        for (int i = 0; i < list.size(); i++) {
            ConfigInfoChanged config = list.get(i);
            result.get(i % count).add(config);
        }
        return result;
    }

    /**
     * 数据变更后调用，添加聚合任务
     */
    public void addMergeTask(String dataId, String groupId, String tenant, String tag, String clientIp) {
        if (!memberManager.isFirstIp()) {
            LogUtil.mergeLog.debug("The current node is not the first node in the cluster and does not process any tasks");
            return;
        }
        MergeDataTask task = new MergeDataTask(dataId, groupId, tenant, tag, clientIp);
        mergeTasks.addTask(task.getId(), task);
    }

    /**
     * 数据变更后调用，添加聚合任务
     */
    public void addMergeTask(String dataId, String groupId, String tenant, String clientIp) {
        if (!memberManager.isFirstIp()) {
            LogUtil.mergeLog.debug("The current node is not the first node in the cluster and does not process any tasks");
            return;
        }
        MergeDataTask task = new MergeDataTask(dataId, groupId, tenant, clientIp);
        mergeTasks.addTask(task.getId(), task);
    }

    public void mergeAll() {
        if (!memberManager.isFirstIp()) {
            LogUtil.mergeLog.debug("The current node is not the first node in the cluster and does not process any tasks");
            return;
        }
        for (ConfigInfoChanged item : persistService.findAllAggrGroup()) {
            addMergeTask(item.getDataId(), item.getGroup(), item.getTenant(), LOCAL_IP);
        }
    }

    // =====================

}
