import { describe, expect, it } from 'vitest';

import { canResubmitReview } from '../version-lifecycle';

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
