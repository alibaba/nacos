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

package com.alibaba.nacos.client.identify;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * StsCredential.
 *
 * @author xiweng.yy
 */
public class StsCredential {
    
    @JsonProperty(value = "AccessKeyId")
    private String accessKeyId;
    
    @JsonProperty(value = "AccessKeySecret")
    private String accessKeySecret;
    
    @JsonProperty(value = "Expiration")
    private Date expiration;
    
    @JsonProperty(value = "SecurityToken")
    private String securityToken;
    
    @JsonProperty(value = "LastUpdated")
    private Date lastUpdated;
    
    @JsonProperty(value = "Code")
    private String code;
    
    public String getAccessKeyId() {
        return accessKeyId;
    }
    
    public String getAccessKeySecret() {
        return accessKeySecret;
    }
    
    public String getSecurityToken() {
        return securityToken;
    }
    
    public Date getExpiration() {
        return expiration;
    }
    
    public Date getLastUpdated() {
        return lastUpdated;
    }
    
    public String getCode() {
        return code;
    }
    
    @Override
    public String toString() {
        return "STSCredential{" + "accessKeyId='" + accessKeyId + '\'' + ", accessKeySecret='" + accessKeySecret
                + '\'' + ", expiration=" + expiration + ", securityToken='" + securityToken + '\''
                + ", lastUpdated=" + lastUpdated + ", code='" + code + '\'' + '}';
    }
}
