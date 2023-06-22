/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.consistency.serialize;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import org.apache.http.HttpException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;

/**
 * {@link HessianSerializer} unit test.
 *
 * @author Chenhao26
 * @date 2022-08-13
 */
public class HessianSerializerTest {
    
    private HessianSerializer hessianSerializer;
    
    @Before
    public void setUp() {
        hessianSerializer = new HessianSerializer();
    }
    
    @Test
    public void testSerializerAndDeserialize() {
        String data = "xxx";
        byte[] bytes = hessianSerializer.serialize(data);
        
        try {
            hessianSerializer.deserialize(bytes);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        
        String res1 = hessianSerializer.deserialize(bytes, String.class);
        Assert.assertEquals(data, res1);
        
        String res2 = hessianSerializer.deserialize(bytes, "java.lang.String");
        Assert.assertEquals(data, res2);
    }
    
    @Test
    public void testSerializerAndDeserializeForNotAllowClass() {
        Serializable data = new HttpException();
        byte[] bytes = hessianSerializer.serialize(data);
        
        try {
            HttpException res = hessianSerializer.deserialize(bytes);
            Assert.fail("deserialize success which is not expected");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ClassCastException);
        }
        
        try {
            HttpException res1 = hessianSerializer.deserialize(bytes, HttpException.class);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NacosDeserializationException);
        }
    }
    
    @Test
    public void testName() {
        Assert.assertEquals("Hessian", hessianSerializer.name());
    }
}
