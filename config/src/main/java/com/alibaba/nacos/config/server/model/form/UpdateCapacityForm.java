/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.api.model.NacosForm;
import org.springframework.http.HttpStatus;

/**
 * This form is used to update capacity-related configurations.
 *
 * @author Nacos
 */
public class UpdateCapacityForm implements NacosForm  {
    
    private static final long serialVersionUID = -1912905276914026856L;
    
    private String groupName;
    
    private String namespaceId;
    
    private Integer quota;
    
    private Integer maxSize;
    
    private Integer maxAggrCount;
    
    private Integer maxAggrSize;
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public Integer getQuota() {
        return quota;
    }
    
    public void setQuota(Integer quota) {
        this.quota = quota;
    }
    
    public Integer getMaxSize() {
        return maxSize;
    }
    
    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }
    
    public Integer getMaxAggrCount() {
        return maxAggrCount;
    }
    
    public void setMaxAggrCount(Integer maxAggrCount) {
        this.maxAggrCount = maxAggrCount;
    }
    
    public Integer getMaxAggrSize() {
        return maxAggrSize;
    }
    
    public void setMaxAggrSize(Integer maxAggrSize) {
        this.maxAggrSize = maxAggrSize;
    }
    
    @Override
    public void validate() throws NacosApiException {
        if (quota == null && maxSize == null && maxAggrCount == null && maxAggrSize == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "The parameters quota, maxSize, maxAggrCount, maxAggrSize cannot be empty at the same time");
        }
    }
    
    /**
     * Check namespaceId and groupName.
     *
     * @param capacityService capacity service
     * @throws NacosApiException NacosApiException
     */
    public void checkNamespaceIdAndGroupName(CapacityService capacityService) throws NacosApiException {
        if (StringUtils.isBlank(groupName) && StringUtils.isBlank(namespaceId)) {
            capacityService.initAllCapacity();
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "At least one of the parameters (groupName or namespaceId) must be provided");
        }
    }
}