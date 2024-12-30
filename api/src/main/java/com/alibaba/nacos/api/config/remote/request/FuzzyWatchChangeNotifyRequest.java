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

package com.alibaba.nacos.api.config.remote.request;

/**
 * Represents a request to notify changes in a fuzzy listening configuration.
 *
 * <p>This request is used to notify clients about changes in configurations that match fuzzy listening patterns.
 *
 * @author stone-98
 * @date 2024/3/13
 */
public class FuzzyWatchChangeNotifyRequest extends AbstractFuzzyWatchNotifyRequest {
    
    /**
     * The tenant of the configuration that has changed.
     */
    private String namespace;
    
    /**
     * The group of the configuration that has changed.
     */
    private String group;
    
    /**
     * The data ID of the configuration that has changed.
     */
    private String dataId;
    
    /**
     * Indicates whether the configuration exists or not.
     */
    private boolean isExist;
    
    /**
     * Constructs an empty FuzzyListenNotifyChangeRequest.
     */
    public FuzzyWatchChangeNotifyRequest() {
    }
    
    /**
     * Constructs a FuzzyListenNotifyChangeRequest with the specified parameters.
     *
     * @param namespace  The tenant of the configuration that has changed
     * @param group   The group of the configuration that has changed
     * @param dataId  The data ID of the configuration that has changed
     * @param isExist Indicates whether the configuration exists or not
     */
    public FuzzyWatchChangeNotifyRequest(String namespace, String group, String dataId, boolean isExist) {
        this.namespace = namespace;
        this.group = group;
        this.dataId = dataId;
        this.isExist = isExist;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
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
    
    public boolean isExist() {
        return isExist;
    }
    
    public void setExist(boolean exist) {
        isExist = exist;
    }
    
    /**
     * Returns a string representation of the FuzzyListenNotifyChangeRequest.
     *
     * @return A string representation of the request
     */
    @Override
    public String toString() {
        return "FuzzyListenNotifyChangeRequest{" + "tenant='" + namespace + '\'' + ", group='" + group + '\''
                + ", dataId='" + dataId + '\'' + ", isExist=" + isExist + '}';
    }
    
}
