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

package com.alibaba.nacos.api.config.remote.request.cluster;

import com.alibaba.nacos.api.config.remote.request.AbstractConfigRequest;

/**
 * config change sync request on clusters.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeClusterSyncRequest.java, v 0.1 2020年08月11日 4:30 PM liuzunfei Exp $
 */
public class ConfigChangeClusterSyncRequest extends AbstractConfigRequest {
    
    String dataId;
    
    String group;
    
    String tenant;
    
    String tag;
    
    long lastModified;
    
    boolean isBeta;
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean beta) {
        isBeta = beta;
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
     * Getter method for property <tt>group</tt>.
     *
     * @return property value of group
     */
    public String getGroup() {
        return group;
    }
    
    /**
     * Setter method for property <tt>group</tt>.
     *
     * @param group value to be assigned to property group
     */
    public void setGroup(String group) {
        this.group = group;
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
    
    /**
     * Getter method for property <tt>tag</tt>.
     *
     * @return property value of tag
     */
    public String getTag() {
        return tag;
    }
    
    /**
     * Setter method for property <tt>tag</tt>.
     *
     * @param tag value to be assigned to property tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    /**
     * Getter method for property <tt>lastModified</tt>.
     *
     * @return property value of lastModified
     */
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Setter method for property <tt>lastModified</tt>.
     *
     * @param lastModified value to be assigned to property lastModified
     */
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
}
