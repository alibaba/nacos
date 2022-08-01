package com.alibaba.nacos.client.utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * @author HuHan
 * @date 2022/8/1 17:04
 */
public class MultipleServerDirMapTest {

    @Test
    public void testBaseDir() {
        String basePath = System.getProperty("user.home") + File.separator + "nacos";
        String key1 = MultipleServerDirMap.convertBaseDir(basePath, "localhost:2215,127.25.36.52:2254");
        String key2 = MultipleServerDirMap.convertBaseDir(basePath, "127.0.0.1:2215,127.25.36.52:2254");
        String key3 = MultipleServerDirMap.convertBaseDir(basePath, "127.25.36.52:2254");
        String key4 = MultipleServerDirMap.convertBaseDir(basePath, "127.25.146.52:225");
        String key5 = MultipleServerDirMap.convertBaseDir(basePath, "127.25.146.52:225,120.36.25.12:2253");
        Assert.assertTrue(key1.equals(key2));
        Assert.assertTrue(key3.equals(key2));
        Assert.assertFalse(key1.equals(key4));
        Assert.assertTrue(key4.equals(key5));
    }
}
