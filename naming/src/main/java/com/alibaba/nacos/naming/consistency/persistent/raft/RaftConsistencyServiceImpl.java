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
package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.stereotype.Service;

/**
 * Use simplified Raft protocol to maintain the consistency status of Nacos cluster.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Service
public class RaftConsistencyServiceImpl implements PersistentConsistencyService {

    @Override
    public void put(String key, Record value) throws NacosException {

    }

    @Override
    public void remove(String key) throws NacosException {

    }

    @Override
    public Record get(String key) throws NacosException {
        return null;
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {

    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {

    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
