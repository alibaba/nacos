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

package com.alibaba.nacos.persistence.repository.embedded.operate;

import java.util.Objects;

public class MockConfigInfo {
    
    private long id;
    
    private String dataId;
    
    private String group;
    
    private String content;
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MockConfigInfo)) {
            return false;
        }
        MockConfigInfo that = (MockConfigInfo) o;
        return id == that.id && Objects.equals(dataId, that.dataId) && Objects.equals(group, that.group) && Objects
                .equals(content, that.content);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, dataId, group, content);
    }
}
