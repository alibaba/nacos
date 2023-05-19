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

package com.alibaba.nacos.api.remote.request;

/**
 * reload long connection request.
 *
 * @author liuzunfei
 * @version $Id: ServerReloadRequest.java, v 0.1 2020年11月09日 4:35 PM liuzunfei Exp $
 */
public class ServerReloadRequest extends InternalRequest {
    
    int reloadCount = 0;
    
    String reloadServer;
    
    /**
     * Getter method for property <tt>reloadCount</tt>.
     *
     * @return property value of reloadCount
     */
    public int getReloadCount() {
        return reloadCount;
    }
    
    /**
     * Setter method for property <tt>reloadCount</tt>.
     *
     * @param reloadCount value to be assigned to property reloadCount
     */
    public void setReloadCount(int reloadCount) {
        this.reloadCount = reloadCount;
    }
    
    public String getReloadServer() {
        return reloadServer;
    }
    
    public void setReloadServer(String reloadServer) {
        this.reloadServer = reloadServer;
    }
}
