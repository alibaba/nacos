package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.model.RestResult;

/**
 * Abstract callback.
 *
 * @author mai.jh
 */
public abstract class AbstractCallback<T> implements Callback<T> {
    
    /**
     * receive method must be implemented concretely.
     *
     * @param result {@link RestResult}
     */
    @Override
    public abstract void onReceive(RestResult<T> result);
    
    @Override
    public void onError(Throwable throwable) {
    
    }
    
    @Override
    public void onCancel() {
    
    }
}
