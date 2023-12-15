/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import org.junit.Assert;
import org.junit.Test;

public class TlsTypeResolveTest {

    @Test
    public void test() {

        SslProvider openssl = TlsTypeResolve.getSslProvider("openssl");
        Assert.assertEquals(SslProvider.OPENSSL, openssl);

        SslProvider openSsL = TlsTypeResolve.getSslProvider("openSSL");
        Assert.assertEquals(SslProvider.OPENSSL, openSsL);

        SslProvider jdk = TlsTypeResolve.getSslProvider("JDK");
        Assert.assertEquals(SslProvider.JDK, jdk);

        SslProvider anySsl = TlsTypeResolve.getSslProvider("anySSL");
        Assert.assertEquals(SslProvider.OPENSSL, anySsl);
        
        SslProvider refcnt = TlsTypeResolve.getSslProvider("openSSL_refcnt");
        Assert.assertEquals(SslProvider.OPENSSL_REFCNT, refcnt);
    }
}
