/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.List;
import java.util.Objects;

/**
 * AgentAuthentication.
 *
 * @author KiteSoar
 */
public class AgentAuthentication {
    
    private List<String> schemes;
    
    private String credentials;
    
    public List<String> getSchemes() {
        return schemes;
    }
    
    public void setSchemes(List<String> schemes) {
        this.schemes = schemes;
    }
    
    public String getCredentials() {
        return credentials;
    }
    
    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentAuthentication that = (AgentAuthentication) o;
        return Objects.equals(schemes, that.schemes) && Objects.equals(credentials, that.credentials);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(schemes, credentials);
    }
}
