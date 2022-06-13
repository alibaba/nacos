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

package com.alibaba.nacos.common.remote;

/**
 * ConnectionType.
 *
 * @author liuzunfei
 * @version $Id: ConnectionType.java, v 0.1 2020年07月13日 7:15 PM liuzunfei Exp $
 */
public enum ConnectionType {
    
    /**
     * gRPC connection.
     */
    GRPC("GRPC", "Grpc Connection");
    
    String type;
    
    String name;
    
    public static ConnectionType getByType(String type) {
        ConnectionType[] values = ConnectionType.values();
        for (ConnectionType connectionType : values) {
            if (connectionType.getType().equals(type)) {
                return connectionType;
            }
        }
        return null;
    }
    
    ConnectionType(String type, String name) {
        this.type = type;
        this.name = name;
    }
    
    /**
     * Getter method for property <tt>type</tt>.
     *
     * @return property value of type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Setter method for property <tt>type</tt>.
     *
     * @param type value to be assigned to property type
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Getter method for property <tt>name</tt>.
     *
     * @return property value of name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Setter method for property <tt>name</tt>.
     *
     * @param name value to be assigned to property name
     */
    public void setName(String name) {
        this.name = name;
    }
}
