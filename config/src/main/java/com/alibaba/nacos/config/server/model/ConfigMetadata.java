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

package com.alibaba.nacos.config.server.model;

import java.util.List;
import java.util.Objects;

/**
 * config export Metadata.
 *
 * @author Nacos
 */
public class ConfigMetadata {
    
    private List<ConfigExportItem> metadata;
    
    public static class ConfigExportItem {
        
        private String group;
        
        private String dataId;
        
        private String desc;
        
        private String type;
        
        private String appName;
        
        public String getGroup() {
            return group;
        }
        
        public void setGroup(String group) {
            this.group = group;
        }
        
        public String getDataId() {
            return dataId;
        }
        
        public void setDataId(String dataId) {
            this.dataId = dataId;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public void setDesc(String desc) {
            this.desc = desc;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getAppName() {
            return appName;
        }
        
        public void setAppName(String appName) {
            this.appName = appName;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigExportItem that = (ConfigExportItem) o;
            return Objects.equals(group, that.group) && Objects.equals(dataId, that.dataId) && Objects
                    .equals(desc, that.desc) && Objects.equals(type, that.type) && Objects
                    .equals(appName, that.appName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(group, dataId, desc, type, appName);
        }
    }
    
    public List<ConfigExportItem> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(List<ConfigExportItem> metadata) {
        this.metadata = metadata;
    }
}
