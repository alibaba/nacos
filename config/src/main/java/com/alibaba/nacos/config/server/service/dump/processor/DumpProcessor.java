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

import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.service.dump.DumpConfigHandler;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;

import java.util.Objects;

/**
 * dump processor.
 *
 * @author Nacos
 * @date 2020/7/5 12:19 PM
 */
public class DumpProcessor implements NacosTaskProcessor {
    
    final ConfigInfoPersistService configInfoPersistService;
    
    final ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    final ConfigInfoTagPersistService configInfoTagPersistService;
    
    public DumpProcessor(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService,
            ConfigInfoTagPersistService configInfoTagPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoTagPersistService = configInfoTagPersistService;
    }
    
    @Override
    public boolean process(NacosTask task) {
        DumpTask dumpTask = (DumpTask) task;
        String[] pair = GroupKey2.parseKey(dumpTask.getGroupKey());
        String dataId = pair[0];
        String group = pair[1];
        String tenant = pair[2];
        long lastModifiedOut = dumpTask.getLastModified();
        String handleIp = dumpTask.getHandleIp();
        boolean isBeta = dumpTask.isBeta();
        String tag = dumpTask.getTag();
        ConfigDumpEvent.ConfigDumpEventBuilder build = ConfigDumpEvent.builder().namespaceId(tenant).dataId(dataId)
                .group(group).isBeta(isBeta).tag(tag).handleIp(handleIp);
        String type = "formal";
        if (isBeta) {
            type = "beta";
        } else if (StringUtils.isNotBlank(tag)) {
            type = "tag-" + tag;
        }
        LogUtil.DUMP_LOG.info("[dump] process {} task. groupKey={}", type, dumpTask.getGroupKey());
        
        if (isBeta) {
            // if publish beta, then dump config, update beta cache
            ConfigInfoBetaWrapper cf = configInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
            build.remove(Objects.isNull(cf));
            build.betaIps(Objects.isNull(cf) ? null : cf.getBetaIps());
            build.content(Objects.isNull(cf) ? null : cf.getContent());
            build.type(Objects.isNull(cf) ? null : cf.getType());
            build.encryptedDataKey(Objects.isNull(cf) ? null : cf.getEncryptedDataKey());
            build.lastModifiedTs(Objects.isNull(cf) ? lastModifiedOut : cf.getLastModified());
            return DumpConfigHandler.configDump(build.build());
        }
        
        if (StringUtils.isNotBlank(tag)) {
            ConfigInfoTagWrapper cf = configInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
            build.remove(Objects.isNull(cf));
            build.content(Objects.isNull(cf) ? null : cf.getContent());
            build.type(Objects.isNull(cf) ? null : cf.getType());
            build.encryptedDataKey(Objects.isNull(cf) ? null : cf.getEncryptedDataKey());
            build.lastModifiedTs(Objects.isNull(cf) ? lastModifiedOut : cf.getLastModified());
            return DumpConfigHandler.configDump(build.build());
        }
        
        ConfigInfoWrapper cf = configInfoPersistService.findConfigInfo(dataId, group, tenant);
        build.remove(Objects.isNull(cf));
        build.content(Objects.isNull(cf) ? null : cf.getContent());
        build.type(Objects.isNull(cf) ? null : cf.getType());
        build.encryptedDataKey(Objects.isNull(cf) ? null : cf.getEncryptedDataKey());
        build.lastModifiedTs(Objects.isNull(cf) ? lastModifiedOut : cf.getLastModified());
        return DumpConfigHandler.configDump(build.build());
        
    }
}
