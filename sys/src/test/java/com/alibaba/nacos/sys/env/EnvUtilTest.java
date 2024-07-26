package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvUtilTest {

    MockedStatic<OperatingSystemBeanManager> systemBeanManagerMocked;

    @BeforeEach
    void before() {
        systemBeanManagerMocked = Mockito.mockStatic(OperatingSystemBeanManager.class);
    }

    @AfterEach
    void after() {
        if (!systemBeanManagerMocked.isClosed()) {
            systemBeanManagerMocked.close();
        }
    }

    @Test
    public void test() {
        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getFreePhysicalMem()).thenReturn(123l);
        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getTotalPhysicalMem()).thenReturn(2048l);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 123 / (double) 2048));

        systemBeanManagerMocked.when(() -> OperatingSystemBeanManager.getFreePhysicalMem()).thenReturn(0l);
        assertEquals(EnvUtil.getMem(), 1 - ((double) 0 / (double) 2048));
    }
}
