import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';
import { getMcpVersionActions, isMcpLifecycleUnavailable } from '../mcp-lifecycle';

const DETAIL_SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');
const ZH_MESSAGES = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, '../../../locales/zh-CN.json'), 'utf-8'),
);

describe('MCP lifecycle actions', () => {
  it('maps every version state to only valid UI actions', () => {
    expect(getMcpVersionActions('draft', null, true)).toEqual([
      'editDraft',
      'submit',
      'deleteDraft',
    ]);
    expect(getMcpVersionActions('reviewing', { executionId: '1', status: 'IN_PROGRESS', pipeline: [] }, true))
      .toEqual(['publish']);
    expect(getMcpVersionActions('reviewed', { executionId: '1', status: 'APPROVED', pipeline: [] }, true)).toEqual([
      'submit',
      'publish',
      'redraft',
    ]);
    expect(getMcpVersionActions('online', null, true)).toEqual(['offline']);
    expect(getMcpVersionActions('offline', null, true)).toEqual(['online']);
  });

  it('offers force publish only to an administrator after rejection', () => {
    const rejected = { executionId: '1', status: 'REJECTED' as const, pipeline: [] };
    expect(getMcpVersionActions('draft', rejected, true)).toEqual([
      'editDraft',
      'submit',
      'deleteDraft',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewed', rejected, true)).toEqual([
      'submit',
      'publish',
      'redraft',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewing', rejected, true)).toEqual([
      'submit',
      'publish',
      'forcePublish',
    ]);
    expect(getMcpVersionActions('reviewed', rejected, false)).not.toContain('forcePublish');
  });

  it('recognizes only the managed-cutover conflict as lifecycle unavailable', () => {
    expect(isMcpLifecycleUnavailable({
      response: {
        status: 409,
        data: { message: 'unavailable before LIFECYCLE_MANAGED cutover' },
      },
    })).toBe(true);
    expect(isMcpLifecycleUnavailable({ response: { status: 409, data: { message: 'conflict' } } }))
      .toBe(false);
    expect(isMcpLifecycleUnavailable({ response: { status: 500 } })).toBe(false);
  });

  it('refreshes the local version summaries immediately after deleting a draft', () => {
    expect(DETAIL_SOURCE).toContain('setVersionSummaries(remaining);');
    expect(DETAIL_SOURCE).toMatch(
      /setVersionSummaries\(remaining\);\s*if \(remaining\.length === 0\)/,
    );
  });

  it('keeps governance and version labels in the detail sidebar', () => {
    const sidebarStart = DETAIL_SOURCE.indexOf('{/* Right column - 1/3 */}');
    const governance = DETAIL_SOURCE.indexOf("{t('mcp.governance')}");
    const versionHistory = DETAIL_SOURCE.indexOf('{/* ===== Version History Sheet ===== */}');

    expect(sidebarStart).toBeGreaterThan(-1);
    expect(governance).toBeGreaterThan(sidebarStart);
    expect(governance).toBeLessThan(versionHistory);
  });

  it('names online and offline actions as version operations', () => {
    expect(ZH_MESSAGES.mcp.lifecycleAction.online).toBe('版本上线');
    expect(ZH_MESSAGES.mcp.lifecycleAction.offline).toBe('版本下线');
  });
});
