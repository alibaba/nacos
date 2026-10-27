/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { TooltipProvider } from '@/components/ui/tooltip';

const server = vi.hoisted(() => ({
  aiEnabled: true, hubEnabled: false, functionMode: '', version: '',
  startupMode: 'standalone', copilotEnabled: false,
}));
vi.mock('@/stores/server-store', () => ({ useServerStore: () => server }));
vi.mock('@/stores/app-store', () => ({ useAppStore: () => ({ sidebarCollapsed: false }) }));
vi.mock('@/stores/auth-store', () => ({ useAuthStore: () => ({ globalAdmin: false }) }));
vi.mock('@/stores/namespace-store', () => ({
  useNamespaceStore: () => ({ currentNamespace: 'public', namespaceShowName: 'Public' }),
}));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));

const { Sidebar } = await import('../sidebar');

describe('shared resource navigation', () => {
  it.each([false, true])('keeps sharing inside resource management with Hub enabled: %s', hubEnabled => {
    server.hubEnabled = hubEnabled;
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/skill']}>
        <TooltipProvider><Sidebar /></TooltipProvider>
      </MemoryRouter>,
    );
    expect(html).not.toContain('menu.hubSkillRegistry');
    expect(html).not.toContain('/hub/skills');
    expect(html).toContain('menu.skillRegistry');
    expect(html).toContain('menu.promptRegistry');
  });
  it('keeps Skill management highlighted on the shared directory', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/skill/shared/list']}>
        <TooltipProvider><Sidebar /></TooltipProvider>
      </MemoryRouter>,
    );
    expect(html).toMatch(/text-sidebar-primary font-medium bg-sidebar-accent[^>]*>[\s\S]*?menu\.skillRegistry/);
  });
});
