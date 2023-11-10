/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.pathencoder;

import com.alibaba.nacos.common.pathencoder.impl.WindowsEncoder;
import junit.framework.TestCase;
import org.junit.Assert;

import java.nio.charset.Charset;

public class WindowsEncoderTest extends TestCase {

    WindowsEncoder windowsEncoder = new WindowsEncoder();

    /**
     * test encode.
     */
    public void testEncode() {
        String charset = Charset.defaultCharset().name();
        String case1 = "aaaadsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case1, charset), case1);
        // matches one
        String case2 = "aaaa\\dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case2, charset), "aaaa%A1%dsaknkf");
        String case3 = "aaaa/dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case3, charset), "aaaa%A2%dsaknkf");
        String case4 = "aaaa:dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case4, charset), "aaaa%A3%dsaknkf");
        String case5 = "aaaa*dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case5, charset), "aaaa%A4%dsaknkf");
        String case6 = "aaaa?dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case6, charset), "aaaa%A5%dsaknkf");
        String case7 = "aaaa\"dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case7, charset), "aaaa%A6%dsaknkf");
        String case8 = "aaaa<dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case8, charset), "aaaa%A7%dsaknkf");
        String case9 = "aaaa>dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case9, charset), "aaaa%A8%dsaknkf");
        String case10 = "aaaa|dsaknkf";
        Assert.assertEquals(windowsEncoder.encode(case10, charset), "aaaa%A9%dsaknkf");

        // matches more
        String case11 = "aaaa<dsa<>>knkf";
        Assert.assertEquals(windowsEncoder.encode(case11, charset), "aaaa%A7%dsa%A7%%A8%%A8%knkf");
        String case12 = "aaaa\"dsa\"\\\\knkf";
        Assert.assertEquals(windowsEncoder.encode(case12, charset), "aaaa%A6%dsa%A6%%A1%%A1%knkf");
    }

    /**
     * test decode.
     */
    public void testDecode() {
        String charset = Charset.defaultCharset().name();
        String case1 = "aaaadsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case1, charset), case1);
        // matches one
        String case2 = "aaaa%A1%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case2, charset), "aaaa\\dsaknkf");
        String case3 = "aaaa%A2%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case3, charset), "aaaa/dsaknkf");
        String case4 = "aaaa%A3%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case4, charset), "aaaa:dsaknkf");
        String case5 = "aaaa%A4%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case5, charset), "aaaa*dsaknkf");
        String case6 = "aaaa%A5%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case6, charset), "aaaa?dsaknkf");
        String case7 = "aaaa%A6%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case7, charset), "aaaa\"dsaknkf");
        String case8 = "aaaa%A7%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case8, charset), "aaaa<dsaknkf");
        String case9 = "aaaa%A8%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case9, charset), "aaaa>dsaknkf");
        String case10 = "aaaa%A9%dsaknkf";
        Assert.assertEquals(windowsEncoder.decode(case10, charset), "aaaa|dsaknkf");

        // matches more
        String case11 = "aaaa%A7%dsa%A7%%A8%%A8%knkf";
        Assert.assertEquals(windowsEncoder.decode(case11, charset), "aaaa<dsa<>>knkf");
        String case12 = "aaaa%A6%dsa%A6%%A1%%A1%knkf";
        Assert.assertEquals(windowsEncoder.decode(case12, charset), "aaaa\"dsa\"\\\\knkf");
    }

    /**
     * test needEncode.
     */
    public void testNeedEncode() {
        String case1 = "aaaadsaknkf";
        // / : ? " < > | \
        String case2 = "?asda";
        String case3 = "/asdasd";
        String case4 = "as\\dasda";
        String case5 = "asd::as";
        String case6 = "sda\"sda";
        String case7 = "asdas<da";
        String case8 = "sdasas>a";
        String case9 = "das1|2e";
        Assert.assertFalse(windowsEncoder.needEncode(null));
        Assert.assertFalse(windowsEncoder.needEncode(case1));
        Assert.assertTrue(windowsEncoder.needEncode(case2));
        Assert.assertTrue(windowsEncoder.needEncode(case3));
        Assert.assertTrue(windowsEncoder.needEncode(case4));
        Assert.assertTrue(windowsEncoder.needEncode(case5));
        Assert.assertTrue(windowsEncoder.needEncode(case6));
        Assert.assertTrue(windowsEncoder.needEncode(case7));
        Assert.assertTrue(windowsEncoder.needEncode(case8));
        Assert.assertTrue(windowsEncoder.needEncode(case9));
    }
}
