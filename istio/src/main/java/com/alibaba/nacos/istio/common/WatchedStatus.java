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

package com.alibaba.nacos.istio.common;

import com.google.protobuf.ProtocolStringList;

/**
 * @author special.fy
 */
public class WatchedStatus {

    private String type;
    
    private boolean lastFull;
    
    private boolean lastAckOrNack;
    
    private ProtocolStringList lastSubscribe;
    
    private ProtocolStringList lastUnSubscribe;

    private String latestVersion;

    private String latestNonce;

    private String ackedVersion;

    private String ackedNonce;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getLatestNonce() {
        return latestNonce;
    }

    public void setLatestNonce(String latestNonce) {
        this.latestNonce = latestNonce;
    }

    public String getAckedVersion() {
        return ackedVersion;
    }

    public void setAckedVersion(String ackedVersion) {
        this.ackedVersion = ackedVersion;
    }

    public String getAckedNonce() {
        return ackedNonce;
    }

    public void setAckedNonce(String ackedNonce) {
        this.ackedNonce = ackedNonce;
    }
    
    public boolean isLastFull() {
        return lastFull;
    }
    
    public void setLastFull(boolean lastFull) {
        this.lastFull = lastFull;
    }
    
    public boolean isLastAckOrNack() {
        return lastAckOrNack;
    }
    
    public void setLastAckOrNack(boolean lastAckOrNack) {
        this.lastAckOrNack = lastAckOrNack;
    }
    
    public ProtocolStringList getLastSubscribe() {
        return lastSubscribe;
    }
    
    public void setLastSubscribe(ProtocolStringList lastSubscribe) {
        this.lastSubscribe = lastSubscribe;
    }
    
    public ProtocolStringList getLastUnSubscribe() {
        return lastUnSubscribe;
    }
    
    public void setLastUnSubscribe(ProtocolStringList lastUnSubscribe) {
        this.lastUnSubscribe = lastUnSubscribe;
    }
}
