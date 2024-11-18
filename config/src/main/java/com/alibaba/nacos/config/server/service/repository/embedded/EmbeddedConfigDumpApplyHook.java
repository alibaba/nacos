/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.ConfigDumpEvent;
import com.alibaba.nacos.config.server.service.dump.DumpConfigHandler;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.utils.GenericType;
import com.alibaba.nacos.persistence.repository.embedded.hook.EmbeddedApplyHook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Embedded apply hook for config dump.
 *
 * @author xiweng.yy
 */
@Component
public class EmbeddedConfigDumpApplyHook extends EmbeddedApplyHook {
    
    public EmbeddedConfigDumpApplyHook() {
        NotifyCenter.registerToPublisher(ConfigDumpEvent.class, NotifyCenter.ringBufferSize);
        NotifyCenter.registerSubscriber(new DumpConfigHandler());
    }
    
    @Override
    public void afterApply(WriteRequest log) {
        handleExtendInfo(log.getExtendInfoMap());
    }
    
    private void handleExtendInfo(Map<String, String> extendInfo) {
        if (extendInfo.containsKey(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT)) {
            String jsonVal = extendInfo.get(Constants.EXTEND_INFO_CONFIG_DUMP_EVENT);
            if (StringUtils.isNotBlank(jsonVal)) {
                NotifyCenter.publishEvent(JacksonUtils.toObj(jsonVal, ConfigDumpEvent.class));
            }
            return;
        }
        if (extendInfo.containsKey(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT)) {
            String jsonVal = extendInfo.get(Constants.EXTEND_INFOS_CONFIG_DUMP_EVENT);
            if (StringUtils.isNotBlank(jsonVal)) {
                List<ConfigDumpEvent> list = JacksonUtils.toObj(jsonVal, new GenericType<List<ConfigDumpEvent>>() {
                }.getType());
                list.stream().filter(Objects::nonNull).forEach(NotifyCenter::publishEvent);
            }
        }
    }
}
