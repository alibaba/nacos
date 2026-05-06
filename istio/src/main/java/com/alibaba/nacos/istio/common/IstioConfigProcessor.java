
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
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.DestinationRule;
import com.alibaba.nacos.istio.model.PushRequest;
import com.alibaba.nacos.istio.model.VirtualService;
import com.alibaba.nacos.istio.xds.NacosXdsService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import java.util.Map;

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
    
    private static final String VIRTUAL_SERVICE = "VirtualService";
    
    private static final String DESTINATION_RULE = "DestinationRule";
    
    private static final String API_VERSION = "networking.istio.io/v1alpha3";
    
    Yaml yaml = new Yaml();
    
    public IstioConfigProcessor() {
        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof IstioConfigChangeEvent) {
                    IstioConfigChangeEvent istioConfigChangeEvent = (IstioConfigChangeEvent) event;
                    String content = istioConfigChangeEvent.content;
                    if (isContentValid(content) && tryParseContent(content)) {
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
                
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return IstioConfigChangeEvent.class;
            }
        });
    }
    
    public boolean isContentValid(String content) {
        if (content == null || content.trim().isEmpty()) {
            Loggers.MAIN.warn("Configuration content is null or empty.");
            return false;
        }
    
        Map<String, Object> obj;
        try {
            obj = yaml.load(content);
        } catch (Exception e) {
            Loggers.MAIN.error("Invalid YAML content.", e);
            return false;
        }
        
        String apiVersion = obj.containsKey("apiVersion") ? (String) obj.get("apiVersion") : "";
        String kind = obj.containsKey("kind") ? (String) obj.get("kind") : "";
    
        return API_VERSION.equals(apiVersion) && (VIRTUAL_SERVICE.equals(kind)
                || DESTINATION_RULE.equals(kind)) && obj.containsKey("metadata") && obj.containsKey("spec");
    }
    
    public boolean tryParseContent(String content) {
    
        if (content == null || content.trim().isEmpty()) {
            Loggers.MAIN.warn("Configuration content is null or empty.");
            return false;
        }
    
        try {
            Map<String, Object> obj = yaml.load(content);
            String kind = (String) obj.get("kind");
            if (VIRTUAL_SERVICE.equals(kind)) {
                VirtualService virtualService = yaml.loadAs(content, VirtualService.class);
                Loggers.MAIN.info("Configuration Content was successfully parsed as VirtualService.");
            } else if (DESTINATION_RULE.equals(kind)) {
                DestinationRule destinationRule = yaml.loadAs(content, DestinationRule.class);
                Loggers.MAIN.info("Configuration Content was successfully parsed as DestinationRule.");
            } else {
                Loggers.MAIN.warn("Unknown Config : Unknown 'kind' field in content: {}", kind);
                return false;
            }
            return true;
        } catch (Exception e) {
            Loggers.MAIN.error("Error parsing configuration content: {}", e.getMessage(), e);
            return false;
        }
    }
    
}
