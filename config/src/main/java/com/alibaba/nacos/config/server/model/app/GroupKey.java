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

package com.alibaba.nacos.config.server.model.app;

import com.alibaba.nacos.config.server.utils.GroupKey2;

/**
 * GroupKey.
 *
 * @author Nacos
 */
public class GroupKey extends GroupKey2 {
    
    private String dataId;
    
    private String group;
    
    public GroupKey(String dataId, String group) {
        this.dataId = dataId;
        this.group = group;
    }
    
    public GroupKey(String groupKeyString) {
        String[] groupKeys = parseKey(groupKeyString);
        this.dataId = groupKeys[0];
        this.group = groupKeys[1];
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
    
    @Override
    public String toString() {
        return dataId + "+" + group;
    }
    
    public String getGroupkeyString() {
        return getKey(dataId, group);
    }
    
    //TODO : equal as we use Set
    
}
