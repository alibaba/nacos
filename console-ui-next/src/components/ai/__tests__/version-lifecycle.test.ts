import { describe, expect, it } from 'vitest';

import { canForcePublish, canResubmitReview } from '../version-lifecycle';

describe('canForcePublish', () => {
  const rejected = { status: 'REJECTED' };

  it('allows only administrators to bypass a current rejected review', () => {
    expect(canForcePublish('draft', rejected, true)).toBe(true);
    expect(canForcePublish('reviewing', rejected, true)).toBe(true);
    expect(canForcePublish('reviewed', rejected, true)).toBe(true);
    expect(canForcePublish('reviewed', rejected, false)).toBe(false);
  });

  it('does not offer force publish before rejection or for serving versions', () => {
    expect(canForcePublish('draft', null, true)).toBe(false);
    expect(canForcePublish('reviewing', { status: 'IN_PROGRESS' }, true)).toBe(false);
    expect(canForcePublish('reviewed', { status: 'APPROVED' }, true)).toBe(false);
    expect(canForcePublish('online', rejected, true)).toBe(false);
    expect(canForcePublish('offline', rejected, true)).toBe(false);
  });

  it('does not reuse a historical rejection for a redrafted version', () => {
    expect(canForcePublish('draft', { status: 'REJECTED', historical: true }, true)).toBe(false);
  });
});

describe('canResubmitReview', () => {
  it('allows reviewed versions to start another review', () => {
    expect(canResubmitReview('reviewed', null)).toBe(true);
    expect(canResubmitReview('reviewed', { status: 'APPROVED' })).toBe(true);
    expect(canResubmitReview('reviewed', { status: 'REJECTED' })).toBe(true);
  });

  it('allows a reviewing version with a current terminal result to recover', () => {
    expect(canResubmitReview('reviewing', { status: 'APPROVED' })).toBe(true);
    expect(canResubmitReview('reviewing', { status: 'REJECTED' })).toBe(true);
  });

  it('keeps active or historical reviews idempotent', () => {
    expect(canResubmitReview('reviewing', null)).toBe(false);
    expect(canResubmitReview('reviewing', { status: 'IN_PROGRESS' })).toBe(false);
    expect(
      canResubmitReview('reviewing', { status: 'REJECTED', historical: true }),
    ).toBe(false);
  });

  it('rejects other lifecycle states', () => {
    expect(canResubmitReview('draft', null)).toBe(false);
    expect(canResubmitReview('online', null)).toBe(false);
    expect(canResubmitReview('offline', null)).toBe(false);
  });
});
