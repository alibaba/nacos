package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder;
import junit.framework.TestCase;
import org.junit.Assert;

import java.lang.reflect.Field;

public class PathEncoderManagerTest extends TestCase {

    public void test() throws Exception {
        // load static
        Class.forName(PathEncoderManager.class.getName());
        // remove windows impl
        Field targetEncoder = PathEncoderManager.class.getDeclaredField("targetEncoder");
        targetEncoder.setAccessible(true);
        targetEncoder.set(PathEncoderManager.class, null);
        // try to encode, non windows
        String case1 = "aa||a";
        Assert.assertEquals(PathEncoderManager.encode(case1), case1);
        String case2 = "aa%A9%%A9%a";
        Assert.assertEquals(PathEncoderManager.decode(case2), case2);
        // try to encode if in windows
        targetEncoder.set(PathEncoderManager.class, new WindowsEncoder());
        Assert.assertEquals(PathEncoderManager.encode(case1), case2);
        Assert.assertEquals(PathEncoderManager.decode(case2), case1);
    }

}
