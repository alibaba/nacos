package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.filter.IConfigFilter;
import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.config.filter.IConfigRequest;
import com.alibaba.nacos.api.config.filter.IConfigResponse;
import com.alibaba.nacos.api.config.filter.IFilterConfig;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.utils.AesUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.AlibabaCloudCredentials;
import com.aliyuncs.auth.AlibabaCloudCredentialsProvider;
import com.aliyuncs.auth.BasicSessionCredentials;
import com.aliyuncs.auth.InstanceProfileCredentialsProvider;
import com.aliyuncs.auth.StaticCredentialsProvider;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.kms.model.v20160120.DecryptRequest;
import com.aliyuncs.kms.model.v20160120.EncryptRequest;
import com.aliyuncs.kms.model.v20160120.GenerateDataKeyRequest;
import com.aliyuncs.kms.model.v20160120.GenerateDataKeyResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.fasterxml.jackson.databind.JsonNode;

import static com.alibaba.nacos.api.common.Constants.CIPHER_PREFIX;
import static com.alibaba.nacos.api.common.Constants.CIPHER_KMS_AES_128_PREFIX;
import static com.alibaba.nacos.api.common.Constants.KMS_KEY_SPEC_AES_128;

public class KmsConfigFilter implements IConfigFilter {
    
    @Override
    public void doFilter(IConfigRequest request, IConfigResponse response, IConfigFilterChain filterChain)
            throws NacosException {
        String dataId = null;
        String group = null;
        try {
            ConfigRequest requestTmp = (ConfigRequest) request;
            ConfigResponse responseTmp = (ConfigResponse) response;
            
            if (request != null && requestTmp.getDataId().startsWith(CIPHER_PREFIX)) {
                
                dataId = requestTmp.getDataId();
                group = requestTmp.getGroup();
                
                if (requestTmp.getContent() != null) {
                    requestTmp.setContent(encrypt(keyId, requestTmp));
                }
            }
            
            filterChain.doFilter(requestTmp, responseTmp);
            
            if (responseTmp != null && responseTmp.getDataId().startsWith(CIPHER_PREFIX)) {
                
                dataId = responseTmp.getDataId();
                group = responseTmp.getGroup();
                
                if (responseTmp.getContent() != null) {
                    responseTmp.setContent(decrypt(responseTmp));
                }
            }
        } catch (Exception e) {
            String message = String.format("KMS error, dataId: %s, groupId: %s", dataId, group);
            throw new NacosException(500, message, e);
        }
    }
    
    private DefaultAcsClient kmsClient(String regionId, String accessKeyId, String accessKeySecret) {
        IClientProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        return new DefaultAcsClient(profile);
    }
    
    /**
     * KMS 所需的 STS Token 让 aliyun-java-sdk-core 自动管理其周期 https://help.aliyun.com/document_detail/59946.html
     * https://help.aliyun.com/document_detail/59919.html 而与通过 STS Token 访问 ACM 的地方（diamond-client）我们自己维护周期，不引入
     * aliyun-java-sdk-core 的依赖
     */
    private DefaultAcsClient kmsClient(String regionId, String ramRoleName) {
        IClientProfile profile = DefaultProfile.getProfile(regionId);
        AlibabaCloudCredentialsProvider alibabaCloudCredentialsProvider = new InstanceProfileCredentialsProvider(
                ramRoleName);
        return new DefaultAcsClient(profile, alibabaCloudCredentialsProvider);
    }
    
    private String decrypt(ConfigResponse configResponse) throws Exception {
        String dataId = configResponse.getDataId();
        
        if (dataId.startsWith(CIPHER_KMS_AES_128_PREFIX)) {
            String encryptedDataKey = configResponse.getEncryptedDataKey();
            if (!StringUtils.isBlank(encryptedDataKey)) {
                String dataKey = decrypt(encryptedDataKey);
                return AesUtils.decrypt(configResponse.getContent(), dataKey, "UTF-8");
            }
        }
        
        return decrypt(configResponse.getContent());
    }
    
    private String decrypt(String content) throws ClientException {
        final DecryptRequest decReq = new DecryptRequest();
        decReq.setAcceptFormat(FormatType.JSON);
        decReq.setCiphertextBlob(content);
        return kmsClient.getAcsResponse(decReq).getPlaintext();
    }
    
