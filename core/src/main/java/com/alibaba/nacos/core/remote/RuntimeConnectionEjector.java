/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

/**
 * runtime connection ejector.
 *
 * @author shiyiyue
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RuntimeConnectionEjector {
    
    /**
     * 4 times of client keep alive.
     */
    public static final long KEEP_ALIVE_TIME = 20000L;
    
    /**
     * current loader adjust count,only effective once,use to re balance.
     */
    private int loadClient = -1;
    
    String redirectAddress = null;
    
    protected ConnectionManager connectionManager;
    
    public RuntimeConnectionEjector() {
    }
    
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public void setConnectionManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    /**
     * eject runtime connection.
     */
    public abstract void doEject();
    
    public int getLoadClient() {
        return loadClient;
    }
    
    public void setLoadClient(int loadClient) {
        this.loadClient = loadClient;
    }
    
    public String getRedirectAddress() {
        return redirectAddress;
    }
    
    public void setRedirectAddress(String redirectAddress) {
        this.redirectAddress = redirectAddress;
    }
    
    /**
     * get name.
     *
     * @return
     */
    public abstract String getName();
}
