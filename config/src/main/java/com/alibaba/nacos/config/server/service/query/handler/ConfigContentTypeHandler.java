package com.alibaba.nacos.config.server.service.query.handler;

import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;

import java.io.IOException;

public class ConfigContentTypeHandler extends AbstractConfigQueryHandler {
    
    private static final String CONFIG_CONTENT_TYPE_HANDLER_NAME = "ConfigContentTypeHandler";
    
    @Override
    public String getName() {
        return CONFIG_CONTENT_TYPE_HANDLER_NAME;
    }
    
    @Override
    public ConfigQueryChainResponse handle(ConfigQueryChainRequest request) throws IOException {
        ConfigQueryChainResponse response = getNextHandler().handle(request);
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND
                || response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND) {
            return response;
        }
        String contentType =
                response.getContentType() != null ? response.getContentType() : FileTypeEnum.TEXT.getFileType();
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(contentType);
        String contentTypeHeader = fileTypeEnum.getContentType();
        response.setContentType(contentTypeHeader);
        return response;
    }
}
