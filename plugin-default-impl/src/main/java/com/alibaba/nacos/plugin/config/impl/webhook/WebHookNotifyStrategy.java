/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.impl.webhook;

import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;

/**
 * define the strategy which is used to implements specify webhook's way.
 *
 * @author liyunfei
 */
public interface WebHookNotifyStrategy {
    
    /**
     * the strategy which notify configChanges information,according to type.
     *
     * @param configChangeNotifyInfo the  information which config change
     * @param pushUrl                the url which push to user
     */
    void notifyConfigChange(ConfigChangeNotifyInfo configChangeNotifyInfo, String pushUrl);
    
    /**
     * WebHookNotifyStrategy Name which for conveniently find WebHookNotifyStrategy instance.
     *
     * @return NotifyStrategyName mark a WebHookNotifyStrategy instance.
     */
    String getNotifyStrategyName();
}
