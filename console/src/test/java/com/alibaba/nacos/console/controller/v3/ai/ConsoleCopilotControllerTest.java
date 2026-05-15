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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.form.PromptDebugForm;
import com.alibaba.nacos.copilot.form.PromptOptimizationForm;
import com.alibaba.nacos.copilot.form.SkillGenerationForm;
import com.alibaba.nacos.copilot.form.SkillOptimizationForm;
import com.alibaba.nacos.copilot.model.PromptDebugResponse;
import com.alibaba.nacos.copilot.model.PromptOptimizationResponse;
import com.alibaba.nacos.copilot.model.SkillGenerationResponse;
import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import com.alibaba.nacos.copilot.service.PromptDebugService;
import com.alibaba.nacos.copilot.service.PromptOptimizationService;
import com.alibaba.nacos.copilot.service.SkillGenerationService;
import com.alibaba.nacos.copilot.service.SkillOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConsoleCopilotControllerTest {
    
    @Mock
    private SkillOptimizationService skillOptimizationService;
    
    @Mock
    private SkillGenerationService skillGenerationService;
    
    @Mock
    private PromptOptimizationService promptOptimizationService;
    
    @Mock
    private PromptDebugService promptDebugService;
    
    @Captor
    private ArgumentCaptor<StreamResponseCallback<SkillOptimizationResponse>> skillOptCaptor;
    
    @Captor
    private ArgumentCaptor<StreamResponseCallback<SkillGenerationResponse>> skillGenCaptor;
    
    @Captor
    private ArgumentCaptor<StreamResponseCallback<PromptOptimizationResponse>> promptOptCaptor;
    
    @Captor
    private ArgumentCaptor<StreamResponseCallback<PromptDebugResponse>> promptDebugCaptor;
    
    private ConsoleCopilotController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ConsoleCopilotController(skillOptimizationService,
            skillGenerationService, promptOptimizationService, promptDebugService);
    }
    
    // ===== optimizeSkillStream tests =====
    
    @Test
    void testOptimizeSkillStreamWithNullForm() {
        SseEmitter emitter = controller.optimizeSkillStream(null);
        assertNotNull(emitter);
        verifyNoInteractions(skillOptimizationService);
    }
    
    @Test
    void testOptimizeSkillStreamValidationFailureNullSkill() {
        SkillOptimizationForm form = new SkillOptimizationForm();
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(skillOptimizationService);
    }
    
    @Test
    void testOptimizeSkillStreamValidationFailureBlankName() {
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("");
        form.setSkill(skill);
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(skillOptimizationService);
    }
    
    @Test
    void testOptimizeSkillStreamSuccess() {
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        form.setOptimizationGoal("improve");
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        
        assertNotNull(emitter);
        verify(skillOptimizationService).optimizeSkillStream(any(), any());
    }
    
    @Test
    void testOptimizeSkillStreamWithSelectedMcpTools() {
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "tool1");
        form.setSelectedMcpTools(List.of(tool));
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        
        assertNotNull(emitter);
        verify(skillOptimizationService).optimizeSkillStream(any(), any());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamCallbackOnNext() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            response.setDone(true);
            response.setExplanation("optimized");
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamCallbackOnError() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            callback.onError(new RuntimeException("service error"));
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamFiltersSkillMdResource() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            Skill optimizedSkill = new Skill();
            optimizedSkill.setName("test-skill");
            Map<String, SkillResource> resources = new HashMap<>();
            SkillResource normalResource = new SkillResource();
            normalResource.setName("README.md");
            resources.put("readme", normalResource);
            SkillResource skillMdResource = new SkillResource();
            skillMdResource.setName("SKILL.MD");
            resources.put("skill-md", skillMdResource);
            optimizedSkill.setResource(resources);
            response.setOptimizedSkill(optimizedSkill);
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamFiltersResourceByKeyContainingSkillMd() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            Skill optimizedSkill = new Skill();
            optimizedSkill.setName("test-skill");
            Map<String, SkillResource> resources = new HashMap<>();
            SkillResource normalResource = new SkillResource();
            normalResource.setName("config.yaml");
            resources.put("config", normalResource);
            SkillResource mdResource = new SkillResource();
            mdResource.setName("docs");
            resources.put("path/SKILL.MD", mdResource);
            optimizedSkill.setResource(resources);
            response.setOptimizedSkill(optimizedSkill);
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamWithNullOptimizedSkill() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            response.setOptimizedSkill(null);
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamWithEmptyResources() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            Skill optimizedSkill = new Skill();
            optimizedSkill.setName("test-skill");
            optimizedSkill.setResource(new HashMap<>());
            response.setOptimizedSkill(optimizedSkill);
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamWithNullResourceName() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            SkillOptimizationResponse response = new SkillOptimizationResponse();
            Skill optimizedSkill = new Skill();
            optimizedSkill.setName("test-skill");
            Map<String, SkillResource> resources = new HashMap<>();
            SkillResource nullNameResource = new SkillResource();
            resources.put("key1", nullNameResource);
            optimizedSkill.setResource(resources);
            response.setOptimizedSkill(optimizedSkill);
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizeSkillStreamWithNullResponse() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillOptimizationResponse> callback =
                invocation.getArgument(1);
            callback.onNext(null);
            callback.onComplete();
            return null;
        }).when(skillOptimizationService).optimizeSkillStream(any(), any());
        
        SkillOptimizationForm form = new SkillOptimizationForm();
        Skill skill = new Skill();
        skill.setName("test-skill");
        form.setSkill(skill);
        
        SseEmitter emitter = controller.optimizeSkillStream(form);
        assertNotNull(emitter);
    }
    
    // ===== generateSkillStream tests =====
    
    @Test
    void testGenerateSkillStreamWithNullForm() {
        SseEmitter emitter = controller.generateSkillStream(null);
        assertNotNull(emitter);
        verifyNoInteractions(skillGenerationService);
    }
    
    @Test
    void testGenerateSkillStreamValidationFailure() {
        SkillGenerationForm form = new SkillGenerationForm();
        SseEmitter emitter = controller.generateSkillStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(skillGenerationService);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testGenerateSkillStreamCallbackOnNext() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillGenerationResponse> callback =
                invocation.getArgument(1);
            SkillGenerationResponse response = new SkillGenerationResponse();
            response.setDone(true);
            response.setExplanation("generated");
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(skillGenerationService).generateSkillStream(any(), any());
        
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("build a weather skill");
        
        SseEmitter emitter = controller.generateSkillStream(form);
        assertNotNull(emitter);
        verify(skillGenerationService).generateSkillStream(any(), any());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testGenerateSkillStreamCallbackOnError() {
        doAnswer(invocation -> {
            StreamResponseCallback<SkillGenerationResponse> callback =
                invocation.getArgument(1);
            callback.onError(new RuntimeException("generation error"));
            return null;
        }).when(skillGenerationService).generateSkillStream(any(), any());
        
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("build a weather skill");
        
        SseEmitter emitter = controller.generateSkillStream(form);
        assertNotNull(emitter);
    }
    
    @Test
    void testGenerateSkillStreamSuccess() {
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("build a weather skill");
        form.setSelectedMcpTools(List.of(Map.of("name", "tool1")));
        
        SseEmitter emitter = controller.generateSkillStream(form);
        
        assertNotNull(emitter);
        verify(skillGenerationService).generateSkillStream(any(), any());
    }
    
    // ===== optimizePromptStream tests =====
    
    @Test
    void testOptimizePromptStreamWithNullForm() {
        SseEmitter emitter = controller.optimizePromptStream(null);
        assertNotNull(emitter);
        verifyNoInteractions(promptOptimizationService);
    }
    
    @Test
    void testOptimizePromptStreamValidationFailure() {
        PromptOptimizationForm form = new PromptOptimizationForm();
        SseEmitter emitter = controller.optimizePromptStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(promptOptimizationService);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizePromptStreamCallbackOnNext() {
        doAnswer(invocation -> {
            StreamResponseCallback<PromptOptimizationResponse> callback =
                invocation.getArgument(1);
            PromptOptimizationResponse response = new PromptOptimizationResponse();
            response.setDone(true);
            response.setExplanation("optimized prompt");
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(promptOptimizationService).optimizePromptStream(any(), any());
        
        PromptOptimizationForm form = new PromptOptimizationForm();
        form.setPrompt("You are a helpful assistant");
        form.setOptimizationGoal("make concise");
        
        SseEmitter emitter = controller.optimizePromptStream(form);
        assertNotNull(emitter);
        verify(promptOptimizationService).optimizePromptStream(any(), any());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testOptimizePromptStreamCallbackOnError() {
        doAnswer(invocation -> {
            StreamResponseCallback<PromptOptimizationResponse> callback =
                invocation.getArgument(1);
            callback.onError(new RuntimeException("optimization failed"));
            return null;
        }).when(promptOptimizationService).optimizePromptStream(any(), any());
        
        PromptOptimizationForm form = new PromptOptimizationForm();
        form.setPrompt("You are a helpful assistant");
        
        SseEmitter emitter = controller.optimizePromptStream(form);
        assertNotNull(emitter);
    }
    
    // ===== debugPromptStream tests =====
    
    @Test
    void testDebugPromptStreamWithNullForm() {
        SseEmitter emitter = controller.debugPromptStream(null);
        assertNotNull(emitter);
        verifyNoInteractions(promptDebugService);
    }
    
    @Test
    void testDebugPromptStreamValidationFailureMissingPrompt() {
        PromptDebugForm form = new PromptDebugForm();
        form.setUserInput("hello");
        SseEmitter emitter = controller.debugPromptStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(promptDebugService);
    }
    
    @Test
    void testDebugPromptStreamValidationFailureMissingUserInput() {
        PromptDebugForm form = new PromptDebugForm();
        form.setPrompt("You are assistant");
        SseEmitter emitter = controller.debugPromptStream(form);
        assertNotNull(emitter);
        verifyNoInteractions(promptDebugService);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testDebugPromptStreamCallbackOnNext() {
        doAnswer(invocation -> {
            StreamResponseCallback<PromptDebugResponse> callback =
                invocation.getArgument(1);
            PromptDebugResponse response = new PromptDebugResponse();
            response.setDone(true);
            callback.onNext(response);
            callback.onComplete();
            return null;
        }).when(promptDebugService).debugPromptStream(any(), any());
        
        PromptDebugForm form = new PromptDebugForm();
        form.setPrompt("You are a helpful assistant");
        form.setUserInput("hello world");
        
        SseEmitter emitter = controller.debugPromptStream(form);
        assertNotNull(emitter);
        verify(promptDebugService).debugPromptStream(any(), any());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testDebugPromptStreamCallbackOnError() {
        doAnswer(invocation -> {
            StreamResponseCallback<PromptDebugResponse> callback =
                invocation.getArgument(1);
            callback.onError(new RuntimeException("debug failed"));
            return null;
        }).when(promptDebugService).debugPromptStream(any(), any());
        
        PromptDebugForm form = new PromptDebugForm();
        form.setPrompt("You are a helpful assistant");
        form.setUserInput("hello world");
        
        SseEmitter emitter = controller.debugPromptStream(form);
        assertNotNull(emitter);
    }
}
