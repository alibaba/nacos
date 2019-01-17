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
package com.alibaba.nacos.naming.consistency.ephemeral.partialrenew;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@Component
public class RenewConsistencyServiceImpl implements EphemeralConsistencyService {

    @Override
    public void put(Object key, Object value) throws NacosException {

    }

    @Override
    public void remove(Object key) throws NacosException {

    }

    @Override
    public Object get(Object key) throws NacosException {
        return null;
    }

    @Override
    public void listen(Object key, DataListener listener) throws NacosException {

    }

    @Override
    public void unlisten(Object key, DataListener listener) throws NacosException {

    }

    @Override
    public boolean isResponsible(Object key) {
        return false;
    }

    @Override
    public String getResponsibleServer(Object key) {
        return null;
    }
}
