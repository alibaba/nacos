package com.alibaba.nacos.client.auth.result;

import java.util.Map;
import java.util.Properties;

public interface RequestManager {
    
    /**
     * send request to server and get result.
     *
     * @param properties request properties.
     * @return response informationn.
     */
    public Map<String, String> getResponse(Properties properties);

}
