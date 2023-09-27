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

package com.alibaba.nacos.naming.core.v2.event.service;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Collection;

/**
 * Service event.
 *
 * @author xiweng.yy
 */
public class ServiceEvent extends Event {
    
    private static final long serialVersionUID = -9173247502346692418L;
    
    private Service service;
    
    public ServiceEvent() {
    }
    
    public ServiceEvent(Service service) {
        this.service = service;
    }
    
    public Service getService() {
        return service;
    }
    
    /**
     * Service data changed event.
     */
    public static class ServiceChangedEvent extends ServiceEvent {
        
        private static final long serialVersionUID = 2123694271992630822L;
        
        private final String changedType;
        
        public ServiceChangedEvent(Service service, String changedType) {
            this(service, changedType, false);
        }
        
        public ServiceChangedEvent(Service service, String changedType, boolean incrementRevision) {
            super(service);
            this.changedType = changedType;
            service.renewUpdateTime();
            if (incrementRevision) {
                service.incrementRevision();
            }
        }
        
        public String getChangedType() {
            return changedType;
        }
        
    }
    
    /**
     * Service is subscribed by one client event.
     */
    public static class ServiceSubscribedEvent extends ServiceEvent {
        
        private static final long serialVersionUID = -2645441445867337345L;
    
        private final String clientId;
        
        public ServiceSubscribedEvent(Service service, String clientId) {
            super(service);
            this.clientId = clientId;
        }
    
        public String getClientId() {
            return clientId;
        }
    }
    
    /**
     * A client initiates a fuzzy watch request.
     */
    public static class ServiceFuzzyWatchInitEvent extends ServiceEvent {
        
        private static final long serialVersionUID = -2645441445867337345L;
        
        private final String clientId;
        
        private final String pattern;
        
        private final Collection<Service> matchedService;
        
        public ServiceFuzzyWatchInitEvent(String clientId, String pattern, Collection<Service> matchedService) {
            super();
            this.clientId = clientId;
            this.pattern = pattern;
            this.matchedService = matchedService;
        }
        
        public String getClientId() {
            return clientId;
        }
        
        public String getPattern() {
            return pattern;
        }
        
        public Collection<Service> getMatchedService() {
            return matchedService;
        }
    }
}
