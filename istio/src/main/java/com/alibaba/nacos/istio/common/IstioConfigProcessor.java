
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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.event.IstioConfigChangeEvent;
import com.alibaba.nacos.istio.model.PushRequest;
import com.alibaba.nacos.istio.xds.NacosXdsService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Listener for IstioConfig.
 *
 * @author junwei
 */
@Service
@Component
public class IstioConfigProcessor {
    
    private NacosXdsService nacosXdsService;
    
    private NacosResourceManager resourceManager;
    
    public static final String CONFIG_REASON = "config";
    
    public IstioConfigProcessor() {
        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof IstioConfigChangeEvent) {
                    IstioConfigChangeEvent istioConfigChangeEvent = (IstioConfigChangeEvent) event;
                    String content = istioConfigChangeEvent.content;
                    PushRequest pushRequest = new PushRequest(content, true);
                    if (null == nacosXdsService) {
                        nacosXdsService = ApplicationUtils.getBean(NacosXdsService.class);
                    }
                    if (null == resourceManager) {
                        resourceManager = ApplicationUtils.getBean(NacosResourceManager.class);
                    }
                    pushRequest.addReason(CONFIG_REASON);
                    ResourceSnapshot snapshot = resourceManager.createResourceSnapshot();
                    pushRequest.setResourceSnapshot(snapshot);
                    nacosXdsService.handleConfigEvent(pushRequest);
                }
                
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return IstioConfigChangeEvent.class;
            }
        });
    }
}
