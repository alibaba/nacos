package com.alibaba.nacos.naming.consistency.cp;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.ConsistencyService;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public interface CpConsistencyService extends ConsistencyService {
    void onPublish(Object key, Object value) throws NacosException;
}
