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

package com.alibaba.nacos.config.server.model.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * ConfigDumpEvent.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ConfigDumpEvent extends Event {
    
    private static final long serialVersionUID = -8776888606458370294L;
    
    private boolean remove;
    
    private String namespaceId;
    
    private String dataId;
    
    private String group;
    
    private boolean isBeta;
    
    private String tag;
    
    private String content;
    
    private String betaIps;
    
    private String handleIp;
    
    private String type;
    
    private long lastModifiedTs;
    
    public boolean isRemove() {
        return remove;
    }
    
    public void setRemove(boolean remove) {
        this.remove = remove;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean beta) {
        isBeta = beta;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getBetaIps() {
        return betaIps;
    }
    
    public void setBetaIps(String betaIps) {
        this.betaIps = betaIps;
    }
    
    public String getHandleIp() {
        return handleIp;
    }
    
    public void setHandleIp(String handleIp) {
        this.handleIp = handleIp;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public static ConfigDumpEventBuilder builder() {
        return new ConfigDumpEventBuilder();
    }
    
    public static final class ConfigDumpEventBuilder {
        
        private boolean remove;
        
        private String namespaceId;
        
        private String dataId;
        
        private String group;
        
        private boolean isBeta;
        
        private String tag;
        
        private String content;
        
        private String betaIps;
        
        private String handleIp;
        
        private String type;
        
        private long lastModifiedTs;
        
        private ConfigDumpEventBuilder() {
        }
        
        public ConfigDumpEventBuilder remove(boolean remove) {
            this.remove = remove;
            return this;
        }
        
        public ConfigDumpEventBuilder namespaceId(String namespaceId) {
            this.namespaceId = namespaceId;
            return this;
        }
        
        public ConfigDumpEventBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }
        
        public ConfigDumpEventBuilder group(String group) {
            this.group = group;
            return this;
        }
        
        public ConfigDumpEventBuilder isBeta(boolean isBeta) {
            this.isBeta = isBeta;
            return this;
        }
        
        public ConfigDumpEventBuilder tag(String tag) {
            this.tag = tag;
            return this;
        }
        
        public ConfigDumpEventBuilder content(String content) {
            this.content = content;
            return this;
        }
        
        public ConfigDumpEventBuilder betaIps(String betaIps) {
            this.betaIps = betaIps;
            return this;
        }
        
        public ConfigDumpEventBuilder handleIp(String handleIp) {
            this.handleIp = handleIp;
            return this;
        }
        
        public ConfigDumpEventBuilder type(String type) {
            this.type = type;
            return this;
        }
        
        public ConfigDumpEventBuilder lastModifiedTs(long lastModifiedTs) {
            this.lastModifiedTs = lastModifiedTs;
            return this;
        }
        
        /**
         * Build a configDumpEvent.
         *
         * @return ConfigDumpEvent object instance.
         */
        public ConfigDumpEvent build() {
            ConfigDumpEvent configDumpEvent = new ConfigDumpEvent();
            configDumpEvent.setRemove(remove);
            configDumpEvent.setNamespaceId(namespaceId);
            configDumpEvent.setDataId(dataId);
            configDumpEvent.setGroup(group);
            configDumpEvent.setTag(tag);
            configDumpEvent.setContent(content);
            configDumpEvent.setBetaIps(betaIps);
            configDumpEvent.setHandleIp(handleIp);
            configDumpEvent.setType(type);
            configDumpEvent.setLastModifiedTs(lastModifiedTs);
            configDumpEvent.isBeta = this.isBeta;
            return configDumpEvent;
        }
    }
}
