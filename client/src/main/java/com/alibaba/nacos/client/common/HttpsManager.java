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

package com.alibaba.nacos.client.common;

import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * @author James Chen
 */
public class HttpsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsManager.class);

    public static void trustAllHttpsCertificates() {
        TrustManager[] trustAllCerts = new TrustManager[1];
        trustAllCerts[0] = new SslConfig();
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("no such algorithn ", e);
        } catch (KeyManagementException e) {
            LOGGER.error("key management error ", e);
        }
    }

    public static void verifyHttpsHostName(String url) {

        if (url.startsWith(UtilAndComs.HTTPS)) {
            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        }
    }

}
