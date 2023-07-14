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

package com.alibaba.nacos.api.lock.model;

import java.io.Serializable;

/**
 * lock instance.
 * @author 985492783@qq.com
 * @date 2023/6/28 2:46
 */
public class LockInstance implements Serializable {
    private static final long serialVersionUID = -53506310567291979L;

    /**
     * instance ip.
     */
    private String ip;

    /**
     * instance port.
     */
    private int port;
    
    public LockInstance() {
    
    }
    
    public LockInstance(String ip, int port) {
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
}
