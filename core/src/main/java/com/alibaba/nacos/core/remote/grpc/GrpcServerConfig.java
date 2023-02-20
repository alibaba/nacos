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

    private Boolean enableSsl = false;

    private Boolean mutualAuthEnable = false;

    private Boolean compatibility = true;

    private Boolean trustCertAll = false;

    private String certificateChainFile;

    private String privateKeyFile;

    private String trustCertCollectionFile;

    private boolean nativeTransport;

    private String protocols;

    private String ciphers;

    private String password;

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

    public Boolean getEnableSsl() {
        return enableSsl;
    }

    public void setEnableSsl(Boolean enableSsl) {
        this.enableSsl = enableSsl;
    }

    public Boolean getMutualAuthEnable() {
        return mutualAuthEnable;
    }

    public void setMutualAuthEnable(Boolean mutualAuthEnable) {
        this.mutualAuthEnable = mutualAuthEnable;
    }

    public String getCertificateChainFile() {
        return certificateChainFile;
    }

    public String getTrustCertCollectionFile() {
        return trustCertCollectionFile;
    }

    public void setTrustCertCollectionFile(String trustCertCollectionFile) {
        this.trustCertCollectionFile = trustCertCollectionFile;
    }

    public void setCertificateChainFile(String certificateChainFile) {
        this.certificateChainFile = certificateChainFile;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public boolean isNativeTransport() {
        return nativeTransport;
    }

    public void setNativeTransport(boolean nativeTransport) {
        this.nativeTransport = nativeTransport;
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
