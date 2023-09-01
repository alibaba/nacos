package com.alibaba.nacos.lock.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * lockRequest handler test.
 *
 * @author 985492783@qq.com
 * @date 2023/9/1 10:00
 */
@RunWith(MockitoJUnitRunner.class)
public class LockRequestHandlerTest {
    
    @Mock
    private LockOperationService lockOperationService;
    
    private LockRequestHandler lockRequestHandler;
    
    @Test
    public void testAcquireHandler() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance("key", 1L);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        Mockito.when(lockOperationService.lock(lockInstance)).thenReturn(true);
        LockOperationResponse response = lockRequestHandler.handle(request, null);
        Assert.assertTrue((Boolean) response.getResult());
    }
    
    @Test
    public void testReleaseHandler() throws NacosException {
        lockRequestHandler = new LockRequestHandler(lockOperationService);
        
        LockInstance lockInstance = new LockInstance("key", 1L);
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(lockInstance);
        request.setLockOperationEnum(LockOperationEnum.RELEASE);
        Mockito.when(lockOperationService.unLock(lockInstance)).thenReturn(true);
        LockOperationResponse response = lockRequestHandler.handle(request, null);
        Assert.assertTrue((Boolean) response.getResult());
    }
}
