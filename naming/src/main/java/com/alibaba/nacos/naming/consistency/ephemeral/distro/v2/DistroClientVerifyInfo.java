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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import java.io.Serializable;

/**
 * Information for verifying client.
 *
 * @author xiweng.yy
 */
public class DistroClientVerifyInfo implements Serializable {
    
    private static final long serialVersionUID = 2223964944788737629L;
    
    private String clientId;
    
    private long revision;
    
    public DistroClientVerifyInfo() {
    }
    
    public DistroClientVerifyInfo(String clientId, long revision) {
        this.clientId = clientId;
        this.revision = revision;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public long getRevision() {
        return revision;
    }
    
    public void setRevision(long revision) {
        this.revision = revision;
    }
}
