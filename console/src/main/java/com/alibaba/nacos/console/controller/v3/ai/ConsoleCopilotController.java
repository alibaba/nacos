/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import com.alibaba.nacos.copilot.form.PromptDebugForm;
import com.alibaba.nacos.copilot.form.PromptOptimizationForm;
import com.alibaba.nacos.copilot.form.SkillGenerationForm;
import com.alibaba.nacos.copilot.form.SkillOptimizationForm;
import com.alibaba.nacos.copilot.model.PromptDebugRequest;
import com.alibaba.nacos.copilot.model.PromptDebugResponse;
import com.alibaba.nacos.copilot.model.PromptOptimizationRequest;
import com.alibaba.nacos.copilot.model.PromptOptimizationResponse;
import com.alibaba.nacos.copilot.model.SkillGenerationRequest;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import com.alibaba.nacos.copilot.model.SkillOptimizationRequest;
import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import com.alibaba.nacos.copilot.service.PromptDebugService;
import com.alibaba.nacos.copilot.service.PromptOptimizationService;
import com.alibaba.nacos.copilot.service.SkillGenerationService;
import com.alibaba.nacos.copilot.service.SkillOptimizationService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Console Copilot controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(CopilotConstants.COPILOT_CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = CopilotHttpParamExtractor.class)
public class ConsoleCopilotController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCopilotController.class);
    
    private final SkillOptimizationService skillOptimizationService;
    
    private final SkillGenerationService skillGenerationService;
    
    private final PromptOptimizationService promptOptimizationService;
    
    private final PromptDebugService promptDebugService;
    
    @Autowired
    public ConsoleCopilotController(SkillOptimizationService skillOptimizationService,
        SkillGenerationService skillGenerationService,
        PromptOptimizationService promptOptimizationService,
        PromptDebugService promptDebugService) {
        this.skillOptimizationService = skillOptimizationService;
        this.skillGenerationService = skillGenerationService;
        this.promptOptimizationService = promptOptimizationService;
        this.promptDebugService = promptDebugService;
    }
    
    /**
     * Optimize skill with stream response (SSE).
     *
     * @param form skill optimization form
     * @return SSE emitter for stream response
     * @throws NacosException if validation fails
     */
    @Since("3.2.0")
    @PostMapping(value = CopilotConstants.SKILL_OPTIMIZE_PATH,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    @SuppressWarnings("PMD.MethodTooLongRule")
    public SseEmitter optimizeSkillStream(
        @RequestBody(required = false) SkillOptimizationForm form) {
        // Create SSE emitter with 5 minutes timeout
        SseEmitter emitter = new SseEmitter(300000L);
        
        // Handle null form or missing request body
        if (form == null) {
            try {
                SkillOptimizationResponse errorResponse = new SkillOptimizationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求体不能为空");
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send error SSE event", ioException);
                emitter.completeWithError(ioException);
            }
            return emitter;
        }
        
        try {
            form.validate();
        } catch (Exception e) {
            LOGGER.error("Form validation failed", e);
            try {
                SkillOptimizationResponse errorResponse = new SkillOptimizationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求验证失败：" + e.getMessage());
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send validation error SSE event", ioException);
                emitter.complete();
            }
            return emitter;
        }
        
        // Build request
        SkillOptimizationRequest request = new SkillOptimizationRequest();
        request.setSkill(form.getSkill());
        request.setOptimizationGoal(form.getOptimizationGoal());
        request.setConversationHistory(form.getConversationHistory());
        request.setTargetFileName(form.getTargetFileName());
        
        // Set selectedMcpTools to params if provided
        if (form.getSelectedMcpTools() != null && !form.getSelectedMcpTools().isEmpty()) {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("selectedMcpTools", form.getSelectedMcpTools());
            request.setParams(params);
        }
        
        // Call optimization service with stream callback
        skillOptimizationService.optimizeSkillStream(request,
            new StreamResponseCallback<SkillOptimizationResponse>() {
                
                @Override
                public void onNext(SkillOptimizationResponse response) {
                    try {
                        // Filter out SKILL.md from resources before sending to frontend
                        if (response != null && response.getOptimizedSkill() != null) {
                            Skill optimizedSkill = response.getOptimizedSkill();
                            if (optimizedSkill.getResource() != null
                                && !optimizedSkill.getResource().isEmpty()) {
                                Map<String, SkillResource> filteredResources = new HashMap<>(
                                    optimizedSkill.getResource().size());
                                boolean hasFiltered = false;
                                
                                for (Map.Entry<String, SkillResource> entry : optimizedSkill
                                    .getResource().entrySet()) {
                                    String key = entry.getKey();
                                    SkillResource resource = entry.getValue();
                                    
                                    // Check if resource name or key is SKILL.md (case-insensitive)
                                    String resourceName =
                                        resource != null && resource.getName() != null
                                            ? resource.getName() : "";
                                    String resourceKey = key != null ? key : "";
                                    
                                    boolean isSkillMd =
                                        "SKILL.MD".equalsIgnoreCase(resourceName)
                                            || "SKILL.MD".equalsIgnoreCase(
                                                resourceKey)
                                            || resourceName.toUpperCase().contains("SKILL.MD")
                                            || resourceKey.toUpperCase().contains("SKILL.MD");
                                    
                                    if (isSkillMd) {
                                        hasFiltered = true;
                                        LOGGER.warn(
                                            "Filtered out SKILL.md resource: key={}, name={}", key,
                                            resourceName);
                                        continue;
                                    }
                                    
                                    filteredResources.put(key, resource);
                                }
                                
                                if (hasFiltered) {
                                    optimizedSkill.setResource(filteredResources);
                                    response.setOptimizedSkill(optimizedSkill);
                                }
                            }
                        }
                        
                        // Send SSE event
                        emitter.send(
                            SseEmitter.event().data(JacksonUtils.toJson(response)).name("message"));
                    } catch (IOException e) {
                        LOGGER.error("Failed to send SSE event", e);
                        try {
                            SkillOptimizationResponse errorResponse =
                                new SkillOptimizationResponse();
                            errorResponse.setDone(true);
                            errorResponse.setExplanation("流式响应发送失败：" + e.getMessage());
                            emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                                .name("error"));
                            emitter.complete();
                        } catch (IOException ioException) {
                            LOGGER.error("Failed to send error SSE event", ioException);
                            emitter.complete();
                        }
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    LOGGER.error("Error in skill optimization stream", t);
                    try {
                        // Send error response
                        SkillOptimizationResponse errorResponse = new SkillOptimizationResponse();
                        errorResponse.setDone(true);
                        errorResponse.setExplanation("优化失败：" + t.getMessage());
                        emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                            .name("error"));
                        emitter.complete();
                    } catch (IOException e) {
                        LOGGER.error("Failed to send error SSE event", e);
                        emitter.complete();
                    }
                }
                
                @Override
                public void onComplete() {
                    emitter.complete();
                }
            });
        
        return emitter;
    }
    
    /**
     * Generate skill from background information with stream response (SSE).
     *
     * @param form skill generation form
     * @return SSE emitter for stream response
     * @throws NacosException if validation fails
     */
    @Since("3.2.0")
    @PostMapping(value = CopilotConstants.SKILL_GENERATE_PATH,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    @SuppressWarnings("PMD.MethodTooLongRule")
    public SseEmitter generateSkillStream(@RequestBody(required = false) SkillGenerationForm form) {
        // Create SSE emitter with 5 minutes timeout
        SseEmitter emitter = new SseEmitter(300000L);
        
        // Handle null form or missing request body
        if (form == null) {
            try {
                SkillGenerationResponse errorResponse = new SkillGenerationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求体不能为空");
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send error SSE event", ioException);
                emitter.completeWithError(ioException);
            }
            return emitter;
        }
        
        try {
            form.validate();
        } catch (Exception e) {
            LOGGER.error("Form validation failed", e);
            try {
                SkillGenerationResponse errorResponse = new SkillGenerationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求验证失败：" + e.getMessage());
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send validation error SSE event", ioException);
                emitter.complete();
            }
            return emitter;
        }
        
        // Build request
        SkillGenerationRequest request = new SkillGenerationRequest();
        request.setBackgroundInfo(form.getBackgroundInfo());
        request.setSelectedMcpTools(form.getSelectedMcpTools());
        request.setConversationHistory(form.getConversationHistory());
        
        // Call generation service with stream callback
        skillGenerationService.generateSkillStream(request,
            new StreamResponseCallback<SkillGenerationResponse>() {
                
                @Override
                public void onNext(SkillGenerationResponse response) {
                    try {
                        // Send SSE event
                        emitter.send(
                            SseEmitter.event().data(JacksonUtils.toJson(response)).name("message"));
                    } catch (IOException e) {
                        LOGGER.error("Failed to send SSE event", e);
                        try {
                            SkillGenerationResponse errorResponse = new SkillGenerationResponse();
                            errorResponse.setDone(true);
                            errorResponse.setExplanation("流式响应发送失败：" + e.getMessage());
                            emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                                .name("error"));
                            emitter.complete();
                        } catch (IOException ioException) {
                            LOGGER.error("Failed to send error SSE event", ioException);
                            emitter.complete();
                        }
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    LOGGER.error("Error in skill generation stream", t);
                    try {
                        // Send error response
                        SkillGenerationResponse errorResponse = new SkillGenerationResponse();
                        errorResponse.setDone(true);
                        errorResponse.setExplanation("生成失败：" + t.getMessage());
                        emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                            .name("error"));
                        emitter.complete();
                    } catch (IOException e) {
                        LOGGER.error("Failed to send error SSE event", e);
                        emitter.complete();
                    }
                }
                
                @Override
                public void onComplete() {
                    emitter.complete();
                }
            });
        
        return emitter;
    }
    
    /**
     * Optimize prompt with stream response (SSE).
     *
     * @param form prompt optimization form
     * @return SSE emitter for stream response
     * @throws NacosException if validation fails
     */
    @Since("3.2.0")
    @PostMapping(value = CopilotConstants.PROMPT_OPTIMIZE_PATH,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    @SuppressWarnings("PMD.MethodTooLongRule")
    public SseEmitter optimizePromptStream(
        @RequestBody(required = false) PromptOptimizationForm form) {
        // Create SSE emitter with 5 minutes timeout
        SseEmitter emitter = new SseEmitter(300000L);
        
        // Handle null form or missing request body
        if (form == null) {
            try {
                PromptOptimizationResponse errorResponse = new PromptOptimizationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求体不能为空");
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send error SSE event", ioException);
                emitter.completeWithError(ioException);
            }
            return emitter;
        }
        
        try {
            form.validate();
        } catch (Exception e) {
            LOGGER.error("Form validation failed", e);
            try {
                PromptOptimizationResponse errorResponse = new PromptOptimizationResponse();
                errorResponse.setDone(true);
                errorResponse.setExplanation("请求验证失败：" + e.getMessage());
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send validation error SSE event", ioException);
                emitter.complete();
            }
            return emitter;
        }
        
        // Build request
        PromptOptimizationRequest request = new PromptOptimizationRequest();
        request.setPrompt(form.getPrompt());
        request.setOptimizationGoal(form.getOptimizationGoal());
        
        // Call optimization service with stream callback
        promptOptimizationService.optimizePromptStream(request,
            new StreamResponseCallback<PromptOptimizationResponse>() {
                
                @Override
                public void onNext(PromptOptimizationResponse response) {
                    try {
                        // Send SSE event
                        emitter.send(
                            SseEmitter.event().data(JacksonUtils.toJson(response)).name("message"));
                    } catch (IOException e) {
                        LOGGER.error("Failed to send SSE event", e);
                        try {
                            PromptOptimizationResponse errorResponse =
                                new PromptOptimizationResponse();
                            errorResponse.setDone(true);
                            errorResponse.setExplanation("流式响应发送失败：" + e.getMessage());
                            emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                                .name("error"));
                            emitter.complete();
                        } catch (IOException ioException) {
                            LOGGER.error("Failed to send error SSE event", ioException);
                            emitter.complete();
                        }
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    LOGGER.error("Error in prompt optimization stream", t);
                    try {
                        // Send error response
                        PromptOptimizationResponse errorResponse = new PromptOptimizationResponse();
                        errorResponse.setDone(true);
                        errorResponse.setExplanation("优化失败：" + t.getMessage());
                        emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                            .name("error"));
                        emitter.complete();
                    } catch (IOException e) {
                        LOGGER.error("Failed to send error SSE event", e);
                        emitter.complete();
                    }
                }
                
                @Override
                public void onComplete() {
                    emitter.complete();
                }
            });
        
        return emitter;
    }
    
    /**
     * Debug prompt with stream response (SSE). This allows testing a prompt with user input and returns the model's
     * response including thinking.
     *
     * @param form prompt debug form containing prompt and user input
     * @return SSE emitter for stream response
     * @throws NacosException if validation fails
     */
    @Since("3.2.0")
    @PostMapping(value = CopilotConstants.PROMPT_DEBUG_PATH,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    @SuppressWarnings("PMD.MethodTooLongRule")
    public SseEmitter debugPromptStream(@RequestBody(required = false) PromptDebugForm form) {
        // Create SSE emitter with 5 minutes timeout
        SseEmitter emitter = new SseEmitter(300000L);
        
        // Handle null form or missing request body
        if (form == null) {
            try {
                PromptDebugResponse errorResponse = new PromptDebugResponse();
                errorResponse.setDone(true);
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send error SSE event", ioException);
                emitter.completeWithError(ioException);
            }
            return emitter;
        }
        
        try {
            form.validate();
        } catch (Exception e) {
            LOGGER.error("Form validation failed", e);
            try {
                PromptDebugResponse errorResponse = new PromptDebugResponse();
                errorResponse.setDone(true);
                emitter.send(
                    SseEmitter.event().data(JacksonUtils.toJson(errorResponse)).name("error"));
                emitter.complete();
            } catch (IOException ioException) {
                LOGGER.error("Failed to send validation error SSE event", ioException);
                emitter.complete();
            }
            return emitter;
        }
        
        // Build request
        PromptDebugRequest request = new PromptDebugRequest();
        request.setPrompt(form.getPrompt());
        request.setUserInput(form.getUserInput());
        
        // Call debug service with stream callback
        promptDebugService.debugPromptStream(request,
            new StreamResponseCallback<PromptDebugResponse>() {
                
                @Override
                public void onNext(PromptDebugResponse response) {
                    try {
                        // Send SSE event
                        emitter.send(
                            SseEmitter.event().data(JacksonUtils.toJson(response)).name("message"));
                    } catch (IOException e) {
                        LOGGER.error("Failed to send SSE event", e);
                        try {
                            PromptDebugResponse errorResponse = new PromptDebugResponse();
                            errorResponse.setDone(true);
                            emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                                .name("error"));
                            emitter.complete();
                        } catch (IOException ioException) {
                            LOGGER.error("Failed to send error SSE event", ioException);
                            emitter.complete();
                        }
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    LOGGER.error("Error in prompt debug stream", t);
                    try {
                        // Send error response
                        PromptDebugResponse errorResponse = new PromptDebugResponse();
                        errorResponse.setDone(true);
                        emitter.send(SseEmitter.event().data(JacksonUtils.toJson(errorResponse))
                            .name("error"));
                        emitter.complete();
                    } catch (IOException e) {
                        LOGGER.error("Failed to send error SSE event", e);
                        emitter.complete();
                    }
                }
                
                @Override
                public void onComplete() {
                    emitter.complete();
                }
            });
        
        return emitter;
    }
    
}
