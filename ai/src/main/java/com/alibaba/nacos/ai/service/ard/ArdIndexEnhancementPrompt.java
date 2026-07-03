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

package com.alibaba.nacos.ai.service.ard;

/**
 * ARD index enhancement prompt.
 *
 * @author nacos
 */
final class ArdIndexEnhancementPrompt {
    
    static final String SYSTEM_PROMPT =
        """
            Generate compact bilingual retrieval-enrichment JSON for the given registry resource.
            
            Goal: improve hybrid keyword + vector recall for the exact resource, especially user search phrases that may not appear in formal names.
            
            Use only the provided source content, metadata, snippets, tags, capabilities,
            inputTypes, outputTypes, representative queries, and limitations. Do not infer
            unsupported capabilities. If evidence is weak, keep the item generic or omit it.
            
            Field rules:
            - summary: one compact bilingual summary of what the resource actually does.
            Mention the main task, supported inputs, outputs, and target use case when present.
            - bilingualAliases: English and Chinese names users may use for this resource or domain.
            Prefer noun phrases and common aliases. Do not put full task queries here.
            - capabilitySynonyms: standalone capability, intent, input, and output phrases supported
            by the source. Include colloquial terms, formal terms, asset words, and task-intent phrases.
            - exampleQueries: natural search queries a user may type. Use both Chinese and English.
            Queries should be concrete, short, and directly supported by the source.
            
            Phrase selection:
            - Prefer phrases grounded in explicit source evidence.
            - Include input/output asset words only when the source supports those assets, such as
            image, audio, video, document, API, model, agent, skill, prompt, dataset, config,
            or workflow.
            - Include task-intent phrases only when the source clearly supports that task.
            - Do not add adjacent capabilities just because they are common in the same product category.
            - Do not convert limitations, negative cases, or "notFor" content into positive capabilities.
            - Do not add product, platform, vendor, or framework names unless they explicitly appear in
             the source content or source metadata.
            
            Ranking and quality:
            - Put the most common and highest-recall user terms first.
            - Keep every array item standalone, concise, and deduplicated.
            - Prefer user language over internal implementation terms.
            - Include formal feature names only when they are likely search terms.
            - Do not add unrelated tools, modalities, vendors, resource types, or unsupported capabilities.
            
            Return strict JSON only. No markdown, no comments, no extra keys.
            
            Schema:
            {
              "summary": "string",
              "bilingualAliases": ["string"],
              "capabilitySynonyms": ["string"],
              "exampleQueries": ["string"]
            }
            """;
    
    private ArdIndexEnhancementPrompt() {
    }
}
