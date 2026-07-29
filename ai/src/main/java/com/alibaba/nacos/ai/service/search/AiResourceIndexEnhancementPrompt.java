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

package com.alibaba.nacos.ai.service.search;

/**
 * AI resource index enhancement prompt.
 *
 * @author nacos
 */
final class AiResourceIndexEnhancementPrompt {
    
    static final String SYSTEM_PROMPT =
        """
            Generate compact bilingual retrieval-enrichment JSON for the given registry resource.
            
            Goal: improve hybrid keyword + vector recall for the exact resource by simulating what
            humans or agents may type when searching in Chinese or English.
            
            Use only the provided source content, metadata, snippets, tags, capabilities, inputTypes,
            outputTypes, representative queries, and limitations. You may translate source-backed
            capabilities between Chinese and English for retrieval, but must not infer unsupported
            capabilities. If evidence is weak, keep the item generic or omit it.
            
            Retrieval design:
            - First identify the user jobs supported by the source: what the user wants to accomplish,
            what input they have, what output they need, and which workflow or scenario they are in.
            - Then simulate how users or agents would search when they need this resource but may not
            know its formal name. Include exact-name searches only when those names are likely useful.
            - Do not output the analysis steps. Return only the JSON fields in the schema.
            
            Field rules:
            - summary: one compact bilingual summary of what the resource actually does. Mention the
            main task, supported inputs, outputs, and target use case when present.
            - searchIntents: natural search phrases for supported user jobs. Include task-intent,
            input-to-output, scenario, workflow, and how-to phrases that users or agents would type.
            - searchTerms: standalone high-value search terms. Include exact source names, product,
            API, framework, model, protocol, competitor, file-format names, colloquial terms, domain
            nouns, and input/output asset words when they are useful and source-supported.
            
            Bilingual coverage:
            - summary must describe the resource in both Chinese and English.
            - searchIntents must include natural Chinese and English ways users or agents would search
            for the supported jobs. Do not require one-to-one translations; cover the same major user
            jobs in both languages when enough evidence exists.
            - searchTerms must include source-language exact names and natural counterpart search terms
            in the other language when useful for retrieval.
            - Do not let one language consume all items. If the source is mostly English, add Chinese
            recall phrases; if the source is mostly Chinese, add English recall phrases.
            
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
            - Return 6 to 10 searchIntents and 6 to 10 searchTerms when enough evidence exists.
            - Put the most likely and highest-recall search phrases first.
            - Keep every item standalone, concise, and deduplicated.
            - Prefer user and agent search language over internal implementation terms.
            - Include formal feature names only when they are likely search terms.
            - Do not add unrelated tools, modalities, vendors, resource types, or unsupported capabilities.
            
            Return strict JSON only. No markdown, no comments, no extra keys.
            
            Schema:
            {
              "summary": "string",
              "searchIntents": ["string"],
              "searchTerms": ["string"]
            }
            """;
    
    private AiResourceIndexEnhancementPrompt() {
    }
}
