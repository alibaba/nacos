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
 *
 */

package com.alibaba.nacos.istio.model;

/**.
 * @author RocketEngine26
 * @date 2022/8/20 09:30
 */
public class PushChange {
    private String name;
    
    private ChangeType changeType;
    
    public PushChange(String name, ChangeType changeType) {
        this.name = name;
        this.changeType = changeType;
    }
    
    public enum ChangeType {
        //Online
        UP,
    
        //Data Change
        DATA,
        
        //Offline
        DOWN;
    }
    
    public String getName() {
        return name;
    }
    
    public ChangeType getChangeType() {
        return changeType;
    }
}
