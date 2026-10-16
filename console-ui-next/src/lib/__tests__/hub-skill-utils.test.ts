import { describe, expect, it } from 'vitest';
import {
  activeSourceVersion, distinctHubEntries, hubDetailPath,
  revokeWarning, sourceSkillPath, validHubId, hubErrorMessage,
} from '../hub-skill-utils';
import type { HubSkillDetail, HubSkillSummary } from '@/types/hub-skill';

describe('S5 Hub Skill links and safe identity', () => {
  it('keeps Java long IDs as strings and rejects lossy numbers', () => {
    expect(validHubId('9223372036854775807')).toBe(true);
    expect(validHubId(Number('9223372036854775807'))).toBe(false);
    expect(validHubId('0')).toBe(false);
    expect(validHubId('1.0')).toBe(false);
    expect(validHubId('01')).toBe(false);
    expect(validHubId('9223372036854775808')).toBe(false);
    expect(validHubId('999999999999999999999')).toBe(false);
  });
  it('two same-named Skills must navigate by different hubEntryId', () => {
    const records = [
      { hubEntryId: '901', displayName: 'find-skills' },
      { hubEntryId: '902', displayName: 'find-skills' },
    ] as HubSkillSummary[];
    expect(distinctHubEntries(records)).toBe(true);
    expect(hubDetailPath(records[0].hubEntryId)).not.toBe(hubDetailPath(records[1].hubEntryId));
    expect(distinctHubEntries([...records, records[0]])).toBe(false);
  });
  it('source link is gated by server sourceReadable and has pinned version', () => {
    const detail = {
      hubEntryId: '901', sourceReadable: true, sourceNamespaceId: 'src a',
      sourceName: 'find/skills', sharedVersion: '1.1.0',
    } as HubSkillDetail;
    expect(sourceSkillPath(detail)).toBe('/skill/find%2Fskills?namespaceId=src+a&version=1.1.0');
    expect(sourceSkillPath({ ...detail, sourceReadable: false })).toBe(null);
    expect(sourceSkillPath({ ...detail, sourceNamespaceId: null })).toBe(null);
  });
  it('never silently replaces an invalid deep-linked version with latest', () => {
    const versions = [{ version: '1.0.0' }, { version: '2.0.0' }];
    expect(activeSourceVersion('1.0.0', versions, '2.0.0')).toBe('1.0.0');
    expect(activeSourceVersion('9.9.9', versions, '2.0.0')).toBe('');
    expect(activeSourceVersion(null, versions, '2.0.0')).toBe('2.0.0');
  });
  it('shows automatic unshare warning only for affected version or whole Skill', () => {
    expect(revokeWarning(true)).toBe(true);
    expect(revokeWarning(true, '1.0.0', '1.0.0')).toBe(true);
    expect(revokeWarning(true, '2.0.0', '1.0.0')).toBe(false);
    expect(revokeWarning(false, '1.0.0', '1.0.0')).toBe(false);
  });
  it('recognizes unauthenticated, forbidden, revoked and stale shared versions', () => {
    expect(hubErrorMessage({ response: { status: 401 } }, 'request')).toBe('permission');
    expect(hubErrorMessage({ response: { status: 403 } }, 'request')).toBe('permission');
    expect(hubErrorMessage({ response: { status: 404 } }, 'request')).toBe('unavailable');
    expect(hubErrorMessage({ response: { status: 409 } }, 'request')).toBe('conflict');
    expect(hubErrorMessage({ code: 20005 }, 'request')).toBe('conflict');
    expect(hubErrorMessage({ code: 10001 }, 'request')).toBe('permission');
    expect(hubErrorMessage({ code: 20004 }, 'request')).toBe('unavailable');
    expect(hubErrorMessage({}, 'request')).toBe('request');
  });

  it('missing pin or reserved source namespace cannot produce an ordinary Skill link', () => {
    const base = { sourceReadable: true, sourceNamespaceId: 'ns-a', sourceName: 'skill', sharedVersion: '1.0.0' } as HubSkillDetail;
    expect(sourceSkillPath({ ...base, sharedVersion: '' })).toBeNull();
    expect(sourceSkillPath({ ...base, sourceNamespaceId: '__nacos_ai_hub__' })).toBeNull();
  });
  it('Axios transport codes do not obscure Nacos business error codes', () => {
    expect(hubErrorMessage({ code: 'ERR_BAD_REQUEST', response: { data: { code: 20005 } } }, 'request')).toBe('conflict');
  });
});
