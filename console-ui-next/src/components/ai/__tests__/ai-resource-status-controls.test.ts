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
import { describe, expect, it, vi } from 'vitest';

import { TooltipProvider } from '@/components/ui/tooltip';
import { AiResourceStatusControls } from '../AiResourceStatusControls';

function renderControls(props: Parameters<typeof AiResourceStatusControls>[0]) {
  return renderToStaticMarkup(createElement(
    TooltipProvider,
    null,
    createElement(AiResourceStatusControls, props),
  ));
}

describe('AiResourceStatusControls', () => {
  it('renders the common enabled, scope, and visibility layout', () => {
    const html = renderControls({
      enabled: true,
      scope: 'PUBLIC',
      enabledLabel: 'Enabled',
      disabledLabel: 'Disabled',
      publicLabel: 'Public',
      privateLabel: 'Private',
      onEnabledChange: vi.fn(),
      onScopeChange: vi.fn(),
      visibilityLabel: 'Permissions',
      visibilityTooltip: 'Manage permissions',
      onVisibilityClick: vi.fn(),
    });

    expect(html).toContain('Enabled');
    expect(html).toContain('Public');
    expect(html).toContain('Permissions');
    expect(html).toContain('data-state="checked"');
    expect(html).toContain('w-px');
  });

  it('supports a read-only scope while keeping the enabled control interactive', () => {
    const html = renderControls({
      enabled: false,
      scope: 'PRIVATE',
      enabledLabel: 'Enabled',
      disabledLabel: 'Disabled',
      publicLabel: 'Public',
      privateLabel: 'Private',
      scopeDisabled: true,
      onEnabledChange: vi.fn(),
    });

    expect(html).toContain('Disabled');
    expect(html).toContain('Private');
    expect(html).toContain('disabled=""');
    expect(html).not.toContain('Permissions');
  });
});
