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

package com.alibaba.nacos.config.server.service.intercept;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.PersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@ConditionalOnProperty(value = "nacos.config.store.type", havingValue = "separate")
@Primary
@Component
public class DefaultConfigIntercept implements Intercept {

    @Autowired
    private PersistService persistService;

    @Override
    public void configSave(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                           Map<String, Object> configAdvanceInfo, boolean notify) {
        persistService.addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
    }

    @Override
    public void configTagSave(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
                              Timestamp time, boolean notify) {
        persistService.addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
    }

    @Override
    public void configBetaSave(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
                               Timestamp time, boolean notify) {
        persistService.addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, false);
    }

    @Override
    public void configUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                             Map<String, Object> configAdvanceInfo, boolean notify) {
        persistService.updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
    }

    @Override
    public void configTagUpdate(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
                                Timestamp time, boolean notify) {
        persistService.updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
    }

    @Override
    public void configBetaUpdate(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
                                 Timestamp time, boolean notify) {
        persistService.updateConfigInfo4Beta(configInfo, srcIp, null, time, notify);
    }

    @Override
    public void configRemove(String dataId, String group, String tenant, String srcIp, String srcUser) {

    }

    @Override
    public void configTagRemove(String dataId, String group, String tenant, String tag, String srcIp,
                                String srcUser) {

    }

    @Override
    public void configBetaRemove(String dataId, String group, String tenant) {

    }

}
