/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.address.exception;

import com.alibaba.nacos.api.common.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Date 2022/7/31.
 *
 * @author GuoJiangFu
 */
public class AddressExceptionTest {
    
    @Test
    public void testNewAddressExceptionWithCode() {
        AddressException actual = new AddressException(403);
        assertEquals(403, actual.getErrCode());
        assertEquals(Constants.NULL, actual.getErrMsg());
    }
    
    @Test
    public void testNewAddressExceptionWithMsg() {
        AddressException actual = new AddressException("Test");
        assertEquals("Test", actual.getErrMsg());
        assertEquals(0, actual.getErrCode());
    }
    
    @Test
    public void testNewAddressExceptionWithNoArgs() {
        AddressException actual = new AddressException();
        assertEquals(Constants.NULL, actual.getErrMsg());
        assertEquals(0, actual.getErrCode());
    }
}
