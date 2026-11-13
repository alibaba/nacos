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

import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { HubSkillVersionInfo } from '../HubSkillVersionInfo';
import type { HubSkillDetail } from '@/types/hub-skill';

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
const detail: HubSkillDetail = {
  hubEntryId: '901', displayName: 'find-skills', sharedVersion: '1.0.0', origin: 'HUB',
  sourceReadable: false, author: 'pinned-author', versionStatus: 'online',
  downloadCount: 57, versionDownloadCount: 7, commitMsg: 'Fixed version instructions',
  tags: ['discovery'], versionLabels: ['stable'],
};
function render(value = detail, downloading = false) {
  return renderToStaticMarkup(<HubSkillVersionInfo detail={value} onDownload={() => {}} downloading={downloading} />);
}
describe('Hub fixed-version information', () => {
  it('shows independent shared downloads without source counters and retains pinned version metadata', () => {
    const html = render();
    expect(html).toContain('pinned-author');
    expect(html).toContain('hub.downloads');
    expect(html).not.toContain('skill.versionDownloads');
    expect(html).toContain('>57<');
    expect(html).not.toContain('>7<');
    expect(html).toContain('Fixed version instructions');
    expect(html).not.toContain('skill.versionStatus.online');
    expect(html).not.toContain('skill.status');
    expect(html).toContain('find-skills-1.0.0.zip');
  });
  it('shows the exact shared CLI install command and labels without edit controls', () => {
    const html = render();
    expect(html).toContain('discovery');
    expect(html).toContain('stable');
    expect(html).not.toContain('latest');
    expect(html).not.toContain('textarea');
    expect(html).toContain('nacos-cli skill-get find-skills 901 --version 1.0.0');
    expect(html).not.toContain('__nacos_ai_hub__');
    expect(html).not.toContain('lucide-pencil');
  });
  it('renders empty labels and absent source metadata gracefully and prevents repeated download clicks', () => {
    const html = render({ ...detail, tags: [], versionLabels: [], author: null, commitMsg: null, versionDownloadCount: null }, true);
    expect(html).toContain('skill.noBizTags');
    expect(html).toContain('common.versionLabels.noLabels');
    expect(html).toContain('disabled');
    expect(html).not.toContain('undefined');
  });
});
