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
import { ResourceSharingPanel } from '../ResourceSharingPanel';
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }));
const props = { title: 'Skill sharing management', version: '1.0.0', share: null,
  canWrite: true, eligible: true, copyable: true, loading: false, busy: false, error: '',
  eligibilityHint: 'only online', lifecycleHint: 'offline withdraws', copyHint: 'select published version',
  onRefresh: vi.fn(), onShare: vi.fn(), onUnshare: vi.fn(), onCopy: vi.fn() };
function button(html: string, label: string) {
  return html.match(/<button\b[\s\S]*?<\/button>/g)?.find(item => item.includes(label)) || '';
}
describe('shared source detail management card', () => {
  it('displays sharing and independent copying together in the same card', () => {
    const html = renderToStaticMarkup(<ResourceSharingPanel {...props} />);
    expect(html).toContain('data-slot="card"');
    expect(html).toContain('Skill sharing management');
    expect(html).toContain('hub.notShared');
    expect(html).toContain('hubResource.share');
    expect(html).toContain('hubResource.copy');
  });
  it('an unreadable governance state does not remove the independent source-READ copy action', () => {
    const html = renderToStaticMarkup(<ResourceSharingPanel {...props} canWrite={false} />);
    expect(html).toContain('hubResource.copy');
    expect(html).not.toContain('hub.notShared');
    expect(html).not.toContain('hubResource.share<');
    expect(html).not.toContain('common.refresh');
  });
  it('keeps a visible disabled copy action for a draft and explains the required version', () => {
    const html = renderToStaticMarkup(<ResourceSharingPanel {...props} eligible={false} copyable={false} />);
    expect(button(html, 'hubResource.copy')).toContain('disabled=""');
    expect(html).toContain('select published version');
    expect(html).toContain('only online');
    expect(html).not.toContain('hubResource.share<');
  });
  it('retains the exact current shared version and cancellation without offering redundant adjustment', () => {
    const html = renderToStaticMarkup(<ResourceSharingPanel {...props}
      share={{ hubEntryId: '9007199254740999', metaVersion: '2', active: true, sharedVersion: '1.0.0' }} />);
    expect(html).toContain('1.0.0'); expect(html).toContain('hub.unshare');
    expect(html).not.toContain('hub.adjust'); expect(html).toContain('hubResource.copy');
  });
  it('allows copying while a sharing-only permission request failed, without permitting share mutations', () => {
    const html = renderToStaticMarkup(<ResourceSharingPanel {...props} error="share permission denied" />);
    expect(html).toContain('role="alert"');
    expect(button(html, 'hubResource.share')).toContain('disabled=""');
    expect(button(html, 'hubResource.copy')).not.toContain('disabled=""');
  });
});
