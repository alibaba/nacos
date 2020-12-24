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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;

import java.util.ArrayList;
import java.util.List;

/**
 * ConfigChangeBatchListenResponse.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeBatchListenResponse.java, v 0.1 2020年07月14日 3:07 PM liuzunfei Exp $
 */
public class ConfigChangeBatchListenResponse extends Response {
    
    List<ConfigContext> changedConfigs = new ArrayList<ConfigContext>();
    
    public ConfigChangeBatchListenResponse() {
    }
    
    /**
     * add changed config.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     */
    public void addChangeConfig(String dataId, String group, String tenant) {
        ConfigContext configContext = new ConfigContext();
        configContext.dataId = dataId;
        configContext.group = group;
        configContext.tenant = tenant;
        changedConfigs.add(configContext);
    }
    
    /**
     * Getter method for property <tt>changedConfigs</tt>.
     *
     * @return property value of changedConfigs
     */
    public List<ConfigContext> getChangedConfigs() {
        return changedConfigs;
    }
    
    /**
     * Setter method for property <tt>changedConfigs</tt>.
     *
     * @param changedConfigs value to be assigned to property changedConfigs
     */
    public void setChangedConfigs(List<ConfigContext> changedConfigs) {
        this.changedConfigs = changedConfigs;
    }
    
    /**
     * build fail response.
     *
     * @param errorMessage errorMessage.
     * @return response.
     */
    public static ConfigChangeBatchListenResponse buildFailResponse(String errorMessage) {
        ConfigChangeBatchListenResponse response = new ConfigChangeBatchListenResponse();
        response.setResultCode(ResponseCode.FAIL.getCode());
        response.setMessage(errorMessage);
        return response;
    }
    
    public static class ConfigContext {
        
        String group;
        
        String dataId;
        
        String tenant;
        
        public ConfigContext() {
        
        }
        
        /**
         * Getter method for property <tt>groupId</tt>.
         *
         * @return property value of groupId
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
        
        @Override
        public String toString() {
            return "ConfigContext{" + "group='" + group + '\'' + ", dataId='" + dataId + '\'' + ", tenant='" + tenant
                    + '\'' + '}';
        }
    }
    
}
