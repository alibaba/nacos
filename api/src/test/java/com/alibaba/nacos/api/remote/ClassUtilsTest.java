package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.utils.ClassUtils;
import org.junit.Test;
import java.util.ArrayList;

/**
 * ClassUtilsTest.
 *
 * @author dingjuntao
 * @date 2021/7/8 19:47
 */
public class ClassUtilsTest {
    
    @Test
    public void getAllClassByInterfaceFileTest() {
        ArrayList<Class> list  = ClassUtils.getAllClassByAbstractClass(Request.class, ClientDetectionRequest.class.getPackage().getName());
        for (Class clazz : list) {
            System.out.println(clazz.getSimpleName());
        }
    }
    
}
