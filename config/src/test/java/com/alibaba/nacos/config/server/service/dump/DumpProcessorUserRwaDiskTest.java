package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRawDiskService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DumpProcessorUserRwaDiskTest extends DumpProcessorTest{
    
    @Before
    public void init() throws Exception {
        super.init();
    }
    
    @Override
    protected ConfigDiskService createDiskService() {
        return new ConfigRawDiskService();
    }
    
    @After
    public void after() {
        super.after();
    }
    
    @Test
    public void testDumpNormalAndRemove() throws IOException {
        super.testDumpNormalAndRemove();
        
    }
    
    @Test
    public void testDumpBetaAndRemove() throws IOException {
        super.testDumpBetaAndRemove();
    }
    
    @Test
    public void testDumpTagAndRemove() throws IOException {
        super.testDumpTagAndRemove();
    }
}
