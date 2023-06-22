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

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;
import java.util.Objects;

/**
 * batch instance redo service.
 *
 * @author <a href="mailto:chenhao26@xiaomi.com">chenhao26</a>
 */
public class BatchInstanceRedoData extends InstanceRedoData {
    
    List<Instance> instances;
    
    public List<Instance> getInstances() {
        return instances;
    }
    
    public void setInstances(List<Instance> instances) {
        this.instances = instances;
    }
    
    protected BatchInstanceRedoData(String serviceName, String groupName) {
        super(serviceName, groupName);
    }

    /**
     * build BatchInstanceRedoData.
     *
     * @param serviceName service name
     * @param groupName   group name
     * @param instances   instances
     * @return build BatchInstanceRedoData
     */
    public static BatchInstanceRedoData build(String serviceName, String groupName, List<Instance> instances) {
        BatchInstanceRedoData result = new BatchInstanceRedoData(serviceName, groupName);
        result.setInstances(instances);
        return result;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BatchInstanceRedoData)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        BatchInstanceRedoData redoData = (BatchInstanceRedoData) o;
        return Objects.equals(instances, redoData.instances);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), instances);
    }
}
