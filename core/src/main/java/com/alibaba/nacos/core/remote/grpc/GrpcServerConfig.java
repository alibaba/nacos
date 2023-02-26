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

package com.alibaba.nacos.core.remote.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Grpc config.
 *
 * @author githubcheng2978.
 */

@ConfigurationProperties(prefix = "nacos.remote.server.grpc.tls")
@Component
public class GrpcServerConfig {

    /**
     * ssl provider,default OPENSSL,JDK,OPENSSL_REFCNT.
     */
    private String sslProvider = "OPENSSL";

    /**
     *  whether to enables tls.
     */
    private Boolean enableTls = false;

    /**
     *  open tls and plain protocol.
     */
    private Boolean compatibility = true;

    /**
     *   private key.
     */
    private String privateKeyFile;

    /**
     * cert file.
     */
    private String certChainFile;

    /**
     * tls version: TLSv1.1,TLSv1.2,TLSv1.3.
     * if want to support multi protocol, use comma  seperated. like TLSv1.1,TLSv1.2,TLSv1.3.
     */
    private String protocols;

    /**
     * cipherList,  same of usage protocols.
     */
    private String ciphers;

    /**
     * read certPrivateKey file when need password.
     */
    private String password;

    /**
     * whether to enable mutual auth.
     */
    private Boolean mutualAuthEnable = false;

    /**
     * ignore certificate valid that trust all client.
     */
    private Boolean trustCertAll = false;

    /**
     *  if enable mutual auth please merge all cert file to one file.
     */
    private String trustCollectionCertFile;

    public String getSslProvider() {
        return sslProvider;
    }

    public void setSslProvider(String sslProvider) {
        this.sslProvider = sslProvider;
    }

    public Boolean getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(Boolean compatibility) {
        this.compatibility = compatibility;
    }

    public Boolean getTrustCertAll() {
        return trustCertAll;
    }

    public void setTrustCertAll(Boolean trustCertAll) {
        this.trustCertAll = trustCertAll;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getEnableTls() {
        return enableTls;
    }

    public void setEnableTls(Boolean enableTls) {
        this.enableTls = enableTls;
    }

    public Boolean getMutualAuthEnable() {
        return mutualAuthEnable;
    }

    public void setMutualAuthEnable(Boolean mutualAuthEnable) {
        this.mutualAuthEnable = mutualAuthEnable;
    }

    public String getTrustCollectionCertFile() {
        return trustCollectionCertFile;
    }

    public void setTrustCollectionCertFile(String trustCollectionCertFile) {
        this.trustCollectionCertFile = trustCollectionCertFile;
    }

    public String getCertChainFile() {
        return certChainFile;
    }

    public void setCertChainFile(String certChainFile) {
        this.certChainFile = certChainFile;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getProtocols() {
        return protocols;
    }

    public void setProtocols(String protocols) {
        this.protocols = protocols;
    }

    public String getCiphers() {
        return ciphers;
    }

    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }
}
