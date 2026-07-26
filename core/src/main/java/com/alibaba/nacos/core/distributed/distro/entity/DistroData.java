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

package com.alibaba.nacos.core.distributed.distro.entity;

import com.alibaba.nacos.consistency.DataOperation;

/**
 * Distro data.
 *
 * @author xiweng.yy
 */
public class DistroData {
    
    private DistroKey distroKey;
    
    private DataOperation type;
    
    private byte[] content;
    
    public DistroData() {
    }
    
    public DistroData(DistroKey distroKey, byte[] content) {
        this.distroKey = distroKey;
        this.content = content;
    }
    
    public DistroKey getDistroKey() {
        return distroKey;
    }
    
    public void setDistroKey(DistroKey distroKey) {
        this.distroKey = distroKey;
    }
    
    public DataOperation getType() {
        return type;
    }
    
    public void setType(DataOperation type) {
        this.type = type;
    }
    
    public byte[] getContent() {
        return content;
    }
    
    public void setContent(byte[] content) {
        this.content = content;
    }
}
