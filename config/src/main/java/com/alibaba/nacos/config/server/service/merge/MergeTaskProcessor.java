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
import com.alibaba.nacos.config.server.manager.AbstractTask;
import com.alibaba.nacos.config.server.manager.TaskProcessor;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.ContentUtils;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Merge task processor
 *
 * @author Nacos
 */
public class MergeTaskProcessor implements TaskProcessor {
    final int PAGE_SIZE = 10000;

    MergeTaskProcessor(PersistService persistService, MergeDatumService mergeService) {
        this.persistService = persistService;
        this.mergeService = mergeService;
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        MergeDataTask mergeTask = (MergeDataTask)task;
        final String dataId = mergeTask.dataId;
        final String group = mergeTask.groupId;
        final String tenant = mergeTask.tenant;
        final String tag = mergeTask.tag;
        final String clientIp = mergeTask.getClientIp();
        try {
            List<ConfigInfoAggr> datumList = new ArrayList<ConfigInfoAggr>();
            int rowCount = persistService.aggrConfigInfoCount(dataId, group, tenant);
            int pageCount = (int)Math.ceil(rowCount * 1.0 / PAGE_SIZE);
            for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                Page<ConfigInfoAggr> page = persistService.findConfigInfoAggrByPage(dataId, group, tenant, pageNo,
                    PAGE_SIZE);
                if (page != null) {
                    datumList.addAll(page.getPageItems());
                    log.info("[merge-query] {}, {}, size/total={}/{}", dataId, group, datumList.size(), rowCount);
                }
            }

            final Timestamp time = TimeUtils.getCurrentTime();
            // 聚合
            if (datumList.size() > 0) {
                ConfigInfo cf = merge(dataId, group, tenant, datumList);

                persistService.insertOrUpdate(null, null, cf, time, null);

                log.info("[merge-ok] {}, {}, size={}, length={}, md5={}, content={}", dataId, group, datumList.size(),
                    cf.getContent().length(), cf.getMd5(), ContentUtils.truncateContent(cf.getContent()));

                ConfigTraceService.logPersistenceEvent(dataId, group, tenant, null, time.getTime(), InetUtils
                                .getSelfIp(),
                    ConfigTraceService.PERSISTENCE_EVENT_MERGE, cf.getContent());
            }
            // 删除
            else {
                if (StringUtils.isBlank(tag)) {
                    persistService.removeConfigInfo(dataId, group, tenant, clientIp, null);
                } else {
                    persistService.removeConfigInfoTag(dataId, group, tenant, tag, clientIp, null);
                }

                log.warn("[merge-delete] delete config info because no datum. dataId=" + dataId
                    + ", groupId=" + group);

                ConfigTraceService.logPersistenceEvent(dataId, group, tenant, null, time.getTime(), InetUtils.getSelfIp(),
                    ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
            }

            EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));

        } catch (Exception e) {
            mergeService.addMergeTask(dataId, group, tenant, mergeTask.getClientIp());
            log.info("[merge-error] " + dataId + ", " + group + ", " + e.toString(), e);
        }

        return true;
    }

    public static ConfigInfo merge(String dataId, String group, String tenant, List<ConfigInfoAggr> datumList) {
        StringBuilder sb = new StringBuilder();
        String appName = null;
        for (ConfigInfoAggr aggrInfo : datumList) {
            if (aggrInfo.getAppName() != null) {
                appName = aggrInfo.getAppName();
            }
            sb.append(aggrInfo.getContent());
            sb.append(Constants.NACOS_LINE_SEPARATOR);
        }
        String content = sb.substring(0, sb.lastIndexOf(Constants.NACOS_LINE_SEPARATOR));
        return new ConfigInfo(dataId, group, tenant, appName, content);
    }

    // =====================

    private static final Logger log = LoggerFactory.getLogger(MergeTaskProcessor.class);

    private PersistService persistService;
    private MergeDatumService mergeService;
}
