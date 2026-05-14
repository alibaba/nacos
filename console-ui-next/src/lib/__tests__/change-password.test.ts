import { describe, expect, it } from 'vitest';

import { validateChangePasswordForm } from '../change-password';

describe('validateChangePasswordForm', () => {
  it('rejects an empty new password', () => {
    expect(validateChangePasswordForm('', '')).toEqual({
      valid: false,
      errorKey: 'authority.passwordRequired',
    });
  });

  it('rejects a whitespace-only new password', () => {
    expect(validateChangePasswordForm('   ', '   ')).toEqual({
      valid: false,
      errorKey: 'authority.passwordRequired',
    });
  });

  it('rejects a confirm password that does not match', () => {
    expect(validateChangePasswordForm('Secret1!', 'Secret2!')).toEqual({
      valid: false,
      errorKey: 'authority.passwordMismatch',
    });
  });

  it('accepts a non-empty new password that matches the confirmation', () => {
    expect(validateChangePasswordForm('Secret1!', 'Secret1!')).toEqual({
      valid: true,
    });
  });

  it('treats the password as significant when only the confirmation is blank', () => {
    expect(validateChangePasswordForm('Secret1!', '')).toEqual({
      valid: false,
      errorKey: 'authority.passwordMismatch',
    });
  });
});
