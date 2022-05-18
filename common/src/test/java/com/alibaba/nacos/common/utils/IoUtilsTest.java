package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Unit test of IoUtils.
 *
 * @author karsonto
 */
public class IoUtilsTest {
    
    @Test()
    public void testCloseQuietly() throws IOException {
        InputStream in = System.in;
        Assert.assertEquals(in.available(), 0);
        IoUtils.closeQuietly(in);
        try {
            in.available();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
    }
    
    @Test()
    public void testCloseQuietly2() throws IOException {
        InputStream in = System.in;
        InputStream in2 = System.in;
        Assert.assertEquals(in.available(), 0);
        Assert.assertEquals(in2.available(), 0);
        IoUtils.closeQuietly(in, in2);
        try {
            in.available();
        } catch (IOException e) {
            Assert.assertNotNull(e);
        }
        try {
            in2.available();
        } catch (IOException e) {
            Assert.assertNotNull(e);
            return;
        }
        Assert.fail();
        
    }
    
}