    private String encrypt(String keyId, ConfigRequest configRequest) throws Exception {
        String dataId = configRequest.getDataId();
        
        if (dataId.startsWith(CIPHER_KMS_AES_128_PREFIX)) {
            GenerateDataKeyResponse generateDataKeyResponse = generateDataKey(keyId, KMS_KEY_SPEC_AES_128);
            configRequest.setEncryptedDataKey(generateDataKeyResponse.getCiphertextBlob());
            String dataKey = generateDataKeyResponse.getPlaintext();
            return AesUtils.encrypt(configRequest.getContent(), dataKey, "UTF-8");
        }
        
        return encrypt(keyId, configRequest.getContent());
    }
    
    private String encrypt(String keyId, String plainText) throws ClientException {
        final EncryptRequest encReq = new EncryptRequest();
        encReq.setAcceptFormat(FormatType.JSON);
        encReq.setKeyId(keyId);
        encReq.setPlaintext(plainText);
        return kmsClient.getAcsResponse(encReq).getCiphertextBlob();
    }
    
    private GenerateDataKeyResponse generateDataKey(String keyId, String keySpec) throws ClientException {
        GenerateDataKeyRequest generateDataKeyRequest = new GenerateDataKeyRequest();
        
        generateDataKeyRequest.setAcceptFormat(FormatType.JSON);
        
        generateDataKeyRequest.setKeyId(keyId);
        generateDataKeyRequest.setKeySpec(keySpec);
        return kmsClient.getAcsResponse(generateDataKeyRequest);
    }
    
    @Override
    public void init(IFilterConfig filterConfig) {
        keyId = (String) filterConfig.getInitParameter(PropertyKeyConst.KMS_KEY_ID);
        String regionId = (String) filterConfig.getInitParameter(PropertyKeyConst.REGION_ID);
        String ramRoleName = (String) filterConfig.getInitParameter(PropertyKeyConst.RAM_ROLE_NAME);
        String securityCredentials = (String) filterConfig.getInitParameter(PropertyKeyConst.SECURITY_CREDENTIALS);
        
        if (!StringUtils.isBlank(securityCredentials)) {
            // 用户自己维护 securityCredentials 的更新
            initKmsClientBySecurityCredentials(regionId, securityCredentials);
        } else if (!StringUtils.isBlank(ramRoleName)) {
            // 通过 ECS 实例 RAM 角色访问 ACM，比 AK/SK 的优先级高
            kmsClient = kmsClient(regionId, ramRoleName);
        } else {
            String accessKey = (String) filterConfig.getInitParameter(PropertyKeyConst.ACCESS_KEY);
            String secretKey = (String) filterConfig.getInitParameter(PropertyKeyConst.SECRET_KEY);
            kmsClient = kmsClient(regionId, accessKey, secretKey);
        }
        
        Object orderObject = filterConfig.getInitParameter("order");
        if (orderObject != null) {
            order = (Integer) orderObject;
        }
    }
    
    private void initKmsClientBySecurityCredentials(String regionId, String securityCredentials) {
        JsonNode jsonNode = JacksonUtils.toObj(securityCredentials);
        String accessKeyId = jsonNode.get("AccessKeyId").asText();
        String accessKeySecret = jsonNode.get("AccessKeySecret").asText();
        String securityToken = jsonNode.get("SecurityToken").asText();
        
        IClientProfile profile = DefaultProfile.getProfile(regionId);
        AlibabaCloudCredentials cloudCredentials = new BasicSessionCredentials(accessKeyId, accessKeySecret,
                securityToken);
        AlibabaCloudCredentialsProvider alibabaCloudCredentialsProvider = new StaticCredentialsProvider(
                cloudCredentials);
        kmsClient = new DefaultAcsClient(profile, alibabaCloudCredentialsProvider);
    }
    
    @Override
    public void deploy() {
        kmsClient = null;
    }
    
    private DefaultAcsClient kmsClient;
    
    private String keyId;
    
    private int order = 100;
    
    @Override
    public int getOrder() {
        return order;
    }
    
    @Override
    public String getFilterName() {
        return this.getClass().getName();
    }
    
}
