/*
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
 */

package com.alibaba.nacos.api.remote.ability;

import java.io.Serializable;
import java.util.Objects;

/**
 * remote abilities of nacos server.
 *
 * @author liuzunfei
 * @version $Id: ServerRemoteAbility.java, v 0.1 2021年01月24日 00:09 AM liuzunfei Exp $
 */
public class ServerRemoteAbility implements Serializable {

    private static final long serialVersionUID = -3069795759506428390L;

    /**
     * if support remote connection.
     */
    private boolean supportRemoteConnection;
    
    /**
     * if support grpc report.
     */
    private boolean grpcReportEnabled = true;
    
    public boolean isSupportRemoteConnection() {
        return this.supportRemoteConnection;
    }
    
    public void setSupportRemoteConnection(boolean supportRemoteConnection) {
        this.supportRemoteConnection = supportRemoteConnection;
    }
    
    public boolean isGrpcReportEnabled() {
        return grpcReportEnabled;
    }
    
    public void setGrpcReportEnabled(boolean grpcReportEnabled) {
        this.grpcReportEnabled = grpcReportEnabled;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServerRemoteAbility that = (ServerRemoteAbility) o;
        return supportRemoteConnection == that.supportRemoteConnection && grpcReportEnabled == that.grpcReportEnabled;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(supportRemoteConnection, grpcReportEnabled);
    }
}
