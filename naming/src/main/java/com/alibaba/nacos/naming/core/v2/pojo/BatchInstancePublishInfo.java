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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * the client support service registers multiple instance entity classes.
 *
 * @author : ChenHao26
 * @ClassName: BatchInstancePublishInfo
 * @Date: 2022/4/21 16:19
 */
public class BatchInstancePublishInfo extends InstancePublishInfo {
    
    /**
     * save all the service instance data transmitted from the client.
     */
    private List<InstancePublishInfo> instancePublishInfos;
    
    public List<InstancePublishInfo> getInstancePublishInfos() {
        return instancePublishInfos;
    }
    
    public void setInstancePublishInfos(List<InstancePublishInfo> instancePublishInfos) {
        this.instancePublishInfos = instancePublishInfos;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BatchInstancePublishInfo)) {
            return false;
        }
        BatchInstancePublishInfo that = (BatchInstancePublishInfo) o;
        return CollectionUtils.isEqualCollection(this.getInstancePublishInfos(), that.getInstancePublishInfos());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(CollectionUtils.getCardinalityMap(instancePublishInfos));
    }
}

