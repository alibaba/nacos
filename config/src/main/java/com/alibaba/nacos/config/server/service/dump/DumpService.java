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

import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.PersistService;
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
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.GlobalExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.SpringUtils;
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
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;
import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

/**
 * Dump data service
 *
 * @author Nacos
 */
@DependsOn("serverMemberManager")
@Service
public class DumpService {

    /**
     * 全量dump间隔
     */
    static final int DUMP_ALL_INTERVAL_IN_MINUTE = 6 * 60;
    /**
     * 全量dump间隔
     */
    static final int INITIAL_DELAY_IN_MINUTE = 6 * 60;
    static final int INIT_THREAD_COUNT = 10;
    private static final Logger log = LoggerFactory.getLogger(DumpService.class);
    private final static String TRUE_STR = "true";
    private final static String BETA_TABLE_NAME = "config_info_beta";
    private final static String TAG_TABLE_NAME = "config_info_tag";
    int total = 0;
    Boolean isQuickStart = false;
    @Autowired
    private PersistService persistService;
    @Autowired
    private MemberManager memberManager;
    @Autowired
    private CPProtocol protocol;
    private TaskManager dumpTaskMgr;
    private TaskManager dumpAllTaskMgr;

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

    @PostConstruct
    public void init() {

        // If using embedded distributed storage, you need to wait for the
        // underlying master to complete the selection

        if (PropertyUtil.isEmbeddedDistributedStorage()) {

            LogUtil.dumpLog.info("With embedded distributed storage, you need to wait for " +
                    "the underlying master to complete before you can perform the dump operation.");

            // watch path => /nacos_config/leader/ has value ?

            protocol.protocolMetaData()
                    .subscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
                            com.alibaba.nacos.consistency.cp.Constants.LEADER_META_DATA,
                            (o, arg) -> dumpOperate());
        } else {
            dumpOperate();
        }

    }

    private void dumpOperate() {
        LogUtil.defaultLog.warn("DumpService start");
        DumpProcessor processor = new DumpProcessor(this);
        DumpAllProcessor dumpAllProcessor = new DumpAllProcessor(this);
        DumpAllBetaProcessor dumpAllBetaProcessor = new DumpAllBetaProcessor(this);
        DumpAllTagProcessor dumpAllTagProcessor = new DumpAllTagProcessor(this);
        CleanConfigHistoryExecutor cleanHistoryProcessor = new CleanConfigHistoryExecutor(this, memberManager);

        dumpTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpTaskManager");
        dumpTaskMgr.setDefaultTaskProcessor(processor);

        dumpAllTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpAllTaskManager");
        dumpAllTaskMgr.setDefaultTaskProcessor(dumpAllProcessor);

        Runnable dumpAll = () -> dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());

        Runnable dumpAllBeta = () -> dumpAllTaskMgr.addTask(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());

        try {
            dumpConfigInfo(dumpAllProcessor);

            // 更新beta缓存
            LogUtil.defaultLog.info("start clear all config-info-beta.");
            DiskUtil.clearAllBeta();
            if (persistService.isExistTable(BETA_TABLE_NAME)) {
                dumpAllBetaProcessor.process(DumpAllBetaTask.TASK_ID, new DumpAllBetaTask());
            }
            // 更新Tag缓存
            LogUtil.defaultLog.info("start clear all config-info-tag.");
            DiskUtil.clearAllTag();
            if (persistService.isExistTable(TAG_TABLE_NAME)) {
                dumpAllTagProcessor.process(DumpAllTagTask.TASK_ID, new DumpAllTagTask());
            }

            if (memberManager.isFirstIp()) {

                // add to dump aggr

                List<ConfigInfoChanged> configList = persistService.findAllAggrGroup();
                if (configList != null && !configList.isEmpty()) {
                    total = configList.size();
                    List<List<ConfigInfoChanged>> splitList = splitList(configList, INIT_THREAD_COUNT);
                    for (List<ConfigInfoChanged> list : splitList) {
                        new MergeAllDataWorker(list).start();
                    }
                    log.info("server start, schedule merge end.");
                }
            }
        } catch (Exception e) {
            LogUtil.fatalLog.error(
                    "Nacos Server did not start because dumpservice bean construction failure :\n" + ExceptionUtil.getAllExceptionMsg(e),
                    e.getCause());
            throw new RuntimeException(
                    "Nacos Server did not start because dumpservice bean construction failure :\n" + ExceptionUtil.getAllExceptionMsg(e));
        }
        if (!STANDALONE_MODE) {
            Runnable heartbeat = () -> {
                String heartBeatTime = TimeUtils.getCurrentTime().toString();
                // write disk
                try {
                    DiskUtil.saveHeartBeatToDisk(heartBeatTime);
                } catch (IOException e) {
                    LogUtil.fatalLog.error("save heartbeat fail" + e.getMessage());
                }
            };

            GlobalExecutor.scheduleWithFixedDelay(heartbeat, 0, 10, TimeUnit.SECONDS);

            long initialDelay = new Random().nextInt(INITIAL_DELAY_IN_MINUTE) + 10;
            LogUtil.defaultLog.warn("initialDelay:{}", initialDelay);

            GlobalExecutor.scheduleWithFixedDelay(dumpAll, initialDelay, DUMP_ALL_INTERVAL_IN_MINUTE,
                    TimeUnit.MINUTES);

            GlobalExecutor.scheduleWithFixedDelay(dumpAllBeta, initialDelay, DUMP_ALL_INTERVAL_IN_MINUTE,
                    TimeUnit.MINUTES);
        }

        cleanHistoryProcessor.start();

    }

    private void dumpConfigInfo(DumpAllProcessor dumpAllProcessor)
            throws IOException {
        int timeStep = 6;
        boolean isAllDump = true;
        // initial dump all
        FileInputStream fis = null;
        Timestamp heartBeatLastStamp = null;
        try {
            if (isQuickStart()) {
                File heartbeatFile = DiskUtil.heartBeatFile();
                if (heartbeatFile.exists()) {
                    fis = new FileInputStream(heartbeatFile);
                    String heartBeatTempLast = IoUtils.toString(fis, Constants.ENCODE);
                    heartBeatLastStamp = Timestamp.valueOf(heartBeatTempLast);
                    if (TimeUtils.getCurrentTime().getTime()
                            - heartBeatLastStamp.getTime() < timeStep * 60 * 60 * 1000) {
                        isAllDump = false;
                    }
                }
            }
            if (isAllDump) {
                LogUtil.defaultLog.info("start clear all config-info.");
                DiskUtil.clearAll();
                dumpAllProcessor.process(DumpAllTask.TASK_ID, new DumpAllTask());
            } else {
                Timestamp beforeTimeStamp = getBeforeStamp(heartBeatLastStamp,
                        timeStep);
                DumpChangeProcessor dumpChangeProcessor = new DumpChangeProcessor(
                        this, beforeTimeStamp, TimeUtils.getCurrentTime());
                dumpChangeProcessor.process(DumpChangeTask.TASK_ID, new DumpChangeTask());
                Runnable checkMd5Task = () -> {
                    LogUtil.defaultLog.error("start checkMd5Task");
                    List<String> diffList = ConfigService.checkMd5();
                    for (String groupKey : diffList) {
                        String[] dg = GroupKey.parseKey(groupKey);
                        String dataId = dg[0];
                        String group = dg[1];
                        String tenant = dg[2];
                        ConfigInfoWrapper configInfo = persistService.queryConfigInfo(dataId, group, tenant);
                        ConfigService.dumpChange(dataId, group, tenant, configInfo.getContent(),
                                configInfo.getLastModified());
                    }
                    LogUtil.defaultLog.error("end checkMd5Task");
                };
                GlobalExecutor.scheduleWithFixedDelay(checkMd5Task, 0, 12,
                        TimeUnit.HOURS);
            }
        } catch (IOException e) {
            LogUtil.fatalLog.error("dump config fail" + e.getMessage());
            throw e;
        } finally {
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LogUtil.defaultLog.warn("close file failed");
                }
            }
        }
    }

    private Timestamp getBeforeStamp(Timestamp date, int step) {
        Calendar cal = Calendar.getInstance();
        /**
         *  date 换成已经已知的Date对象
         */
        cal.setTime(date);
        /**
         *  before 6 hour
         */
        cal.add(Calendar.HOUR_OF_DAY, -step);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return Timestamp.valueOf(format.format(cal.getTime()));
    }

    private Boolean isQuickStart() {
        try {
            String val = null;
            val = SpringUtils.getProperty("isQuickStart");
            if (TRUE_STR.equals(val)) {
                isQuickStart = true;
            }
            fatalLog.warn("isQuickStart:{}", isQuickStart);
        } catch (Exception e) {
            fatalLog.error("read application.properties wrong", e);
        }
        return isQuickStart;
    }

    public void dump(String dataId, String group, String tenant, String tag, long lastModified, String handleIp) {
        dump(dataId, group, tenant, tag, lastModified, handleIp, false);
    }

    public void dump(String dataId, String group, String tenant, long lastModified, String handleIp) {
        dump(dataId, group, tenant, lastModified, handleIp, false);
    }

    public void dump(String dataId, String group, String tenant, long lastModified, String handleIp, boolean isBeta) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        dumpTaskMgr.addTask(groupKey, new DumpTask(groupKey, lastModified, handleIp, isBeta));
    }

    public void dump(String dataId, String group, String tenant, String tag, long lastModified, String handleIp,
                     boolean isBeta) {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        dumpTaskMgr.addTask(groupKey, new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));
    }

    public void dumpAll() {
        dumpAllTaskMgr.addTask(DumpAllTask.TASK_ID, new DumpAllTask());
    }

    public PersistService getPersistService() {
        return persistService;
    }

}
