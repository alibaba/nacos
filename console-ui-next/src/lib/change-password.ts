/*
 * Validation helpers for the self-service change-password flow rendered in the
 * console header. Extracted so the rules can be unit-tested without rendering
 * the dialog component.
 */
export interface ChangePasswordValidation {
  valid: boolean;
  errorKey?: string;
}

export function validateChangePasswordForm(
  newPassword: string,
  confirmPassword: string,
): ChangePasswordValidation {
  if (!newPassword || !newPassword.trim()) {
    return { valid: false, errorKey: 'authority.passwordRequired' };
  }
  if (newPassword !== confirmPassword) {
    return { valid: false, errorKey: 'authority.passwordMismatch' };
  }
  return { valid: true };
}
