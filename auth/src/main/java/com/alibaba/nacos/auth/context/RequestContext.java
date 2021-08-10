package com.alibaba.nacos.auth.context;

import java.util.HashMap;
import java.util.Map;

/**
 * Auth Context.
 *
 * @author Wuyfee
 */
public class RequestContext {
    
    /**
     * get context from request.
     */
    private final Map<String, Object> param = new HashMap<String, Object>();
    
    /**
     * get key from context.
     * @param key key of request
     * @return value of param key
     */
    public Object getParameter(String key) {
        return param.get(key); }
    
    /**
     * put key and value to param.
     * @param key key of request
     * @param value value of request's key
     */
    public void setParameter(String key, Object value) {
        param.put(key, value); }
}
