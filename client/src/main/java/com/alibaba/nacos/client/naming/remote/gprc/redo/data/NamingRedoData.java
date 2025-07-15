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

package com.alibaba.nacos.client.naming.remote.gprc.redo.data;

import com.alibaba.nacos.client.redo.data.RedoData;

import java.util.Objects;

/**
 * Nacos naming redo data.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class NamingRedoData<T> extends RedoData<T> {
    
    private final String serviceName;
    
    private final String groupName;
    
    protected NamingRedoData(String serviceName, String groupName) {
        super();
        this.serviceName = serviceName;
        this.groupName = groupName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NamingRedoData<?> redoData = (NamingRedoData<?>) o;
        return super.equals(o) && serviceName.equals(redoData.serviceName) && groupName.equals(redoData.groupName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), serviceName, groupName);
    }
}
