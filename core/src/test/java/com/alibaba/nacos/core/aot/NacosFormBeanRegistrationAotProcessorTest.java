/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.aot;

import com.alibaba.nacos.api.model.NacosForm;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosFormBeanRegistrationAotProcessorTest {
    
    private final NacosFormBeanRegistrationAotProcessor processor =
        new NacosFormBeanRegistrationAotProcessor();
    
    @Test
    void testRegistersInheritedAndOptionalBindingWithoutCreatingBeans() throws Exception {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("controller", new RootBeanDefinition(FormController.class));
        BeanRegistrationAotContribution contribution =
            processor.processAheadOfTime(RegisteredBean.of(factory, "controller"));
        assertNotNull(contribution);
        RuntimeHints hints = new RuntimeHints();
        GenerationContext generationContext = mock(GenerationContext.class);
        when(generationContext.getRuntimeHints()).thenReturn(hints);
        contribution.applyTo(generationContext, mock(BeanRegistrationCode.class));
        assertTrue(RuntimeHintsPredicates.reflection()
            .onConstructorInvocation(ChildForm.class.getDeclaredConstructor()).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection()
            .onMethodInvocation(BaseForm.class.getMethod("getSrcUser")).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection()
            .onMethodInvocation(BaseForm.class.getMethod("setSrcUser", String.class)).test(hints));
        assertTrue(RuntimeHintsPredicates.reflection()
            .onMethodInvocation(ChildForm.class.getMethod("setGroupName", String.class))
            .test(hints));
        assertFalse(factory.containsSingleton("controller"));
    }
    
    @Test
    void testIgnoresNonControllerBeans() {
        assertNull(contributionFor(UnrelatedService.class));
    }
    
    @Test
    void testIgnoresNonEndpointFormsAndUnrelatedParameters() {
        assertNull(contributionFor(UnrelatedController.class));
    }
    
    @Test
    void testDiscoveredOnlyThroughAotFactories() {
        assertTrue(AotServices.factories(getClass().getClassLoader())
            .load(BeanRegistrationAotProcessor.class).stream()
            .anyMatch(NacosFormBeanRegistrationAotProcessor.class::isInstance));
    }
    
    private BeanRegistrationAotContribution contributionFor(Class<?> beanClass) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("candidate", new RootBeanDefinition(beanClass));
        return processor.processAheadOfTime(RegisteredBean.of(factory, "candidate"));
    }
    
    @RestController
    static class FormController {
        
        FormController() {
            throw new AssertionError("AOT must not instantiate controllers");
        }
        
        @PostMapping("/form")
        public void publish(ChildForm form) {
        }
    }
    
    static class UnrelatedService {
        
        @PostMapping("/form")
        public void publish(ChildForm form) {
        }
    }
    
    @RestController
    static class UnrelatedController {
        
        @PostMapping("/text")
        public void publish(String value) {
        }
        
        public void helper(ChildForm form) {
        }
    }
    
    public static class BaseForm implements NacosForm {
        
        private static final long serialVersionUID = 1L;
        
        private String srcUser;
        
        @Override
        public void validate() {
        }
        
        public String getSrcUser() {
            return srcUser;
        }
        
        public void setSrcUser(String srcUser) {
            this.srcUser = srcUser;
        }
    }
    
    public static class ChildForm extends BaseForm {
        
        private static final long serialVersionUID = 1L;
        
        private String groupName;
        
        public String getGroupName() {
            return groupName;
        }
        
        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }
    }
}
