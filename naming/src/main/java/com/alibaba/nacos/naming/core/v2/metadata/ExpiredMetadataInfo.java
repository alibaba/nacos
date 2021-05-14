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

package com.alibaba.nacos.naming.core.v2.metadata;

import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Date;
import java.util.Objects;

/**
 * Expired metadata information.
 * <p>
 * When an original object like service or instance be remove, the metadata need to be removed.
 * </p>
 *
 * @author xiweng.yy
 */
public class ExpiredMetadataInfo {
    
    private final Service service;
    
    private final String metadataId;
    
    private final long createTime;
    
    private ExpiredMetadataInfo(Service service, String metadataId) {
        this.service = service;
        this.metadataId = metadataId;
        this.createTime = System.currentTimeMillis();
    }
    
    public static ExpiredMetadataInfo newExpiredServiceMetadata(Service service) {
        return new ExpiredMetadataInfo(service, null);
    }
    
    public static ExpiredMetadataInfo newExpiredInstanceMetadata(Service service, String metadataId) {
        return new ExpiredMetadataInfo(service, metadataId);
    }
    
    public Service getService() {
        return service;
    }
    
    public String getMetadataId() {
        return metadataId;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpiredMetadataInfo)) {
            return false;
        }
        ExpiredMetadataInfo that = (ExpiredMetadataInfo) o;
        return Objects.equals(service, that.service) && Objects.equals(metadataId, that.metadataId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(service, metadataId);
    }
    
    @Override
    public String toString() {
        return "ExpiredMetadataInfo{" + "service=" + service + ", metadataId='" + metadataId + '\'' + ", createTime="
                + new Date(createTime) + '}';
    }
}
