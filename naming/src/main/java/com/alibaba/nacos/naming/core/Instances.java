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
package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.JSON;

import java.util.Map;

/**
 * @author nkorange
 */
public class Instances {

    private Map<String, Instance> instanceMap;

    public Map<String, Instance> getInstanceMap() {
        return instanceMap;
    }

    public void setInstanceMap(Map<String, Instance> instanceMap) {
        this.instanceMap = instanceMap;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
