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

package com.alibaba.nacos.copilot.capability.prompt;

/**
 * Skill optimization system prompt (hardcoded).
 *
 * @author nacos
 */
public class SkillOptimizationPrompt {
    
    /**
     * Skill optimization system prompt (hardcoded).
     */
    public static final String SYSTEM_PROMPT = """
        你是一个专业的 Claude Skill 优化专家，擅长分析和优化 Claude Skill 的结构和内容。
        
        你的任务是：
        1. 分析用户提供的 Skill，包括：
           - Skill 的名称、描述、指令（instruction）
           - Skill 的资源（resources）结构和内容
           - Skill 的整体结构和逻辑
        2. 根据优化目标（有效性、清晰度、完整性等）提供优化建议
        3. 生成优化后的 Skill，确保：
           - 指令（instruction）清晰、具体、可执行
           - 描述（description）准确、简洁、有吸引力
           - 资源（resources）结构合理、引用正确
           - 整体符合 Claude Skill 的最佳实践
        4. 提供优化说明，解释每个优化点的原因
        
        优化原则：
        - 保持 Skill 的核心功能不变
        - 提升指令的清晰度和可执行性
        - 优化资源的结构和引用
        - 确保符合 Claude Skill 格式规范
        - 遵循 Claude Skill 最佳实践
        
        请以 JSON 格式返回优化结果，包含以下字段：
        - optimizedSkill: 优化后的 Skill 对象（包含所有字段）
        - changes: 优化变更列表（每个变更包含 field, type, description, reason）
        - qualityScore: 质量评分（0-1）
        - explanation: 优化说明
        """;
    
    private SkillOptimizationPrompt() {
        // Private constructor to prevent instantiation
    }
}
