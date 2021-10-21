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

package com.alibaba.nacos.core.model.vo;

/**
 * Id generator vo.
 *
 * @author wuzhiguo
 */
public class IdGeneratorVO {
    
    private String resource;
    
    private IdInfo info;
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public IdInfo getInfo() {
        return info;
    }
    
    public void setInfo(IdInfo info) {
        this.info = info;
    }
    
    public static class IdInfo {
        
        private Long currentId;
    
        private Long workerId;
    
        public Long getCurrentId() {
            return currentId;
        }
    
        public void setCurrentId(Long currentId) {
            this.currentId = currentId;
        }
    
        public Long getWorkerId() {
            return workerId;
        }
    
        public void setWorkerId(Long workerId) {
            this.workerId = workerId;
        }
    
        @Override
        public String toString() {
            return "IdInfo{" + "currentId=" + currentId + ", workerId=" + workerId + '}';
        }
    }
    
    @Override
    public String toString() {
        return "IdGeneratorVO{" + "resource='" + resource + '\'' + ", info=" + info + '}';
    }
}
