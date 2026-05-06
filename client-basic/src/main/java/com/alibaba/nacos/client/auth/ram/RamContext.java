/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.ram;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Aliyun RAM context.
 *
 * @author xiweng.yy
 */
public class RamContext {
    
    private String accessKey;
    
    private String secretKey;
    
    private String ramRoleName;
    
    private String regionId;
    
    public String getAccessKey() {
        return accessKey;
    }
    
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }
    
    public String getSecretKey() {
        return secretKey;
    }
    
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public String getRamRoleName() {
        return ramRoleName;
    }
    
    public void setRamRoleName(String ramRoleName) {
        this.ramRoleName = ramRoleName;
    }
    
    public String getRegionId() {
        return regionId;
    }
    
    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }
    
    public boolean validate() {
        return StringUtils.isNotBlank(ramRoleName) || StringUtils.isNotBlank(accessKey) && StringUtils
                .isNotBlank(secretKey);
    }
}
