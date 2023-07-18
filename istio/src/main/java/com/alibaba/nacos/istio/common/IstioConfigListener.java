
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
import com.alibaba.nacos.istio.model.DestinationRule;
import com.alibaba.nacos.istio.model.VirtualService;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * Listener for IstioConfig.
 *
 * @author junweiasdf
 */
@Service
public class IstioConfigListener {
    
    private static final String TYPE_VIRTUAL_SERVICE = "virtualservice";
    
    private static final String TYPE_DESTINATION_RULE = "destinationrule";
    
    private final Yaml yaml;
    
    public IstioConfigListener() {
        this.yaml = new Yaml();
        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof IstioConfigChangeEvent) {
                    processEvent((IstioConfigChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return IstioConfigChangeEvent.class;
            }
        });
    }
    
    private void processEvent(IstioConfigChangeEvent event) {
        String type = event.type;
        String content = event.content;
        try {
            if (TYPE_VIRTUAL_SERVICE.equals(type)) {
                VirtualService vs = parseContent(content, VirtualService.class);
                System.out.println(vs);
            } else if (TYPE_DESTINATION_RULE.equals(type)) {
                DestinationRule dr = parseContent(content, DestinationRule.class);
                System.out.println(dr);
            }
        } catch (Exception e) {
            // A good practice is to have some meaningful logging here.
            System.err.println("Error processing event of type " + type);
            e.printStackTrace();
        }
    }
    
    private <T> T parseContent(String content, Class<T> valueType) {
        return yaml.loadAs(content, valueType);
    }
}
