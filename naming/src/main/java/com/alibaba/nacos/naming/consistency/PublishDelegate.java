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
package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ap.ApConsistencyService;
import com.alibaba.nacos.naming.consistency.cp.CpConsistencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publish execute delegate
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @since 1.0.0
 */
@Component
public class PublishDelegate implements ConsistencyService {

    @Autowired
    private CpConsistencyService cpConsistencyService;

    @Autowired
    private ApConsistencyService apConsistencyService;

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
