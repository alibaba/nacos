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

package com.alibaba.nacos.config.server.service.dump.processor;

import com.alibaba.nacos.config.server.manager.AbstractTask;
import com.alibaba.nacos.config.server.manager.TaskProcessor;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class DumpProcessor implements TaskProcessor {

    final DumpService dumpService;
    private PersistService persistService;

    public DumpProcessor(DumpService dumpService) {
        this.dumpService = dumpService;
        this.persistService = dumpService.getPersistService();
    }

    @Override
    public boolean process(String taskType, AbstractTask task) {
        DumpTask dumpTask = (DumpTask) task;
        String[] pair = GroupKey2.parseKey(dumpTask.getGroupKey());
        String dataId = pair[0];
        String group = pair[1];
        String tenant = pair[2];
        long lastModified = dumpTask.getLastModified();
        String handleIp = dumpTask.getHandleIp();
        boolean isBeta = dumpTask.isBeta();
        String tag = dumpTask.getTag();
        if (isBeta) {
            // beta发布，则dump数据，更新beta缓存
            ConfigInfo4Beta cf = persistService.findConfigInfo4Beta(dataId, group, tenant);
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
                ConfigInfo cf = persistService.findConfigInfo(dataId, group, tenant);
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
                    result = ConfigService.dump(dataId, group, tenant, cf.getContent(), lastModified, cf.getType());

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
                ConfigInfo4Tag cf = persistService.findConfigInfo4Tag(dataId, group, tenant, tag);
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
}
