package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface ConsistencyService {
    void publish(Object key, Object value) throws NacosException;
    void sync(Object key) throws NacosException;
}
