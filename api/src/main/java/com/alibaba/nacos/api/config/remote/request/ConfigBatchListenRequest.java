/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.config.remote.request;

import java.util.ArrayList;
import java.util.List;

/**
 * request of listening a batch of configs.
 *
 * @author liuzunfei
 * @version $Id: ConfigBatchListenRequest.java, v 0.1 2020年07月27日 7:46 PM liuzunfei Exp $
 */
public class ConfigBatchListenRequest extends AbstractConfigRequest {
    
    /**
     * listen or remove listen.
     */
    private boolean listen = true;
    
    private List<ConfigListenContext> configListenContexts = new ArrayList<ConfigListenContext>();
    
    /**
     * add listen config.
     *
     * @param group  group.
     * @param dataId dataId.
     * @param tenant tenant.
     * @param md5    md5.
     */
    public void addConfigListenContext(String group, String dataId, String tenant, String md5) {
        ConfigListenContext configListenContext = new ConfigListenContext();
        configListenContext.dataId = dataId;
        configListenContext.group = group;
        configListenContext.md5 = md5;
        configListenContext.tenant = tenant;
        configListenContexts.add(configListenContext);
    }
    
    /**
     * Getter method for property <tt>configListenContexts</tt>.
     *
     * @return property value of configListenContexts
     */
    public List<ConfigListenContext> getConfigListenContexts() {
        return configListenContexts;
    }
    
    /**
     * Setter method for property <tt>configListenContexts</tt>.
     *
     * @param configListenContexts value to be assigned to property configListenContexts
     */
    public void setConfigListenContexts(List<ConfigListenContext> configListenContexts) {
        this.configListenContexts = configListenContexts;
    }
    
    /**
     * Getter method for property <tt>listen</tt>.
     *
     * @return property value of listen
     */
    public boolean isListen() {
        return listen;
    }
    
    /**
     * Setter method for property <tt>listen</tt>.
     *
     * @param listen value to be assigned to property listen
     */
    public void setListen(boolean listen) {
        this.listen = listen;
    }
    
    public static class ConfigListenContext {
        
        String group;
        
        String md5;
        
        String dataId;
        
        String tenant;
        
        public ConfigListenContext() {
        
        }
        
        @Override
        public String toString() {
            return "ConfigListenContext{" + "group='" + group + '\'' + ", md5='" + md5 + '\'' + ", dataId='" + dataId
                    + '\'' + ", tenant='" + tenant + '\'' + '}';
        }
        
        /**
         * Getter method for property <tt>group</tt>.
         *
         * @return property value of group
         */
        public String getGroup() {
            return group;
        }
        
        /**
         * Setter method for property <tt>groupId</tt>.
         *
         * @param group value to be assigned to property groupId
         */
        public void setGroup(String group) {
            this.group = group;
        }
        
        /**
         * Getter method for property <tt>md5</tt>.
         *
         * @return property value of md5
         */
        public String getMd5() {
            return md5;
        }
        
        /**
         * Setter method for property <tt>md5</tt>.
         *
         * @param md5 value to be assigned to property md5
         */
        public void setMd5(String md5) {
            this.md5 = md5;
        }
        
        /**
         * Getter method for property <tt>dataId</tt>.
         *
         * @return property value of dataId
         */
        public String getDataId() {
            return dataId;
        }
        
        /**
         * Setter method for property <tt>dataId</tt>.
         *
         * @param dataId value to be assigned to property dataId
         */
        public void setDataId(String dataId) {
            this.dataId = dataId;
        }
        
        /**
         * Getter method for property <tt>tenant</tt>.
         *
         * @return property value of tenant
         */
        public String getTenant() {
            return tenant;
        }
        
        /**
         * Setter method for property <tt>tenant</tt>.
         *
         * @param tenant value to be assigned to property tenant
         */
        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }
}
