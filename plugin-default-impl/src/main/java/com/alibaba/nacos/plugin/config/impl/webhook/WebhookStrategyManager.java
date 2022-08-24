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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * manage the all webhook notify strategy.
 *
 * @author liyunfei
 */
public class WebhookStrategyManager {
    
    private final Map<String, WebHookNotifyStrategy> webHookNotifyStrategyMap = new HashMap<>();
    
    private static final WebhookStrategyManager INSTANCE = new WebhookStrategyManager();
    
    public static WebhookStrategyManager getInstance() {
        return INSTANCE;
    }
    
    private WebhookStrategyManager() {
        loadStrategies();
    }
    
    private void loadStrategies() {
        webHookNotifyStrategyMap.put("cloudevent", new WebHookCloudEventStrategy());
    }
    
    /**
     * find webhook strategy by name.
     *
     * @param name strategy name
     * @return Optional WebHookNotifyStrategy
     */
    public Optional<WebHookNotifyStrategy> findStrategyByName(String name) {
        return Optional.ofNullable(webHookNotifyStrategyMap.get(name));
    }
    
    public WebHookNotifyStrategy getDefaultStrategy() {
        return webHookNotifyStrategyMap.get("eventBridge");
    }
}
