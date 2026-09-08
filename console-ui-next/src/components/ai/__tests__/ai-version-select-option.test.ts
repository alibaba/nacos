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

import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { AiVersionSelectOption } from '../AiVersionSelectOption';

const labels = {
  latest: 'Latest',
  draft: 'Draft',
  reviewing: 'Reviewing',
  pendingPublish: 'Pending publish',
  rejected: 'Rejected',
};

function renderOption(
  props: Omit<Parameters<typeof AiVersionSelectOption>[0], 'labels'>,
): string {
  return renderToStaticMarkup(createElement(AiVersionSelectOption, { ...props, labels }));
}

describe('AiVersionSelectOption', () => {
  it('shares latest and draft badges across AI resource pages', () => {
    const html = renderOption({ version: '1.1.0', status: 'draft', latest: true });

    expect(html).toContain('1.1.0');
    expect(html).toContain('Latest');
    expect(html).toContain('Draft');
    expect(html).toContain('bg-emerald-100');
    expect(html).toContain('bg-amber-100');
  });

  it('distinguishes pending and rejected review results', () => {
    const pending = renderOption({
      version: '1.1.0',
      status: 'reviewing',
      publishPipelineInfo: JSON.stringify({
        executionId: 'approved-execution',
        status: 'APPROVED',
        pipeline: [],
      }),
    });
    const rejected = renderOption({
      version: '1.1.0',
      status: 'reviewed',
      publishPipelineInfo: JSON.stringify({
        executionId: 'rejected-execution',
        status: 'REJECTED',
        pipeline: [],
      }),
    });

    expect(pending).toContain('Pending publish');
    expect(pending).toContain('bg-teal-100');
    expect(rejected).toContain('Rejected');
    expect(rejected).toContain('bg-red-100');
  });
});
