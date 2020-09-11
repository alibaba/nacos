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

/**
 * tls system config.
 *
 * @author wangwei
 */
public final class TlsSystemConfig {
    
    public static final String TLS_TEST_MODE_ENABLE = "tls.test";
    
    public static final String TLS_ENABLE = "tls.enable";
    
    public static final String CLIENT_AUTH = "tls.client.authServer";
    
    public static final String CLIENT_KEYPATH = "tls.client.keyPath";
    
    public static final String CLIENT_KEYPASSWORD = "tls.client.keyPassword";
    
    public static final String CLIENT_CERTPATH = "tls.client.certPath";
    
    public static final String CLIENT_TRUST_CERT = "tls.client.trustCertPath";
    
    public static final String SERVER_AUTH = "tls.server.authClient";
    
    public static final String SERVER_KEYPATH = "tls.server.keyPath";
    
    public static final String SERVER_KEYPASSWORD = "tls.server.keyPassword";
    
    public static final String SERVER_CERTPATH = "tls.server.certPath";
    
    public static final String SERVER_TRUST_CERT = "tls.server.trustCertPath";
    
    public static final String CHECK_INTERVAL = "checkIntervalTlsFile";
    
    /**
     * To determine whether use SSL in client-side.
     */
    public static boolean tlsEnable = Boolean.parseBoolean(System.getProperty(TLS_ENABLE, "false"));
    
    /**
     * To determine whether use test mode when initialize TLS context.
     */
    public static boolean tlsTestModeEnable = Boolean.parseBoolean(System.getProperty(TLS_TEST_MODE_ENABLE, "false"));
    
    /**
     * To determine whether verify the server endpoint's certificate strictly.
     */
    public static boolean tlsClientAuthServer = Boolean.parseBoolean(System.getProperty(CLIENT_AUTH, "false"));
    
    /**
     * To determine whether verify the client endpoint's certificate strictly.
     */
    public static boolean tlsServerAuthClient = Boolean.parseBoolean(System.getProperty(SERVER_AUTH, "false"));
    
    /**
     * The store path of client-side private key.
     */
    public static String tlsClientKeyPath = System.getProperty(CLIENT_KEYPATH, null);
    
    /**
     * The password of the client-side private key.
     */
    public static String tlsClientKeyPassword = System.getProperty(CLIENT_KEYPASSWORD, null);
    
    /**
     * The store path of client-side X.509 certificate chain in PEM format.
     */
    public static String tlsClientCertPath = System.getProperty(CLIENT_CERTPATH, null);
    
    /**
     * The store path of trusted certificates for verifying the server endpoint's certificate.
     */
    public static String tlsClientTrustCertPath = System.getProperty(CLIENT_TRUST_CERT, null);
    
    /**
     * The store path of server-side private key.
     */
    public static String tlsServerKeyPath = System.getProperty(SERVER_KEYPATH, null);
    
    /**
     * The  password of the server-side private key.
     */
    public static String tlsServerKeyPassword = System.getProperty(SERVER_KEYPASSWORD, null);
    
    /**
     * The store path of server-side X.509 certificate chain in PEM format.
     */
    public static String tlsServerCertPath = System.getProperty(SERVER_CERTPATH, null);
    
    /**
     * The store path of trusted certificates for verifying the client endpoint's certificate.
     */
    public static String tlsServerTrustCertPath = System.getProperty(SERVER_TRUST_CERT, null);
    
    /**
     * tls file check interval , default is 10 min.
     */
    public static int tlsFileCheckInterval = Integer.parseInt(System.getProperty(CHECK_INTERVAL, "10"));
    
}
