package com.alibaba.nacos.lock.service.impl;

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.constant.PropertiesConstant;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.raft.request.MutexLockRequest;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static com.alibaba.nacos.lock.constant.Constants.LOCK_ACQUIRE_SERVICE_GROUP_V2;

/**
 * @author 985492783@qq.com
 * @date 2023/8/30 14:01
 */
@RunWith(MockitoJUnitRunner.class)
public class LockOperationServiceImplTest {
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private static CPProtocol cpProtocol;
    
    @Mock
    private static LockManager lockManager;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private LockOperationServiceImpl lockOperationService;
    
    private static MockedStatic<ApplicationUtils> mockedStatic;
    
    private static MockedStatic<EnvUtil> mockedEnv;
    
    @BeforeClass
    public static void setUp() {
        mockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        mockedEnv = Mockito.mockStatic(EnvUtil.class);
        mockedEnv.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(ins -> ins.getArgument(2));
    }
    
    public void buildService() {
        mockedStatic.when(() -> ApplicationUtils.getBean(ProtocolManager.class)).thenReturn(protocolManager);
        Mockito.when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        lockOperationService = Mockito.spy(new LockOperationServiceImpl(lockManager));
    }
    
    @Test
    public void testGroup() {
        buildService();
        
        Assert.assertEquals(lockOperationService.group(), LOCK_ACQUIRE_SERVICE_GROUP_V2);
    }
    
    @Test
    public void testLockExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInstance lockInstance = mutexLockRequest.getLockInstance();
            Assert.assertEquals(lockInstance.getLockType(), LockConstants.NACOS_LOCK_TYPE);
            Assert.assertEquals(lockInstance.getExpireTimestamp(),
                    timestamp + PropertiesConstant.DEFAULT_AUTO_EXPIRE_TIME);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", -1L);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testLockSimple() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInstance lockInstance = mutexLockRequest.getLockInstance();
            Assert.assertEquals(lockInstance.getLockType(), LockConstants.NACOS_LOCK_TYPE);
            Assert.assertEquals(lockInstance.getExpireTimestamp(), timestamp + 1_000L);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", 1_000L);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testLockMaxExpire() throws Exception {
        buildService();
        
        long timestamp = 1 << 10;
        Mockito.when(lockOperationService.getNowTimestamp()).thenReturn(timestamp);
        Mockito.when(cpProtocol.write(Mockito.any())).thenAnswer((i) -> {
            WriteRequest request = i.getArgument(0);
            MutexLockRequest mutexLockRequest = serializer.deserialize(request.getData().toByteArray());
            LockInstance lockInstance = mutexLockRequest.getLockInstance();
            Assert.assertEquals(lockInstance.getLockType(), LockConstants.NACOS_LOCK_TYPE);
            Assert.assertEquals(lockInstance.getExpireTimestamp(), timestamp + PropertiesConstant.MAX_AUTO_EXPIRE_TIME);
            
            return getResponse();
        });
        LockInstance lockInstance = new LockInstance("key", PropertiesConstant.MAX_AUTO_EXPIRE_TIME + 1_000L);
        lockOperationService.lock(lockInstance);
    }
    
    @Test
    public void testOnApply() {
        buildService();
        Mockito.when(lockManager.getMutexLock(LockConstants.NACOS_LOCK_TYPE, "key")).thenReturn(new MutexAtomicLock("key"));
        
        WriteRequest request = getRequest(LockOperationEnum.ACQUIRE);
        Response response = lockOperationService.onApply(request);
        Assert.assertTrue(response.getSuccess());
        Assert.assertTrue(serializer.deserialize(response.getData().toByteArray()));
    }
    
    public WriteRequest getRequest(LockOperationEnum lockOperationEnum) {
        MutexLockRequest mutexLockRequest = new MutexLockRequest();
        mutexLockRequest.setLockInstance(new LockInstance("key", 1L));
        WriteRequest writeRequest = WriteRequest.newBuilder().setGroup(lockOperationService.group())
                .setData(ByteString.copyFrom(serializer.serialize(mutexLockRequest)))
                .setOperation(lockOperationEnum.name()).build();
        return writeRequest;
    }
    
    public Response getResponse() {
        return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(true))).build();
    }
    
    @AfterClass
    public static void destroy() {
        mockedStatic.close();
        mockedEnv.close();
    }
}
