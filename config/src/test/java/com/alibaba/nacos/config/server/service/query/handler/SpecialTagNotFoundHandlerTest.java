package com.alibaba.nacos.config.server.service.query.handler;

import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialTagNotFoundHandlerTest {
    
    @InjectMocks
    private SpecialTagNotFoundHandler specialTagNotFoundHandler;
    
    @Mock
    private ConfigQueryHandler nextHandler;
    
    @Test
    public void handleTagNotEmptyReturnsSpecialTagNotFoundResponse() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTag("someTag");
        ConfigQueryChainResponse response = specialTagNotFoundHandler.handle(request);
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND, response.getStatus());
    }
    
    @Test
    public void handleTagEmptyDelegatesToNextHandler() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTag("");
        ConfigQueryChainResponse expectedResponse = new ConfigQueryChainResponse();
        when(nextHandler.handle(request)).thenReturn(expectedResponse);
        ConfigQueryChainResponse response = specialTagNotFoundHandler.handle(request);
        assertEquals(expectedResponse, response);
    }
    
    @Test
    public void getName() {
        assertEquals("SpecialTagNotFoundHandler", specialTagNotFoundHandler.getName());
    }
    
}