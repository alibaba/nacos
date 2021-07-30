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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data aggregation service.
 *
 * <p>Full aggregation at startup and single aggregation triggered by data modification.
 *
 * @author jiuRen
 */
@Service
public class MergeDatumService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeDatumService.class);
    
    final TaskManager mergeTasks;
    
    private PersistService persistService;
    
    static final int INIT_THREAD_COUNT = 40;
    
    static final AtomicInteger FINISHED = new AtomicInteger();
    
    static int total = 0;
    
    @Autowired
    public MergeDatumService(PersistService persistService) {
        this.persistService = persistService;
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
     * Called after data changes to add aggregation tasks.
     */
    public void addMergeTask(String dataId, String groupId, String tenant, String tag, String clientIp) {
        if (!canExecute()) {
            return;
        }
        MergeDataTask task = new MergeDataTask(dataId, groupId, tenant, tag, clientIp);
        mergeTasks.addTask(task.getId(), task);
    }
    
    /**
     * Called after data changes to add aggregation tasks.
     */
    public void addMergeTask(String dataId, String groupId, String tenant, String clientIp) {
        if (!canExecute()) {
            return;
        }
        MergeDataTask task = new MergeDataTask(dataId, groupId, tenant, clientIp);
        mergeTasks.addTask(task.getId(), task);
    }
    
    /**
     * Merge all.
     */
    public void mergeAll() {
        if (!canExecute()) {
            return;
        }
        for (ConfigInfoChanged item : persistService.findAllAggrGroup()) {
            addMergeTask(item.getDataId(), item.getGroup(), item.getTenant(), InetUtils.getSelfIP());
        }
    }
    
    private boolean canExecute() {
        if (!PropertyUtil.isEmbeddedStorage()) {
            return true;
        }
        if (EnvUtil.getStandaloneMode()) {
            return true;
        }
        ProtocolManager protocolManager = ApplicationUtils.getBean(ProtocolManager.class);
        return protocolManager.getCpProtocol().isLeader(Constants.CONFIG_MODEL_RAFT_GROUP);
    }
    
    class MergeAllDataWorker extends Thread {
        
        static final int PAGE_SIZE = 10000;
        
        private List<ConfigInfoChanged> configInfoList;
        
        public MergeAllDataWorker(List<ConfigInfoChanged> configInfoList) {
            super("MergeAllDataWorker");
            this.configInfoList = configInfoList;
        }
        
        @Override
        public void run() {
            for (ConfigInfoChanged configInfo : configInfoList) {
                String dataId = configInfo.getDataId();
                String group = configInfo.getGroup();
                String tenant = configInfo.getTenant();
                try {
                    List<ConfigInfoAggr> datumList = new ArrayList<ConfigInfoAggr>();
                    int rowCount = persistService.aggrConfigInfoCount(dataId, group, tenant);
                    int pageCount = (int) Math.ceil(rowCount * 1.0 / PAGE_SIZE);
                    for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                        Page<ConfigInfoAggr> page = persistService
                                .findConfigInfoAggrByPage(dataId, group, tenant, pageNo, PAGE_SIZE);
                        if (page != null) {
                            datumList.addAll(page.getPageItems());
                            LOGGER.info("[merge-query] {}, {}, size/total={}/{}", dataId, group, datumList.size(),
                                    rowCount);
                        }
                    }
                    
                    final Timestamp time = TimeUtils.getCurrentTime();
                    
                    if (datumList.size() > 0) {
                        // merge
                        ConfigInfo cf = MergeTaskProcessor.merge(dataId, group, tenant, datumList);
                        persistService.insertOrUpdate(null, null, cf, time, null, false);
                        LOGGER.info("[merge-ok] {}, {}, size={}, length={}, md5={}, content={}", dataId, group,
                                datumList.size(), cf.getContent().length(), cf.getMd5(),
                                ContentUtils.truncateContent(cf.getContent()));
                    } else {
                        // remove
                        persistService.removeConfigInfo(dataId, group, tenant, InetUtils.getSelfIP(), null);
                        LOGGER.warn("[merge-delete] delete config info because no datum. dataId=" + dataId + ", groupId="
                                + group);
                    }
                    
                } catch (Exception e) {
                    LOGGER.info("[merge-error] " + dataId + ", " + group + ", " + e.toString(), e);
                }
                FINISHED.incrementAndGet();
                if (FINISHED.get() % 100 == 0) {
                    LOGGER.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
                }
            }
            LOGGER.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
        }
    }
}
