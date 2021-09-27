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
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllBetaProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllTagProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpChangeProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpProcessor;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllBetaTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTagTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpChangeTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.merge.MergeTaskProcessor;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import com.alibaba.nacos.core.utils.TimerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.config.server.utils.LogUtil.DUMP_LOG;
import static com.alibaba.nacos.config.server.utils.LogUtil.FATAL_LOG;

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
    
    protected final PersistService persistService;
    
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
    
    static final AtomicInteger FINISHED = new AtomicInteger();
    
    static final int INIT_THREAD_COUNT = 10;
    
    int total = 0;
    
    private static final String TRUE_STR = "true";
    
    private static final String BETA_TABLE_NAME = "config_info_beta";
    
    private static final String TAG_TABLE_NAME = "config_info_tag";
    
    Boolean isQuickStart = false;
    
    private int retentionDays = 30;
    
    /**
     * Here you inject the dependent objects constructively, ensuring that some of the dependent functionality is
     * initialized ahead of time.
     *
     * @param persistService {@link PersistService}
     * @param memberManager  {@link ServerMemberManager}
     */
    public DumpService(PersistService persistService, ServerMemberManager memberManager) {
        this.persistService = persistService;
        this.memberManager = memberManager;
        this.processor = new DumpProcessor(this);
        this.dumpAllProcessor = new DumpAllProcessor(this);
        this.dumpAllBetaProcessor = new DumpAllBetaProcessor(this);
        this.dumpAllTagProcessor = new DumpAllTagProcessor(this);
        this.dumpTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpTaskManager");
        this.dumpTaskMgr.setDefaultTaskProcessor(processor);
        
        this.dumpAllTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpAllTaskManager");
        this.dumpAllTaskMgr.setDefaultTaskProcessor(dumpAllProcessor);
        
        this.dumpAllTaskMgr.addProcessor(DumpAllTask.TASK_ID, dumpAllProcessor);
        this.dumpAllTaskMgr.addProcessor(DumpAllBetaTask.TASK_ID, dumpAllBetaProcessor);
        this.dumpAllTaskMgr.addProcessor(DumpAllTagTask.TASK_ID, dumpAllTagProcessor);
        
        DynamicDataSource.getInstance().getDataSource();
    }
    
    public PersistService getPersistService() {
        return persistService;
    }
    
    public ServerMemberManager getMemberManager() {
        return memberManager;
    }
    
    /**
     * initialize.
     *
     * @throws Throwable throws Exception when actually operate.
     */
    protected abstract void init() throws Throwable;
    
    protected void dumpOperate(DumpProcessor processor, DumpAllProcessor dumpAllProcessor,
            DumpAllBetaProcessor dumpAllBetaProcessor, DumpAllTagProcessor dumpAllTagProcessor) throws NacosException {
        String dumpFileContext = "CONFIG_DUMP_TO_FILE";
        TimerContext.start(dumpFileContext);
        try {
            LogUtil.DEFAULT_LOG.warn("DumpService start");
            
            Runnable dumpAll = () -> dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
            
            Runnable dumpAllBeta = () -> dumpAllTaskMgr.addTask(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());
            
            Runnable dumpAllTag = () -> dumpAllTaskMgr.addTask(DumpAllTagTask.TASK_ID, new DumpAllTagTask());
            
            Runnable clearConfigHistory = () -> {
                LOGGER.warn("clearConfigHistory start");
                if (canExecute()) {
                    try {
                        Timestamp startTime = getBeforeStamp(TimeUtils.getCurrentTime(), 24 * getRetentionDays());
                        int totalCount = persistService.findConfigHistoryCountByTime(startTime);
                        if (totalCount > 0) {
                            int pageSize = 1000;
                            int removeTime = (totalCount + pageSize - 1) / pageSize;
                            LOGGER.warn(
                                    "clearConfigHistory, getBeforeStamp:{}, totalCount:{}, pageSize:{}, removeTime:{}",
                                    startTime, totalCount, pageSize, removeTime);
                            while (removeTime > 0) {
                                // delete paging to avoid reporting errors in batches
                                persistService.removeConfigHistory(startTime, pageSize);
                                removeTime--;
                            }
                        }
                    } catch (Throwable e) {
                        LOGGER.error("clearConfigHistory error : {}", e.toString());
                    }
                }
            };
            
            try {
                dumpConfigInfo(dumpAllProcessor);
                
                // update Beta cache
                LogUtil.DEFAULT_LOG.info("start clear all config-info-beta.");
                DiskUtil.clearAllBeta();
                if (persistService.isExistTable(BETA_TABLE_NAME)) {
                    dumpAllBetaProcessor.process(new DumpAllBetaTask());
                }
                // update Tag cache
                LogUtil.DEFAULT_LOG.info("start clear all config-info-tag.");
                DiskUtil.clearAllTag();
                if (persistService.isExistTable(TAG_TABLE_NAME)) {
                    dumpAllTagProcessor.process(new DumpAllTagTask());
                }
                
                // add to dump aggr
                List<ConfigInfoChanged> configList = persistService.findAllAggrGroup();
                if (configList != null && !configList.isEmpty()) {
                    total = configList.size();
                    List<List<ConfigInfoChanged>> splitList = splitList(configList, INIT_THREAD_COUNT);
                    for (List<ConfigInfoChanged> list : splitList) {
                        MergeAllDataWorker work = new MergeAllDataWorker(list);
                        work.start();
                    }
                    LOGGER.info("server start, schedule merge end.");
                }
            } catch (Exception e) {
                LogUtil.FATAL_LOG
                        .error("Nacos Server did not start because dumpservice bean construction failure :\n" + e
                                .toString());
                throw new NacosException(NacosException.SERVER_ERROR,
                        "Nacos Server did not start because dumpservice bean construction failure :\n" + e.getMessage(),
                        e);
            }
            if (!EnvUtil.getStandaloneMode()) {
                Runnable heartbeat = () -> {
                    String heartBeatTime = TimeUtils.getCurrentTime().toString();
                    // write disk
                    try {
                        DiskUtil.saveHeartBeatToDisk(heartBeatTime);
                    } catch (IOException e) {
                        LogUtil.FATAL_LOG.error("save heartbeat fail" + e.getMessage());
                    }
                };
                
                ConfigExecutor.scheduleConfigTask(heartbeat, 0, 10, TimeUnit.SECONDS);
                
                long initialDelay = new Random().nextInt(INITIAL_DELAY_IN_MINUTE) + 10;
                LogUtil.DEFAULT_LOG.warn("initialDelay:{}", initialDelay);
                
                ConfigExecutor.scheduleConfigTask(dumpAll, initialDelay, DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
                
                ConfigExecutor
                        .scheduleConfigTask(dumpAllBeta, initialDelay, DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
                
                ConfigExecutor
                        .scheduleConfigTask(dumpAllTag, initialDelay, DUMP_ALL_INTERVAL_IN_MINUTE, TimeUnit.MINUTES);
            }
            
            ConfigExecutor.scheduleConfigTask(clearConfigHistory, 10, 10, TimeUnit.MINUTES);
        } finally {
            TimerContext.end(dumpFileContext, LogUtil.DUMP_LOG);
        }
        
    }
    
    private void dumpConfigInfo(DumpAllProcessor dumpAllProcessor) throws IOException {
        int timeStep = 6;
        boolean isAllDump = true;
        // initial dump all
        FileInputStream fis = null;
        Timestamp heartheatLastStamp = null;
        try {
            if (isQuickStart()) {
                File heartbeatFile = DiskUtil.heartBeatFile();
                if (heartbeatFile.exists()) {
                    fis = new FileInputStream(heartbeatFile);
                    String heartheatTempLast = IoUtils.toString(fis, Constants.ENCODE);
                    heartheatLastStamp = Timestamp.valueOf(heartheatTempLast);
                    if (TimeUtils.getCurrentTime().getTime() - heartheatLastStamp.getTime()
                            < timeStep * 60 * 60 * 1000) {
                        isAllDump = false;
                    }
                }
            }
            if (isAllDump) {
                LogUtil.DEFAULT_LOG.info("start clear all config-info.");
                DiskUtil.clearAll();
                dumpAllProcessor.process(new DumpAllTask());
            } else {
                Timestamp beforeTimeStamp = getBeforeStamp(heartheatLastStamp, timeStep);
                DumpChangeProcessor dumpChangeProcessor = new DumpChangeProcessor(this, beforeTimeStamp,
                        TimeUtils.getCurrentTime());
                dumpChangeProcessor.process(new DumpChangeTask());
                Runnable checkMd5Task = () -> {
                    LogUtil.DEFAULT_LOG.error("start checkMd5Task");
                    List<String> diffList = ConfigCacheService.checkMd5();
                    for (String groupKey : diffList) {
                        String[] dg = GroupKey.parseKey(groupKey);
                        String dataId = dg[0];
                        String group = dg[1];
                        String tenant = dg[2];
                        ConfigInfoWrapper configInfo = persistService.queryConfigInfo(dataId, group, tenant);
                        ConfigCacheService.dumpChange(dataId, group, tenant, configInfo.getContent(),
                                configInfo.getLastModified());
                    }
                    LogUtil.DEFAULT_LOG.error("end checkMd5Task");
                };
                ConfigExecutor.scheduleConfigTask(checkMd5Task, 0, 12, TimeUnit.HOURS);
            }
        } catch (IOException e) {
            LogUtil.FATAL_LOG.error("dump config fail" + e.getMessage());
            throw e;
        } finally {
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LogUtil.DEFAULT_LOG.warn("close file failed");
                }
            }
        }
    }
    
    private Timestamp getBeforeStamp(Timestamp date, int step) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        // before 6 hour
        cal.add(Calendar.HOUR_OF_DAY, -step);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return Timestamp.valueOf(format.format(cal.getTime()));
    }
    
    private Boolean isQuickStart() {
        try {
            String val = null;
            val = EnvUtil.getProperty("isQuickStart");
            if (val != null && TRUE_STR.equals(val)) {
                isQuickStart = true;
            }
            FATAL_LOG.warn("isQuickStart:{}", isQuickStart);
        } catch (Exception e) {
            FATAL_LOG.error("read application.properties wrong", e);
        }
        return isQuickStart;
    }
    
    private int getRetentionDays() {
        String val = EnvUtil.getProperty("nacos.config.retention.days");
        if (null == val) {
            return retentionDays;
        }
        
        int tmp = 0;
        try {
            tmp = Integer.parseInt(val);
            if (tmp > 0) {
                retentionDays = tmp;
            }
        } catch (NumberFormatException nfe) {
            FATAL_LOG.error("read nacos.config.retention.days wrong", nfe);
        }
        
        return retentionDays;
    }
    
    public void dump(String dataId, String group, String tenant, String tag, long lastModified, String handleIp) {
        dump(dataId, group, tenant, tag, lastModified, handleIp, false);
    }
    
    public void dump(String dataId, String group, String tenant, long lastModified, String handleIp) {
        dump(dataId, group, tenant, lastModified, handleIp, false);
    }
    
    /**
     * Add DumpTask to TaskManager, it will execute asynchronously.
     */
    public void dump(String dataId, String group, String tenant, long lastModified, String handleIp, boolean isBeta) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = String.join("+", dataId, group, tenant, String.valueOf(isBeta));
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, lastModified, handleIp, isBeta));
        DUMP_LOG.info("[dump-task] add task. groupKey={}, taskKey={}", groupKey, taskKey);
    }
    
    /**
     * Add DumpTask to TaskManager, it will execute asynchronously.
     */
    public void dump(String dataId, String group, String tenant, String tag, long lastModified, String handleIp,
            boolean isBeta) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String taskKey = String.join("+", dataId, group, tenant, String.valueOf(isBeta), tag);
        dumpTaskMgr.addTask(taskKey, new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));
        DUMP_LOG.info("[dump-task] add task. groupKey={}, taskKey={}", groupKey, taskKey);
    }
    
    public void dumpAll() {
        dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
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
    
    class MergeAllDataWorker extends Thread {
        
        static final int PAGE_SIZE = 10000;
        
        private List<ConfigInfoChanged> configInfoList;
        
        public MergeAllDataWorker(List<ConfigInfoChanged> configInfoList) {
            super("MergeAllDataWorker");
            this.configInfoList = configInfoList;
        }
        
        @Override
        public void run() {
            if (!canExecute()) {
                return;
            }
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
                    // merge
                    if (datumList.size() > 0) {
                        ConfigInfo cf = MergeTaskProcessor.merge(dataId, group, tenant, datumList);
                        String aggrContent = cf.getContent();
                        String localContentMD5 = ConfigCacheService.getContentMd5(GroupKey.getKey(dataId, group));
                        String aggrConetentMD5 = MD5Utils.md5Hex(aggrContent, Constants.ENCODE);
                        
                        if (!StringUtils.equals(localContentMD5, aggrConetentMD5)) {
                            persistService.insertOrUpdate(null, null, cf, time, null, false);
                            LOGGER.info("[merge-ok] {}, {}, size={}, length={}, md5={}, content={}", dataId, group,
                                    datumList.size(), cf.getContent().length(), cf.getMd5(),
                                    ContentUtils.truncateContent(cf.getContent()));
                        }
                    } else {
                        // remove config info
                        persistService.removeConfigInfo(dataId, group, tenant, InetUtils.getSelfIP(), null);
                        LOGGER.warn(
                                "[merge-delete] delete config info because no datum. dataId=" + dataId + ", groupId="
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
    }
    
    /**
     * Used to determine whether the aggregation task, configuration history cleanup task can be performed.
     *
     * @return {@link Boolean}
     */
    protected abstract boolean canExecute();
}
