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

import com.alibaba.nacos.config.server.manager.AbstractTask;
import com.alibaba.nacos.config.server.manager.TaskProcessor;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.*;
import com.alibaba.nacos.config.server.service.PersistService.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.service.PersistService.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.service.PersistService.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.config.server.utils.StringUtils;

import java.sql.Timestamp;
import java.util.List;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * Dump data task
 *
 * @author Nacos
 */
public class DumpTask extends AbstractTask {

    public DumpTask(String groupKey, long lastModified, String handleIp) {
        this.groupKey = groupKey;
        this.lastModified = lastModified;
        this.handleIp = handleIp;
        this.isBeta = false;
        this.tag = null;
        /**
         * retry interval: 1s
         */
        setTaskInterval(1000L);
    }

    public DumpTask(String groupKey, long lastModified, String handleIp, boolean isBeta) {
        this.groupKey = groupKey;
        this.lastModified = lastModified;
        this.handleIp = handleIp;
        this.isBeta = isBeta;
        this.tag = null;
        /**
         *  retry interval: 1s
         */
        setTaskInterval(1000L);
    }

    public DumpTask(String groupKey, String tag, long lastModified, String handleIp, boolean isBeta) {
        this.groupKey = groupKey;
        this.lastModified = lastModified;
        this.handleIp = handleIp;
        this.isBeta = isBeta;
        this.tag = tag;
        /**
         * retry interval: 1s
         */
        setTaskInterval(1000L);
    }

    @Override
    public void merge(AbstractTask task) {
    }

    final String groupKey;
    final long lastModified;
    final String handleIp;
    final boolean isBeta;
    final String tag;
}

class DumpAllTask extends AbstractTask {
    @Override
    public void merge(AbstractTask task) {
    }

    static final String TASK_ID = "dumpAllConfigTask";
}

class DumpAllBetaTask extends AbstractTask {
    @Override
    public void merge(AbstractTask task) {
    }

    static final String TASK_ID = "dumpAllBetaConfigTask";
}

class DumpAllTagTask extends AbstractTask {
    @Override
    public void merge(AbstractTask task) {
    }

    static final String TASK_ID = "dumpAllTagConfigTask";
}

class DumpChangeTask extends AbstractTask {
    @Override
    public void merge(AbstractTask task) {
    }

    static final String TASK_ID = "dumpChangeConfigTask";
}

class DumpProcessor implements TaskProcessor {

    DumpProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        DumpTask dumpTask = (DumpTask)task;
        String[] pair = GroupKey2.parseKey(dumpTask.groupKey);
        String dataId = pair[0];
        String group = pair[1];
        String tenant = pair[2];
        long lastModified = dumpTask.lastModified;
        String handleIp = dumpTask.handleIp;
        boolean isBeta = dumpTask.isBeta;
        String tag = dumpTask.tag;
        if (isBeta) {
            // beta发布，则dump数据，更新beta缓存
            ConfigInfo4Beta cf = dumpService.persistService.findConfigInfo4Beta(dataId, group, tenant);
            boolean result;
            if (null != cf) {
                result = ConfigService.dumpBeta(dataId, group, tenant, cf.getContent(), lastModified, cf.getBetaIps());
                if (result) {
                    ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                        ConfigTraceService.DUMP_EVENT_OK, System.currentTimeMillis() - lastModified,
                        cf.getContent().length());
                }
            } else {
                result = ConfigService.removeBeta(dataId, group, tenant);
                if (result) {
                    ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                        ConfigTraceService.DUMP_EVENT_REMOVE_OK, System.currentTimeMillis() - lastModified, 0);
                }
            }
            return result;
        } else {
            if (StringUtils.isBlank(tag)) {
                ConfigInfo cf = dumpService.persistService.findConfigInfo(dataId, group, tenant);
                if (dataId.equals(AggrWhitelist.AGGRIDS_METADATA)) {
                    if (null != cf) {
                        AggrWhitelist.load(cf.getContent());
                    } else {
                        AggrWhitelist.load(null);
                    }
                }

                if (dataId.equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
                    if (null != cf) {
                        ClientIpWhiteList.load(cf.getContent());
                    } else {
                        ClientIpWhiteList.load(null);
                    }
                }

                if (dataId.equals(SwitchService.SWITCH_META_DATAID)) {
                    if (null != cf) {
                        SwitchService.load(cf.getContent());
                    } else {
                        SwitchService.load(null);
                    }
                }

                boolean result;
                if (null != cf) {
                    result = ConfigService.dump(dataId, group, tenant, cf.getContent(), lastModified);

                    if (result) {
                        ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                            ConfigTraceService.DUMP_EVENT_OK, System.currentTimeMillis() - lastModified,
                            cf.getContent().length());
                    }
                } else {
                    result = ConfigService.remove(dataId, group, tenant);

                    if (result) {
                        ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                            ConfigTraceService.DUMP_EVENT_REMOVE_OK, System.currentTimeMillis() - lastModified, 0);
                    }
                }
                return result;
            } else {
                ConfigInfo4Tag cf = dumpService.persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
                //
                boolean result;
                if (null != cf) {
                    result = ConfigService.dumpTag(dataId, group, tenant, tag, cf.getContent(), lastModified);
                    if (result) {
                        ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                            ConfigTraceService.DUMP_EVENT_OK, System.currentTimeMillis() - lastModified,
                            cf.getContent().length());
                    }
                } else {
                    result = ConfigService.removeTag(dataId, group, tenant, tag);
                    if (result) {
                        ConfigTraceService.logDumpEvent(dataId, group, tenant, null, lastModified, handleIp,
                            ConfigTraceService.DUMP_EVENT_REMOVE_OK, System.currentTimeMillis() - lastModified, 0);
                    }
                }
                return result;
            }
        }

    }

    final DumpService dumpService;
}

