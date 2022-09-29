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

package com.alibaba.nacos.naming.core.v2.client;

public class MockAbstractClient extends AbstractClient {
    
    public MockAbstractClient(Long revision) {
        super(revision);
    }
    
    @Override
    public String getClientId() {
        return "-1";
    }
    
    @Override
    public boolean isEphemeral() {
        return false;
    }
    
    @Override
    public boolean isExpire(long currentTime) {
        return false;
    }
}
