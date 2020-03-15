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

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * @author kkyeer
 * @Description: A application contains services with same ip and port
 * @Date:Created in 16:14 2-23
 * @Modified By:
 */
public class Application implements Serializable {
    private String ip;
    private int port;
    private int instanceCount;

    public Application(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public Application setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public int getPort() {
        return port;
    }

    public Application setPort(int port) {
        this.port = port;
        return this;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o){ return true;}
        if (!(o instanceof Application)){ return false;}
        Application that = (Application) o;
        return getPort() == that.getPort() &&
            getIp().equals(that.getIp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIp(), getPort());
    }
}
