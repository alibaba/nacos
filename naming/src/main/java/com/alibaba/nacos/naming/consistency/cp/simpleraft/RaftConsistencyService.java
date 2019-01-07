package com.alibaba.nacos.naming.consistency.cp.simpleraft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.cp.CpConsistencyService;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class RaftConsistencyService implements CpConsistencyService {

    @Override
    public void publish(Object key, Object value) throws NacosException {

    }

    @Override
    public void sync(Object key) throws NacosException {

    }

    @Override
    public void onPublish(Object key, Object value) throws NacosException {

    }
}
