package com.alibaba.nacos.naming.healthcheck.v2;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.PersistentClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

/**
 * PersistentHealthStatusSynchronizerTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class PersistentHealthStatusSynchronizerTest {
    
    @Mock
    private PersistentClientOperationServiceImpl persistentClientOperationService;
    
    @Mock
    private Client client;
    
    @Test
    public void testInstanceHealthStatusChange() {
        Service service = Service.newService("public", "DEFAULT", "nacos", true);
        InstancePublishInfo instancePublishInfo = new InstancePublishInfo("127.0.0.1", 8080);
        PersistentHealthStatusSynchronizer persistentHealthStatusSynchronizer = new PersistentHealthStatusSynchronizer(
                persistentClientOperationService);
        persistentHealthStatusSynchronizer.instanceHealthStatusChange(true, client, service, instancePublishInfo);
        
        Instance updateInstance = InstanceUtil.parseToApiInstance(service, instancePublishInfo);
        updateInstance.setHealthy(true);
        
        verify(client).getClientId();
        verify(persistentClientOperationService).registerInstance(service, updateInstance, client.getClientId());
    }
}