class DumpAllProcessor implements TaskProcessor {

    DumpAllProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.persistService;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        long currentMaxId = persistService.findConfigMaxId();
        long lastMaxId = 0;
        while (lastMaxId < currentMaxId) {
            Page<PersistService.ConfigInfoWrapper> page = persistService.findAllConfigInfoFragment(lastMaxId,
                PAGE_SIZE);
            if (page != null && page.getPageItems() != null) {
                for (PersistService.ConfigInfoWrapper cf : page.getPageItems()) {
                    long id = cf.getId();
                    lastMaxId = id > lastMaxId ? id : lastMaxId;
                    if (cf.getDataId().equals(AggrWhitelist.AGGRIDS_METADATA)) {
                        AggrWhitelist.load(cf.getContent());
                    }

                    if (cf.getDataId().equals(ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA)) {
                        ClientIpWhiteList.load(cf.getContent());
                    }

                    if (cf.getDataId().equals(SwitchService.SWITCH_META_DATAID)) {
                        SwitchService.load(cf.getContent());
                    }

                    boolean result = ConfigService.dump(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getContent(),
                        cf.getLastModified());

                    final String content = cf.getContent();
                    final String md5 = MD5.getInstance().getMD5String(content);
                    LogUtil.dumpLog.info("[dump-all-ok] {}, {}, length={}, md5={}",
                        GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(), content.length(), md5);
                }
                defaultLog.info("[all-dump] {} / {}", lastMaxId, currentMaxId);
            } else {
                lastMaxId += PAGE_SIZE;
            }
        }
        return true;
    }

    static final int PAGE_SIZE = 1000;

    final DumpService dumpService;
    final PersistService persistService;
}

class DumpAllBetaProcessor implements TaskProcessor {

    DumpAllBetaProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.persistService;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        int rowCount = persistService.configInfoBetaCount();
        int pageCount = (int)Math.ceil(rowCount * 1.0 / PAGE_SIZE);

        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoBetaWrapper> page = persistService.findAllConfigInfoBetaForDumpAll(pageNo, PAGE_SIZE);
            if (page != null) {
                for (ConfigInfoBetaWrapper cf : page.getPageItems()) {
                    boolean result = ConfigService.dumpBeta(cf.getDataId(), cf.getGroup(), cf.getTenant(),
                        cf.getContent(), cf.getLastModified(), cf.getBetaIps());
                    LogUtil.dumpLog.info("[dump-all-beta-ok] result={}, {}, {}, length={}, md5={}", result,
                        GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(), cf.getContent()
                            .length(), cf.getMd5());
                }

                actualRowCount += page.getPageItems().size();
                defaultLog.info("[all-dump-beta] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }

    static final int PAGE_SIZE = 1000;

    final DumpService dumpService;
    final PersistService persistService;
}

class DumpAllTagProcessor implements TaskProcessor {

    DumpAllTagProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.persistService;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        int rowCount = persistService.configInfoTagCount();
        int pageCount = (int)Math.ceil(rowCount * 1.0 / PAGE_SIZE);

        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoTagWrapper> page = persistService.findAllConfigInfoTagForDumpAll(pageNo, PAGE_SIZE);
            if (page != null) {
                for (ConfigInfoTagWrapper cf : page.getPageItems()) {
                    boolean result = ConfigService.dumpTag(cf.getDataId(), cf.getGroup(), cf.getTenant(), cf.getTag(),
                        cf.getContent(), cf.getLastModified());
                    LogUtil.dumpLog.info("[dump-all-Tag-ok] result={}, {}, {}, length={}, md5={}", result,
                        GroupKey2.getKey(cf.getDataId(), cf.getGroup()), cf.getLastModified(), cf.getContent()
                            .length(), cf.getMd5());
                }

                actualRowCount += page.getPageItems().size();
                defaultLog.info("[all-dump-tag] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }

    static final int PAGE_SIZE = 1000;

    final DumpService dumpService;
    final PersistService persistService;
}

class DumpChangeProcessor implements TaskProcessor {

    DumpChangeProcessor(DumpService dumpService, Timestamp startTime,
                        Timestamp endTime) {
        this.dumpService = dumpService;
        this.persistService = dumpService.persistService;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        LogUtil.defaultLog.warn("quick start; startTime:{},endTime:{}",
            startTime, endTime);
        LogUtil.defaultLog.warn("updateMd5 start");
        long startUpdateMd5 = System.currentTimeMillis();
        List<ConfigInfoWrapper> updateMd5List = persistService
            .listAllGroupKeyMd5();
        LogUtil.defaultLog.warn("updateMd5 count:{}", updateMd5List.size());
        for (ConfigInfoWrapper config : updateMd5List) {
            final String groupKey = GroupKey2.getKey(config.getDataId(),
                config.getGroup());
            ConfigService.updateMd5(groupKey, config.getMd5(),
                config.getLastModified());
        }
        long endUpdateMd5 = System.currentTimeMillis();
        LogUtil.defaultLog.warn("updateMd5 done,cost:{}", endUpdateMd5
            - startUpdateMd5);

        LogUtil.defaultLog.warn("deletedConfig start");
        long startDeletedConfigTime = System.currentTimeMillis();
        List<ConfigInfo> configDeleted = persistService.findDeletedConfig(
            startTime, endTime);
        LogUtil.defaultLog.warn("deletedConfig count:{}", configDeleted.size());
        for (ConfigInfo configInfo : configDeleted) {
            if (persistService.findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                configInfo.getTenant()) == null) {
                ConfigService.remove(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant());
            }
        }
        long endDeletedConfigTime = System.currentTimeMillis();
        LogUtil.defaultLog.warn("deletedConfig done,cost:{}",
            endDeletedConfigTime - startDeletedConfigTime);

        LogUtil.defaultLog.warn("changeConfig start");
        long startChangeConfigTime = System.currentTimeMillis();
        List<PersistService.ConfigInfoWrapper> changeConfigs = persistService
            .findChangeConfig(startTime, endTime);
        LogUtil.defaultLog.warn("changeConfig count:{}", changeConfigs.size());
        for (PersistService.ConfigInfoWrapper cf : changeConfigs) {
            boolean result = ConfigService.dumpChange(cf.getDataId(), cf.getGroup(), cf.getTenant(),
                cf.getContent(), cf.getLastModified());
            final String content = cf.getContent();
            final String md5 = MD5.getInstance().getMD5String(content);
            LogUtil.defaultLog.info(
                "[dump-change-ok] {}, {}, length={}, md5={}",
                new Object[] {
                    GroupKey2.getKey(cf.getDataId(), cf.getGroup()),
                    cf.getLastModified(), content.length(), md5});
        }
        ConfigService.reloadConfig();
        long endChangeConfigTime = System.currentTimeMillis();
        LogUtil.defaultLog.warn("changeConfig done,cost:{}",
            endChangeConfigTime - startChangeConfigTime);
        return true;
    }

    // =====================

    final DumpService dumpService;
    final PersistService persistService;
    final Timestamp startTime;
    final Timestamp endTime;
}
