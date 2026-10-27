import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosHeaders, type AxiosResponseTransformer, type InternalAxiosRequestConfig } from 'axios';

vi.mock('../client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
import client from '../client';
import { hubSkillApi } from '../hub-skill';

const get = vi.mocked(client.get);
const post = vi.mocked(client.post);
const state = { hubEntryId: '9223372036854775807', metaVersion: '9007199254740993', sharedVersion: '1.0.0', active: true };
const sharedSkill = {
  namespaceId: '__nacos_ai_hub__', name: state.hubEntryId, hubEntryId: state.hubEntryId,
  discoverySource: 'HUB', displayName: 'find-skills', sourceDisplayName: 'team-a',
  owner: 'alice', description: 'Find tools', version: '1.0.0', bizTags: '["search,tools","coding"]',
  updateTime: 123, downloadCount: 17,
};
// The shared Axios interceptor returns the Nacos body directly.
const respond = (data: unknown) => get.mockResolvedValue(data);

beforeEach(() => vi.resetAllMocks());
describe('Hub Console transport contract', () => {
  it('reuses the existing Skill list with the shared namespace and ordinary filters', async () => {
    respond({ code: 0, data: { totalCount: 0, pageItems: [] } });
    await hubSkillApi.list({ query: 'find', bizTag: 'search,tools', pageNo: 2, pageSize: 12 });
    expect(get).toHaveBeenCalledWith('v3/console/ai/skills/list', { params: {
      namespaceId: '__nacos_ai_hub__', skillName: 'find', search: 'blur',
      bizTag: 'search,tools', pageNo: 2, pageSize: 12, orderBy: undefined,
    } });
  });
  it('passes the existing download sort parameter without sorting a page locally', async () => {
    respond({ code: 0, data: { totalCount: 0, pageItems: [] } });
    await hubSkillApi.list({ orderBy: 'download_count', pageNo: 1, pageSize: 12 });
    expect(get).toHaveBeenCalledWith('v3/console/ai/skills/list', { params: {
      namespaceId: '__nacos_ai_hub__', skillName: undefined, search: 'blur',
      bizTag: undefined, pageNo: 1, pageSize: 12, orderBy: 'download_count',
    } });
  });
  it('maps ordinary Skill provenance and preserves server ordering and independent counts', async () => {
    respond({ code: 0, data: { totalCount: 2, pageItems: [sharedSkill, {
      ...sharedSkill, name: '901', hubEntryId: '901', downloadCount: 2,
    }] } });
    const { data } = await hubSkillApi.list({ pageNo: 1, pageSize: 12 });
    expect(data.totalCount).toBe(2);
    expect(data.pageItems.map(item => item.hubEntryId)).toEqual([state.hubEntryId, '901']);
    expect(data.pageItems[0]).toMatchObject({
      displayName: 'find-skills', sharedVersion: '1.0.0', author: 'alice', sharedAt: 123,
      downloadCount: 17, tags: ['search,tools', 'coding'], origin: 'HUB',
    });
  });
  it('reads ordinary Skill detail with the opaque ID and only its pinned version', async () => {
    respond({ code: 0, data: { ...sharedSkill, labels: { latest: '1.0.0', stale: '0.1.0' },
      sourceReadable: false, origin: { mode: 'DIRECT_COPY' },
      versions: [{ version: '1.0.0', status: 'online', downloadCount: 4, commitMsg: 'shared release' }],
    } });
    const { data } = await hubSkillApi.detail(state.hubEntryId);
    expect(get).toHaveBeenCalledWith('v3/console/ai/skills', { params: {
      namespaceId: '__nacos_ai_hub__', skillName: state.hubEntryId,
    } });
    expect(data).toMatchObject({ hubEntryId: state.hubEntryId, sharedVersion: '1.0.0',
      versionStatus: 'online', versionDownloadCount: 4, commitMsg: 'shared release',
      versionLabels: ['latest'], sourceReadable: false, origin: 'HUB' });
  });
  it('pins content through the ordinary version endpoint and checks its returned identity', async () => {
    respond({ code: 0, data: { namespaceId: '__nacos_ai_hub__', name: state.hubEntryId,
      skillMd: '# Find tools', resource: {},
    } });
    const { data } = await hubSkillApi.content(state.hubEntryId, '1.0.0', 'find-skills');
    expect(get).toHaveBeenCalledWith('v3/console/ai/skills/version', { params: {
      namespaceId: '__nacos_ai_hub__', skillName: state.hubEntryId, version: '1.0.0',
    } });
    expect(data).toMatchObject({ hubEntryId: state.hubEntryId, sharedVersion: '1.0.0', skillMd: '# Find tools' });
    respond({ code: 0, data: { namespaceId: 'team-a', name: 'find-skills', skillMd: 'private' } });
    await expect(hubSkillApi.content(state.hubEntryId, '1.0.0', 'find-skills')).rejects.toThrow('Mismatched');
  });
  it('rejects ambiguous or lossy shared identities instead of reading a display name', async () => {
    respond({ code: 0, data: { totalCount: 1, pageItems: [{ ...sharedSkill, name: 'find-skills' }] } });
    await expect(hubSkillApi.list({ pageNo: 1, pageSize: 12 })).rejects.toThrow('identity');
    respond({ code: 0, data: { ...sharedSkill, versions: [{ version: '2.0.0' }] } });
    await expect(hubSkillApi.detail(state.hubEntryId)).rejects.toThrow('version');
    await expect(hubSkillApi.detail('find-skills')).rejects.toThrow('Invalid');
  });
  it('only a never-shared source permits absent null data', async () => {
    respond({ code: 0 });
    expect((await hubSkillApi.sourceShare('ns-a', 'find-skills')).data).toBeNull();
    await expect(hubSkillApi.governanceState('901')).rejects.toThrow('Missing');
    await expect(hubSkillApi.detail('901')).rejects.toThrow('Missing');
    await expect(hubSkillApi.content('901', '1.0.0', 'find-skills')).rejects.toThrow('Missing');
    await expect(hubSkillApi.list({ pageNo: 1, pageSize: 12 })).rejects.toThrow('Missing');
  });
  it('capability requests preserve full long strings and suppress expected permission toasts', async () => {
    respond({ code: 0, data: state });
    expect((await hubSkillApi.governanceState(state.hubEntryId)).data).toEqual(state);
    expect(get).toHaveBeenCalledWith('v3/console/ai/hub/skills/governance-state', {
      params: { hubEntryId: state.hubEntryId }, silentError: true,
    });
  });
  it('rejects state for another entry and lossy numeric optimistic lock versions', async () => {
    respond({ code: 0, data: state });
    await expect(hubSkillApi.governanceState('901')).rejects.toThrow('Mismatched');
    respond({ code: 0, data: { ...state, metaVersion: 9007199254740992 } });
    await expect(hubSkillApi.sourceShare('ns-a', 'find-skills')).rejects.toThrow('Invalid');
  });
  it('HTTP 200 business failures retain denied or conflict codes', async () => {
    for (const code of [10001, 20004, 20005]) {
      respond({ code, message: 'denied or unavailable' });
      await expect(hubSkillApi.governanceState('901')).rejects.toMatchObject({ code });
    }
  });
  it('transport denial is propagated without fallback to an admin identity', async () => {
    const denial = { response: { status: 403 } };
    get.mockRejectedValue(denial);
    await expect(hubSkillApi.governanceState('901')).rejects.toBe(denial);
    expect(post).not.toHaveBeenCalled();
  });
  it('copy conflicts preserve the requested independent target and do not retry overwriting it', async () => {
    post.mockResolvedValue({ code: 20005, message: 'target exists' });
    const target = { hubEntryId: state.hubEntryId, targetNamespaceId: 'tenant-a', targetName: 'renamed', targetVersion: '0.0.1' };
    await expect(hubSkillApi.import(target)).rejects.toMatchObject({ code: 20005 });
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith('v3/console/ai/hub/skills/import', target, { timeout: 60000 });
  });
});

describe('fixed Hub ZIP download', () => {
  it('preserves large entry ids and binary responses with version headers', async () => {
    const blob = new Blob([new Uint8Array([80, 75, 0, 255])], { type: 'application/zip' });
    respond({ blob, hubEntryId: state.hubEntryId, sharedVersion: '1.0.0' });
    expect(await hubSkillApi.download(state.hubEntryId, '1.0.0')).toBe(blob);
    const config = get.mock.calls[0][1]!;
    expect(get.mock.calls[0][0]).toBe('v3/console/ai/skills/version/download');
    expect(config.params).toEqual({ namespaceId: '__nacos_ai_hub__', skillName: state.hubEntryId, version: '1.0.0' });
    expect(config.responseType).toBe('blob');
    expect(config.timeout).toBe(60000);
    const transform = (config.transformResponse as AxiosResponseTransformer[])[0];
    const headers = new AxiosHeaders({
      'x-nacos-hub-entry-id': state.hubEntryId,
      'x-nacos-skill-resolved-version': '1.0.0',
    });
    expect(transform.call(config as InternalAxiosRequestConfig, blob, headers, 200))
      .toEqual({ blob, hubEntryId: state.hubEntryId, sharedVersion: '1.0.0' });
  });
  it('refuses a ZIP for a changed Hub publication instead of saving it under the old version', async () => {
    respond({ blob: new Blob(['zip'], { type: 'application/zip' }), hubEntryId: state.hubEntryId, sharedVersion: '2.0.0' });
    await expect(hubSkillApi.download(state.hubEntryId, '1.0.0')).rejects.toMatchObject({ code: 20005 });
  });
  it('never saves a JSON business denial returned with HTTP 200 as a ZIP', async () => {
    respond({ blob: new Blob([JSON.stringify({ code: 10001, message: 'denied' })], { type: 'application/json' }) });
    await expect(hubSkillApi.download('901', '1.0.0')).rejects.toMatchObject({ code: 10001 });
  });
});


describe('direct Skill copy to an independent namespace draft', () => {
  const request = { sourceNamespaceId: 'tenant-a', sourceName: 'private-skill', sourceVersion: '1.0.0',
    targetNamespaceId: 'tenant-b', targetName: 'renamed', targetVersion: '0.0.1' };
  const created = { targetNamespaceId: 'tenant-b', targetName: 'renamed', draftVersion: '0.0.1',
    status: 'draft', targetResourceId: '9007199254740999', origin: '{}', hubEntryId: null };
  it('uses the existing copy route with exact source and target identities, without first sharing', async () => {
    post.mockResolvedValue({ code: 0, data: created });
    expect((await hubSkillApi.copy(request)).data).toEqual(created);
    expect(post).toHaveBeenCalledOnce();
    expect(post).toHaveBeenCalledWith('v3/console/ai/hub/skills/copy', request, { timeout: 60000 });
  });
  it('never reports a draft for a different target or version as a successful copy', async () => {
    for (const change of [{ targetNamespaceId: 'tenant-a' }, { targetName: 'other' }, { draftVersion: '9.0.0' },
      { status: 'online' }, { targetResourceId: Number('9007199254740999') }, { hubEntryId: '901' }]) {
      post.mockResolvedValue({ code: 0, data: { ...created, ...change } });
      await expect(hubSkillApi.copy(request)).rejects.toThrow('Mismatched');
    }
  });
  it('does not retry a target conflict as an overwrite or a share operation', async () => {
    post.mockResolvedValue({ code: 20005, message: 'target exists' });
    await expect(hubSkillApi.copy(request)).rejects.toMatchObject({ code: 20005 });
    expect(post).toHaveBeenCalledOnce();
  });
});
