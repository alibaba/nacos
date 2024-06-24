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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    
    static final int INIT_THREAD_COUNT = 40;
    
    static final AtomicInteger FINISHED = new AtomicInteger();
    
    static int total = 0;
    
    private ConfigInfoPersistService configInfoPersistService;
    
    private ConfigInfoAggrPersistService configInfoAggrPersistService;
    
    @Autowired
    public MergeDatumService(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoAggrPersistService configInfoAggrPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoAggrPersistService = configInfoAggrPersistService;
        mergeTasks = new TaskManager("com.alibaba.nacos.MergeDatum");
        mergeTasks.setDefaultTaskProcessor(
                new MergeTaskProcessor(configInfoPersistService, configInfoAggrPersistService,
                        configInfoTagPersistService, this));
    }
    
    /**
     * splitList.
     *
     * @param list  list to split.
     * @param count count expect to be split.
     * @return
     */
    public List<List<ConfigInfoChanged>> splitList(List<ConfigInfoChanged> list, int count) {
        List<List<ConfigInfoChanged>> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new ArrayList<>());
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
    public void addMergeTask(String dataId, String groupId, String tenant, String clientIp) {
        if (!canExecute()) {
            return;
        }
        MergeDataTask task = new MergeDataTask(dataId, groupId, tenant, clientIp);
        mergeTasks.addTask(task.getId(), task);
    }
    
    private boolean canExecute() {
        if (!DatasourceConfiguration.isEmbeddedStorage()) {
            return true;
        }
        if (EnvUtil.getStandaloneMode()) {
            return true;
        }
        ProtocolManager protocolManager = ApplicationUtils.getBean(ProtocolManager.class);
        return protocolManager.getCpProtocol().isLeader(PersistenceConstant.CONFIG_MODEL_RAFT_GROUP);
    }
    
    void executeMergeConfigTask(List<ConfigInfoChanged> configInfoList, int pageSize) {
        for (ConfigInfoChanged configInfo : configInfoList) {
            String dataId = configInfo.getDataId();
            String group = configInfo.getGroup();
            String tenant = configInfo.getTenant();
            try {
                List<ConfigInfoAggr> datumList = new ArrayList<>();
                int rowCount = configInfoAggrPersistService.aggrConfigInfoCount(dataId, group, tenant);
                int pageCount = (int) Math.ceil(rowCount * 1.0 / pageSize);
                for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                    Page<ConfigInfoAggr> page = configInfoAggrPersistService.findConfigInfoAggrByPage(dataId, group,
                            tenant, pageNo, pageSize);
                    if (page != null) {
                        datumList.addAll(page.getPageItems());
                        LOGGER.info("[merge-query] {}, {}, size/total={}/{}", dataId, group, datumList.size(),
                                rowCount);
                    }
                }
                
                // merge
                if (datumList.size() > 0) {
                    ConfigInfo cf = MergeTaskProcessor.merge(dataId, group, tenant, datumList);
                    String aggrContent = cf.getContent();
                    String localContentMD5 = ConfigCacheService.getContentMd5(GroupKey.getKey(dataId, group));
                    String aggrConetentMD5 = MD5Utils.md5Hex(aggrContent, Constants.ENCODE);
                    
                    if (!StringUtils.equals(localContentMD5, aggrConetentMD5)) {
                        configInfoPersistService.insertOrUpdate(null, null, cf, null);
                        LOGGER.info("[merge-ok] {}, {}, size={}, length={}, md5={}, content={}", dataId, group,
                                datumList.size(), cf.getContent().length(), cf.getMd5(),
                                ContentUtils.truncateContent(cf.getContent()));
                    }
                } else {
                    // remove config info
                    configInfoPersistService.removeConfigInfo(dataId, group, tenant, InetUtils.getSelfIP(), null);
                    LOGGER.warn("[merge-delete] delete config info because no datum. dataId=" + dataId + ", groupId="
                            + group);
                }
                
            } catch (Throwable e) {
                LOGGER.info("[merge-error] " + dataId + ", " + group + ", " + e.toString(), e);
            }
            FINISHED.incrementAndGet();
            if (FINISHED.get() % 100 == 0) {
                LOGGER.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
            }
        }
        LOGGER.info("[all-merge-dump] {} / {}", FINISHED.get(), total);
    }
    
    public void executeConfigsMerge(List<ConfigInfoChanged> configInfoList) {
        new MergeAllDataWorker(configInfoList).start();
    }
    
    public class MergeAllDataWorker extends Thread {
        
        static final int PAGE_SIZE = 10000;
        
        private List<ConfigInfoChanged> configInfoList;
        
        public MergeAllDataWorker(List<ConfigInfoChanged> configInfoList) {
            super("MergeAllDataWorker");
            this.configInfoList = configInfoList;
        }
        
        @Override
        public void run() {
            executeMergeConfigTask(configInfoList, PAGE_SIZE);
        }
    }
}
