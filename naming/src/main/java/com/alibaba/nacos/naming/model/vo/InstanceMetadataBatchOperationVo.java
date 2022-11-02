/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.model.vo;

import java.io.Serializable;
import java.util.List;

/**
 * InstanceUpdateVo.
 * @author dongyafei
 * @date 2022/9/7
 */
public class InstanceMetadataBatchOperationVo implements Serializable {
    
    private static final long serialVersionUID = -5793871911227035729L;
    
    private List<String> updated;
    
    public InstanceMetadataBatchOperationVo() {
    }
    
    public InstanceMetadataBatchOperationVo(List<String> updated) {
        this.updated = updated;
    }
    
    public List<String> getUpdated() {
        return updated;
    }
    
    public void setUpdated(List<String> updated) {
        this.updated = updated;
    }
}
