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
import { CreateDraftFromVersionButton } from '../CreateDraftFromVersionButton';

function renderButton(props: Parameters<typeof CreateDraftFromVersionButton>[0]) {
  return renderToStaticMarkup(createElement(
    TooltipProvider,
    null,
    createElement(CreateDraftFromVersionButton, props),
  ));
}

describe('CreateDraftFromVersionButton', () => {
  it('renders the shared available action', () => {
    const html = renderButton({ label: 'Create from this version', onClick: vi.fn() });

    expect(html).toContain('Create from this version');
    expect(html).not.toContain('disabled=""');
  });

  it('renders a divided and disabled action while another draft exists', () => {
    const html = renderButton({
      label: 'Create from this version',
      blocked: true,
      blockedMessage: 'A draft already exists',
      divider: true,
      onClick: vi.fn(),
    });

    expect(html).toContain('Create from this version');
    expect(html).toContain('disabled=""');
    expect(html).toContain('w-px');
  });
});
