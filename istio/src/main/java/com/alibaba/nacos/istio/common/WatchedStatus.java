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

/**
 * @author special.fy
 */
public class WatchedStatus {

    private String type;

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
}
