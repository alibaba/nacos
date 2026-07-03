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
            
            Goal: improve hybrid keyword + vector recall for the exact resource with bilingual
            search text that humans or agents may type in Chinese or English.
            
            Use only the provided source content, metadata, snippets, tags, capabilities, inputTypes,
            outputTypes, representative queries, and limitations. You may translate source-backed
            capabilities between Chinese and English for retrieval, but must not infer unsupported
            capabilities. If evidence is weak, keep the item generic or omit it.
            
            Bilingual expansion:
            - Do not only copy the source language. If the source is English, generate natural Chinese
            search phrases for supported capabilities. If the source is Chinese, generate natural
            English search phrases for supported capabilities.
            - For each core source-backed capability, include both formal and colloquial search
            phrases in Chinese and English when enough evidence exists.
            - Include common user-intent phrases derived from supported inputs, outputs, and actions.
            Do not stop at formal names or literal translations.
            - When a source capability maps to a target-language term whose primary dictionary meaning
            is broader but whose search-intent meaning clearly matches the capability, include the
            colloquial high-recall term. Recall > precision at the retrieval stage; disambiguation
            is handled downstream.
            - Preserve original product, API, framework, model, protocol, and file-format names when
            they appear in the source.
            
            Translation quality:
            - Avoid awkward literal translations or word-by-word calques. When translating a technical
            phrase, use natural search phrases in the target language while preserving the
            source-backed input, output, and action semantics.
            - Apply a native-speaker search-box check to every phrase. If a phrase sounds like a
            literal translation or a native user would not type it into search, replace it with a
            more natural target-language search phrase.
            - Do not replace high-recall colloquial search phrases with overly formal terms when the
            colloquial phrases are directly grounded in the source.
            - Prefer specific search phrases over broad single words.
            
            Field rules:
            - summary: one compact bilingual summary of what the resource actually does. Mention the
            main task, supported inputs, outputs, and target use case when present.
            - bilingualAliases: common Chinese and English resource/domain names. Prefer short noun
            phrases and common aliases. Do not force unnatural translated aliases; put task phrases
            under capabilitySynonyms or exampleQueries instead.
            - capabilitySynonyms: standalone capability, input-output, asset, and action phrases
            supported by the source. Keep a balanced mix of formal terms, colloquial terms, and
            task-intent phrases.
            - exampleQueries: natural search queries a human or agent may type in Chinese or English.
            Queries should be concrete, short, and directly supported by the source. Include at least
            2 queries using the most common colloquial search terms for the resource's primary task,
            even if those terms are semantically broader than the formal capability name.
            
            Phrase selection:
            - Prefer phrases grounded in explicit source evidence.
            - Include input/output asset words only when the source supports those assets, such as
            image, audio, video, document, API, model, agent, skill, prompt, dataset, config, or workflow.
            - Include task-intent phrases only when the source clearly supports that task.
            - Do not add adjacent capabilities just because they are common in the same product category.
            - Do not convert limitations, negative cases, or "notFor" content into positive capabilities.
            - Do not add product, platform, vendor, or framework names unless they explicitly appear
            in the source content or source metadata.
            
            Ranking and size:
            - Return about 6 to 8 items per array when enough evidence exists.
            - The first items are the most important. Put a balanced mix early: common formal names,
            colloquial search phrases, and input-output/action phrases.
            - Do not spend all early items on formal domain labels.
            - Keep every array item standalone, concise, and deduplicated.
            - Prefer user and agent search language over internal implementation terms.
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
