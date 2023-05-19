/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.tls;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Utils for build {@link SSLContext}.
 *
 * <p>Currently only supports client-side
 *
 * <h3>Making your client support TLS without authentication</h3>
 * <pre>
 * System.setProperty({@link TlsSystemConfig#TLS_ENABLE}, "true");
 * </pre>
 *
 * <h3>Making your client support TLS one-way authentication</h3>
 *
 * <pre>
 * System.setProperty({@link TlsSystemConfig#TLS_ENABLE}, "true");
 * System.setProperty({@link TlsSystemConfig#CLIENT_AUTH}, "true");
 * System.setProperty({@link TlsSystemConfig#CLIENT_TRUST_CERT}, "trustCert");
 * </pre>
 *
 * @author wangwei
 * @date 2020/8/19 2:59 PM
 */
public final class TlsHelper {
    
    /**
     * Returns a {@link org.apache.http.ssl.SSLContexts}.
     *
     * <p>For example</p>
     * <code>HttpsURLConnection.setDefaultSSLSocketFactory(TlsHelper.buildSslContext(true).getSocketFactory());</code>
     *
     * @param forClient whether for client
     * @return {@link SSLContext}
     * @throws NoSuchAlgorithmException Not support the specified algorithm
     * @throws KeyManagementException   KeyManagement exception
     */
    public static SSLContext buildSslContext(boolean forClient)
            throws NoSuchAlgorithmException, KeyManagementException {
        
        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, SelfTrustManager
                        .trustManager(TlsSystemConfig.tlsClientAuthServer, TlsSystemConfig.tlsClientTrustCertPath),
                new java.security.SecureRandom());
        return sslcontext;
    }
}
