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
            
            Goal: improve hybrid keyword + vector recall for the exact resource by simulating what
            humans or agents may type when searching in Chinese or English.
            
            Use only the provided source content, metadata, snippets, tags, capabilities, inputTypes,
            outputTypes, representative queries, and limitations. You may translate source-backed
            capabilities between Chinese and English for retrieval, but must not infer unsupported
            capabilities. If evidence is weak, keep the item generic or omit it.
            
            Search simulation:
            - Imagine 12 to 16 different searchers trying to find this resource. They may include a
            developer who knows an exact model or API name, a user who describes the task casually, a
            Chinese user typing short keywords, an English user typing a how-to query, and an agent
            selecting a tool for a workflow.
            - List what each searcher would type. Mix formal names, colloquial nouns, short keywords,
            input/output asset words, task-intent phrases, how-to queries, workflow phrases, and
            alternative-tool searches when supported by the source.
            - Support both Chinese and English recall. If the source is English, generate natural
            Chinese search phrases for supported capabilities. If the source is Chinese, generate
            natural English search phrases for supported capabilities.
            - Preserve original product, API, framework, model, protocol, competitor, and file-format
            names when they appear in the source and are likely search terms.
            
            Field rules:
            - summary: one compact bilingual summary of what the resource actually does. Mention the
            main task, supported inputs, outputs, and target use case when present.
            - searchPhrases: a flat list of search terms, phrases, and short queries. Do not classify
            them as aliases, synonyms, or examples. Maximize lexical coverage while keeping each item
            directly grounded in source-supported capabilities.
            
            Phrase selection:
            - Prefer phrases grounded in explicit source evidence.
            - Do not only output formal labels. Include colloquial phrases and short keywords that
            users would naturally type when they only know the task, input, or desired output.
            - Include input/output asset words only when the source supports those assets, such as
            image, audio, video, document, API, model, agent, skill, prompt, dataset, config, or workflow.
            - Include task-intent phrases only when the source clearly supports that task.
            - Avoid awkward literal translations or word-by-word calques. Apply a native-speaker
            search-box check; if a phrase sounds unnatural, replace it with a phrase a native user
            would actually type.
            - Prefer specific search phrases over broad single words, but include high-recall
            colloquial short keywords when they are directly grounded in the source.
            - Do not add adjacent capabilities just because they are common in the same product category.
            - Do not convert limitations, negative cases, or "notFor" content into positive capabilities.
            - Do not add product, platform, vendor, or framework names unless they explicitly appear
            in the source content or source metadata.
            
            Ranking and size:
            - Return 12 to 16 searchPhrases when enough evidence exists.
            - Put the most likely and highest-recall search phrases first.
            - Keep every item standalone, concise, and deduplicated.
            - Prefer user and agent search language over internal implementation terms.
            - Include formal feature names only when they are likely search terms.
            - Do not add unrelated tools, modalities, vendors, resource types, or unsupported capabilities.
            
            Return strict JSON only. No markdown, no comments, no extra keys.
            
            Schema:
            {
              "summary": "string",
              "searchPhrases": ["string"]
            }
            """;
    
    private ArdIndexEnhancementPrompt() {
    }
}
