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

import java.sql.Timestamp;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public interface Intercept {

    /**
     * save config
     *
     * @param srcIp
     * @param srcUser
     * @param configInfo
     * @param time
     * @param configAdvanceInfo
     * @param notify
     */
    void configSave(final String srcIp,
                    final String srcUser,
                    final ConfigInfo configInfo,
                    final Timestamp time,
                    final Map<String, Object> configAdvanceInfo,
                    final boolean notify);


    /**
     * config with tag save
     *
     * @param configInfo
     * @param tag
     * @param srcIp
     * @param srcUser
     * @param time
     * @param notify
     */
    void configTagSave(final ConfigInfo configInfo,
                       final String tag,
                       final String srcIp,
                       final String srcUser,
                       final Timestamp time,
                       final boolean notify);

    /**
     * config with beta save
     *
     * @param configInfo
     * @param betaIps
     * @param srcIp
     * @param srcUser
     * @param time
     * @param notify
     */
    void configBetaSave(final ConfigInfo configInfo,
                        final String betaIps,
                        final String srcIp,
                        final String srcUser,
                        final Timestamp time,
                        final boolean notify);

    /**
     * config update
     *
     * @param srcIp
     * @param srcUser
     * @param configInfo
     * @param time
     * @param configAdvanceInfo
     * @param notify
     */
    void configUpdate(final String srcIp,
                      final String srcUser,
                      final ConfigInfo configInfo,
                      final Timestamp time,
                      final Map<String, Object> configAdvanceInfo,
                      final boolean notify);


    /**
     * config with tag update
     *
     * @param configInfo
     * @param tag
     * @param srcIp
     * @param srcUser
     * @param time
     * @param notify
     */
    void configTagUpdate(final ConfigInfo configInfo,
                         final String tag,
                         final String srcIp,
                         final String srcUser,
                         final Timestamp time,
                         final boolean notify);

    /**
     * config with beta update
     *
     * @param configInfo
     * @param betaIps
     * @param srcIp
     * @param srcUser
     * @param time
     * @param notify
     */
    void configBetaUpdate(final ConfigInfo configInfo,
                          final String betaIps,
                          final String srcIp,
                          final String srcUser,
                          final Timestamp time,
                          final boolean notify);

    /**
     * config remove
     *
     * @param dataId
     * @param group
     * @param tenant
     * @param srcIp
     * @param srcUser
     */
    void configRemove(final String dataId,
                      final String group,
                      final String tenant,
                      final String srcIp,
                      final String srcUser);

    /**
     * config with tag remove
     *
     * @param dataId
     * @param group
     * @param tenant
     * @param tag
     * @param srcIp
     * @param srcUser
     */
    void configTagRemove(final String dataId,
                         final String group,
                         final String tenant,
                         final String tag,
                         final String srcIp,
                         final String srcUser);

    /**
     * config with beta remove
     *
     * @param dataId
     * @param group
     * @param tenant
     */
    void configBetaRemove(final String dataId,
                          final String group,
                          final String tenant);

}
