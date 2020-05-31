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
package com.alibaba.nacos.common.lifecycle;

/**
 * Define serveral lifecycle state.
 *
 * @author zongtanghu
 */
public enum LifeCycleState {

    /**
     * The service's current state is STOPPED.
     */
    STOPPED("STOPPED"),

    /**
     * The service's current state is STARTING.
     */
    STARTING("STARTING"),

    /**
     * The service's current state is STARTED.
     */
    STARTED("STARTED"),

    /**
     * The service's current state is STOPPING.
     */
    STOPPING("STOPPING"),

    /**
     * The service's current state is FAILED.
     */
    FAILED("FAILED");

    private String name;

    LifeCycleState(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "LifeCycleState{" +
            "name='" + name + '\'' +
            '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
