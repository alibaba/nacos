package com.alibaba.nacos.config.server.service.dump.disk;

import junit.framework.TestCase;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigRawDiskServiceTest extends TestCase {
    
    /**
     * 测试获取beta文件路径.
     */
    public void testTargetFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetFile", String.class, String.class, String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "aaaa\\dsaknkf", "aaaa/dsaknkf", "aaaa:dsaknkf");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        // 获取最后三段路径
        String lastSegment = path.getFileName().toString();
        String secondLastSegment = parent.getFileName().toString();
        String thirdLastSegment = grandParent.getFileName().toString();
        assertEquals("aaaa%A3%dsaknkf", thirdLastSegment);
        assertEquals("aaaa%A2%dsaknkf", secondLastSegment);
        assertEquals("aaaa%A1%dsaknkf", lastSegment);
    }
    
    /**
     * 测试获取beta文件路径.
     */
    public void testTargetBetaFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetBetaFile", String.class, String.class, String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "aaaa\\dsaknkf", "aaaa/dsaknkf", "aaaa:dsaknkf");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        // 获取最后三段路径
        String lastSegment = path.getFileName().toString();
        String secondLastSegment = parent.getFileName().toString();
        String thirdLastSegment = grandParent.getFileName().toString();
        assertEquals("aaaa%A3%dsaknkf", thirdLastSegment);
        assertEquals("aaaa%A2%dsaknkf", secondLastSegment);
        assertEquals("aaaa%A1%dsaknkf", lastSegment);
        
    }
    
    /**
     * 测试获取tag文件路径.
     * @throws NoSuchMethodException  方法不存在异常
     * @throws IllegalAccessException 非法访问异常
     * @throws InvocationTargetException 目标异常
     */
    public void testTargetTagFile() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = ConfigRawDiskService.class.getDeclaredMethod("targetTagFile", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        File result = (File) method.invoke(null, "aaaa\\dsaknkf", "aaaa/dsaknkf", "aaaa:dsaknkf", "aaaadsaknkf");
        // 分解路径
        Path path = Paths.get(result.getPath());
        Path parent = path.getParent();
        Path grandParent = parent.getParent();
        Path greatGrandParent = grandParent.getParent();
        // 获取最后四段路径
        String secondLastSegment = parent.getFileName().toString();
        String thirdLastSegment = grandParent.getFileName().toString();
        String fourthLastSegment = greatGrandParent.getFileName().toString();
        assertEquals("aaaa%A3%dsaknkf", fourthLastSegment);
        assertEquals("aaaa%A2%dsaknkf", thirdLastSegment);
        assertEquals("aaaa%A1%dsaknkf", secondLastSegment);
        String lastSegment = path.getFileName().toString();
        assertEquals("aaaadsaknkf", lastSegment);
    }
}
