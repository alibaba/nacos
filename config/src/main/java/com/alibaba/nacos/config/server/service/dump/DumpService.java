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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.NetUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllBetaProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllTagProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpProcessor;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllBetaTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTagTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.merge.MergeDatumService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.TimerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.config.server.utils.LogUtil.DUMP_LOG;

/**
 * Dump data service.
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class DumpService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DumpService.class);
    
    protected DumpProcessor processor;
    
    protected DumpAllProcessor dumpAllProcessor;
    
    protected DumpAllBetaProcessor dumpAllBetaProcessor;
    
    protected DumpAllTagProcessor dumpAllTagProcessor;
    
    protected ConfigInfoPersistService configInfoPersistService;
    
    protected NamespacePersistService namespacePersistService;
    
    protected HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    protected ConfigInfoAggrPersistService configInfoAggrPersistService;
    
    protected ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    protected ConfigInfoTagPersistService configInfoTagPersistService;
    
    protected MergeDatumService mergeDatumService;
    
    protected final ServerMemberManager memberManager;
    
    /**
     * full dump interval.
     */
    static final int DUMP_ALL_INTERVAL_IN_MINUTE = 6 * 60;
    
    /**
     * full dump delay.
     */
    static final int INITIAL_DELAY_IN_MINUTE = 6 * 60;
    
    private TaskManager dumpTaskMgr;
    
    private TaskManager dumpAllTaskMgr;
    
    static final int INIT_THREAD_COUNT = 10;
    
    int total = 0;
    
    private static final String BETA_TABLE_NAME = "config_info_beta";
    
    private static final String TAG_TABLE_NAME = "config_info_tag";
    
    private int retentionDays = 30;
    
    /**
     * Here you inject the dependent objects constructively, ensuring that some of the dependent functionality is
     * initialized ahead of time.
     *
     * @param memberManager {@link ServerMemberManager}
     */
    public DumpService(ConfigInfoPersistService configInfoPersistService,
            NamespacePersistService namespacePersistService,
            HistoryConfigInfoPersistService historyConfigInfoPersistService,
            ConfigInfoAggrPersistService configInfoAggrPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService, MergeDatumService mergeDatumService,
            ServerMemberManager memberManager) {
        this.configInfoPersistService = configInfoPersistService;
        this.namespacePersistService = namespacePersistService;
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        this.configInfoAggrPersistService = configInfoAggrPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
        this.mergeDatumService = mergeDatumService;
        this.memberManager = memberManager;
        this.processor = new DumpProcessor(this.configInfoPersistService, this.configInfoBetaPersistService,
                this.configInfoTagPersistService);
        this.dumpAllProcessor = new DumpAllProcessor(this.configInfoPersistService);
        this.dumpAllBetaProcessor = new DumpAllBetaProcessor(this.configInfoBetaPersistService);
        this.dumpAllTagProcessor = new DumpAllTagProcessor(this.configInfoTagPersistService);
        this.dumpTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpTaskManager");
        this.dumpTaskMgr.setDefaultTaskProcessor(processor);
        
        this.dumpAllTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpAllTaskManager");
        this.dumpAllTaskMgr.setDefaultTaskProcessor(dumpAllProcessor);
        
        this.dumpAllTaskMgr.addProcessor(DumpAllTask.TASK_ID, dumpAllProcessor);
        this.dumpAllTaskMgr.addProcessor(DumpAllBetaTask.TASK_ID, dumpAllBetaProcessor);
        this.dumpAllTaskMgr.addProcessor(DumpAllTagTask.TASK_ID, dumpAllTagProcessor);
        
        DynamicDataSource.getInstance().getDataSource();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                handleConfigDataChange(event);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
    }
    
    void handleConfigDataChange(Event event) {
        // Generate ConfigDataChangeEvent concurrently
        if (event instanceof ConfigDataChangeEvent) {
            ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
            
            DumpRequest dumpRequest = DumpRequest.create(evt.dataId, evt.group, evt.tenant, evt.lastModifiedTs,
                    NetUtils.localIP());
            dumpRequest.setBeta(evt.isBeta);
            dumpRequest.setBatch(evt.isBatch);
            dumpRequest.setTag(evt.tag);
            DumpService.this.dump(dumpRequest);
        }
    }
    
    /**
     * initialize.
     *
     * @throws Throwable throws Exception when actually operate.
     */
    protected abstract void init() throws Throwable;
    
    /**
     * config history clear.
     */
    class ConfigHistoryClear implements Runnable {
        
        private HistoryConfigCleaner historyConfigCleaner;
        
        public ConfigHistoryClear(HistoryConfigCleaner historyConfigCleaner) {
            this.historyConfigCleaner = historyConfigCleaner;
        }
        
        @Override
        public void run() {
            LOGGER.warn("clearHistoryConfig get scheduled");
            if (canExecute()) {
                try {
                    LOGGER.warn("clearHistoryConfig is enable in current context, try to run cleaner");
                    historyConfigCleaner.cleanHistoryConfig();
                    LOGGER.warn("history config cleaner successfully");
                } catch (Throwable e) {
                    LOGGER.error("clearConfigHistory error : {}", e.toString());
                }
            } else {
                LOGGER.warn("clearHistoryConfig is disable in current context");
            }
        }
    }
    
    /**
     * config history clear.
     */
    class DumpAllProcessorRunner implements Runnable {
        
        @Override
        public void run() {
            dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
        }
    }
    
    /**
     * dump all beta processor runner.
     */
    class DumpAllBetaProcessorRunner implements Runnable {
        
        @Override
        public void run() {
            dumpAllTaskMgr.addTask(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());
        }
    }
    
    /**
     * dump all tag processor runner.
     */
    class DumpAllTagProcessorRunner implements Runnable {
        
        @Override
        public void run() {
            dumpAllTaskMgr.addTask(DumpAllTagTask.TASK_ID, new DumpAllTagTask());
        }
    }
    
    protected void dumpOperate() throws NacosException {
        String dumpFileContext = "CONFIG_DUMP_TO_FILE";
        TimerContext.start(dumpFileContext);
        try {
            LogUtil.DEFAULT_LOG.warn("DumpService start");
            
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            
            try {
                dumpAllConfigInfoOnStartup(dumpAllProcessor);
                
                // update Beta cache
                LogUtil.DEFAULT_LOG.info("start clear all config-info-beta.");
                ConfigDiskServiceFactory.getInstance().clearAllBeta();
                if (namespacePersistService.isExistTable(BETA_TABLE_NAME)) {
                    dumpAllBetaProcessor.process(new DumpAllBetaTask());
                }
                // update Tag cache
                LogUtil.DEFAULT_LOG.info("start clear all config-info-tag.");
                ConfigDiskServiceFactory.getInstance().clearAllTag();
                if (namespacePersistService.isExistTable(TAG_TABLE_NAME)) {
                    dumpAllTagProcessor.process(new DumpAllTagTask());
                }
                
                // add to dump aggr
                List<ConfigInfoChanged> configList = configInfoAggrPersistService.findAllAggrGroup();
                if (configList != null && !configList.isEmpty()) {
                    total = configList.size();
                    List<List<ConfigInfoChanged>> splitList = mergeDatumService.splitList(configList,
                            INIT_THREAD_COUNT);
                    for (List<ConfigInfoChanged> list : splitList) {
                        mergeDatumService.executeConfigsMerge(list);
                    }
                    LOGGER.info("server start, schedule merge end.");
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG.error(
                        "Nacos Server did not start because dumpservice bean construction failure :\n" + e);
                throw new NacosException(NacosException.SERVER_ERROR,
                        "Nacos Server did not start because dumpservice bean construction failure :\n" + e.getMessage(),
                        e);
            }
            if (!EnvUtil.getStandaloneMode()) {
                
                Random random = new Random();
                long initialDelay = random.nextInt(INITIAL_DELAY_IN_MINUTE) + 10;
                LogUtil.DEFAULT_LOG.warn("initialDelay:{}", initialDelay);
                
                ConfigExecutor.scheduleConfigTask(new DumpAllProcessorRunner(), initialDelay,
                        DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
                
                ConfigExecutor.scheduleConfigTask(new DumpAllBetaProcessorRunner(), initialDelay,
                        DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
                
                ConfigExecutor.scheduleConfigTask(new DumpAllTagProcessorRunner(), initialDelay,
                        DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
                ConfigExecutor.scheduleConfigChangeTask(
                        new DumpChangeConfigWorker(this.configInfoPersistService, this.historyConfigInfoPersistService,
                                currentTime), random.nextInt((int) PropertyUtil.getDumpChangeWorkerInterval()),
                        TimeUnit.MILLISECONDS);
                
            }
            
            HistoryConfigCleaner cleaner = HistoryConfigCleanerManager.getHistoryConfigCleaner(
                    HistoryConfigCleanerConfig.getInstance().getActiveHistoryConfigCleaner());
            ConfigExecutor.scheduleConfigTask(new ConfigHistoryClear(cleaner), 10, 10, TimeUnit.MINUTES);
            
        } finally {
            TimerContext.end(dumpFileContext, LogUtil.DUMP_LOG);
        }
        
    }
    
    private void dumpAllConfigInfoOnStartup(DumpAllProcessor dumpAllProcessor) {
        
        try {
            LogUtil.DEFAULT_LOG.info("start clear all config-info.");
            ConfigDiskServiceFactory.getInstance().clearAll();
            dumpAllProcessor.process(new DumpAllTask(true));
        } catch (Exception e) {
            LogUtil.FATAL_LOG.error("dump config fail" + e.getMessage());
            throw e;
        }
    }
    
    /**
     * dump operation.
     *
     * @param dumpRequest dumpRequest.
     */
    public void dump(DumpRequest dumpRequest) {
        if (dumpRequest.isBeta()) {
            dumpBeta(dumpRequest.getDataId(), dumpRequest.getGroup(), dumpRequest.getTenant(),
                    dumpRequest.getLastModifiedTs(), dumpRequest.getSourceIp());
        } else if (dumpRequest.isBatch()) {
            dumpBatch(dumpRequest.getDataId(), dumpRequest.getGroup(), dumpRequest.getTenant(),
                    dumpRequest.getLastModifiedTs(), dumpRequest.getSourceIp());
        } else if (StringUtils.isNotBlank(dumpRequest.getTag())) {
            dumpTag(dumpRequest.getDataId(), dumpRequest.getGroup(), dumpRequest.getTenant(), dumpRequest.getTag(),
                    dumpRequest.getLastModifiedTs(), dumpRequest.getSourceIp());
        } else {
            dumpFormal(dumpRequest.getDataId(), dumpRequest.getGroup(), dumpRequest.getTenant(),
                    dumpRequest.getLastModifiedTs(), dumpRequest.getSourceIp());
        }
    }
    
    /**
     * dump formal config.
     *
     * @param dataId       dataId.
     * @param group        group.
     * @param tenant       tenant.
     * @param lastModified lastModified.
     * @param handleIp     handleIp.
     */
    private void dumpFormal(String dataId, String group, String tenant, long lastModified, String handleIp) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = groupKey;
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, false, false, false, null, lastModified, handleIp));
        DUMP_LOG.info("[dump] add formal task. groupKey={}", groupKey);
        
    }
    
    /**
     * dump beta.
     *
     * @param dataId       dataId.
     * @param group        group.
     * @param tenant       tenant.
     * @param lastModified lastModified.
     * @param handleIp     handleIp.
     */
    private void dumpBeta(String dataId, String group, String tenant, long lastModified, String handleIp) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = groupKey + "+beta";
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, true, false, false, null, lastModified, handleIp));
        DUMP_LOG.info("[dump] add beta task. groupKey={}", groupKey);
        
    }
    
    /**
     * dump batch.
     *
     * @param dataId       dataId.
     * @param group        group.
     * @param tenant       tenant.
     * @param lastModified lastModified.
     * @param handleIp     handleIp.
     */
    private void dumpBatch(String dataId, String group, String tenant, long lastModified, String handleIp) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = groupKey + "+batch";
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, false, true, false, null, lastModified, handleIp));
        DUMP_LOG.info("[dump] add batch task. groupKey={}", dataId + "+" + group);
    }
    
    /**
     * dump tag.
     *
     * @param dataId       dataId.
     * @param group        group.
     * @param tenant       tenant.
     * @param tag          tag.
     * @param lastModified lastModified.
     * @param handleIp     handleIp.
     */
    private void dumpTag(String dataId, String group, String tenant, String tag, long lastModified, String handleIp) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = groupKey + "+tag+" + tag;
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, false, false, true, tag, lastModified, handleIp));
        DUMP_LOG.info("[dump] add tag task. groupKey={},tag={}", groupKey, tag);
        
    }
    
    public void dumpAll() {
        dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
    }
    
    /**
     * Used to determine whether the aggregation task, configuration history cleanup task can be performed.
     *
     * @return {@link Boolean}
     */
    protected abstract boolean canExecute();
}
