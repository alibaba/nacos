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

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;

/**
 * Distro http received data.
 *
 * <p>
 * Apply for old distro http api. The data content has been deserialize by spring mvc, so there is no need to
 * deserialize again.
 * </p>
 *
 * @author xiweng.yy
 */
public class DistroHttpData extends DistroData {
    
    private Object deserializedContent;
    
    public DistroHttpData(DistroKey distroKey, Object deserializedContent) {
        setDistroKey(distroKey);
        this.deserializedContent = deserializedContent;
    }
    
    public Object getDeserializedContent() {
        return deserializedContent;
    }
    
    public void setDeserializedContent(Object deserializedContent) {
        this.deserializedContent = deserializedContent;
    }
}
