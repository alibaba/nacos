/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.lock.model;

import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Objects;
import java.util.Set;

/**
 * lock service.
 *
 * @author 985492783@qq.com
 * @date 2023/6/28 2:36
 */
public class Service {
    
    /**
     * service ip.
     */
    private String ip;
    
    /**
     * service port.
     */
    private int port;
    
    private volatile Set<String> keysSet;
    
    public Service(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public Set<String> getKeysSet() {
        return keysSet;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Service service = (Service) o;
        return port == service.port && Objects.equals(ip, service.ip);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
    
    /**
     * add key set.
     * @param key key
     */
    public void addKey(String key) {
        if (keysSet == null) {
            synchronized (this) {
                if (keysSet == null) {
                    keysSet = new ConcurrentHashSet<>();
                }
            }
        }
        keysSet.add(key);
    }
    
    public void removeKey(String key) {
        keysSet.remove(key);
    }
}
