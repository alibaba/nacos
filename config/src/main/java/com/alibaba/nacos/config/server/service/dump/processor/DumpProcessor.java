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
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.service.dump.DumpConfigHandler;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
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
    
    final ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    public DumpProcessor(ConfigInfoPersistService configInfoPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
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
        String grayName = dumpTask.getGrayName();
        
        ConfigDumpEvent.ConfigDumpEventBuilder build = ConfigDumpEvent.builder().namespaceId(tenant).dataId(dataId)
                .group(group).grayName(grayName).handleIp(handleIp);
        String type = "formal";
        if (StringUtils.isNotBlank(grayName)) {
            type = grayName;
        }
        LogUtil.DUMP_LOG.info("[dump] process {} task. groupKey={}", type, dumpTask.getGroupKey());
        
        if (StringUtils.isNotBlank(grayName)) {
            ConfigInfoGrayWrapper cf = configInfoGrayPersistService.findConfigInfo4Gray(dataId, group, tenant,
                    grayName);
            build.remove(Objects.isNull(cf));
            build.content(Objects.isNull(cf) ? null : cf.getContent());
            build.type(Objects.isNull(cf) ? null : cf.getType());
            build.encryptedDataKey(Objects.isNull(cf) ? null : cf.getEncryptedDataKey());
            build.lastModifiedTs(Objects.isNull(cf) ? lastModifiedOut : cf.getLastModified());
            build.grayName(grayName);
            build.grayRule(Objects.isNull(cf) ? null : cf.getGrayRule());
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
