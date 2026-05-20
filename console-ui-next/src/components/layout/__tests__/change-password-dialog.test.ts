import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../change-password-dialog.tsx'),
  'utf-8',
);

const EN_LOCALE = fs.readFileSync(
  path.resolve(__dirname, '../../../locales/en-US.json'),
  'utf-8',
);

const ZH_LOCALE = fs.readFileSync(
  path.resolve(__dirname, '../../../locales/zh-CN.json'),
  'utf-8',
);

describe('ChangePasswordDialog', () => {
  it('exposes a controlled open/onOpenChange interface', () => {
    expect(SOURCE).toContain('interface ChangePasswordDialogProps {');
    expect(SOURCE).toContain('open: boolean;');
    expect(SOURCE).toContain('onOpenChange: (open: boolean) => void;');
  });

  it('reads the current username from the auth store', () => {
    expect(SOURCE).toContain("import { useAuthStore } from '@/stores/auth-store'");
    expect(SOURCE).toContain('useAuthStore((state) => state.username)');
  });

  it('delegates field validation to the shared helper before submitting', () => {
    expect(SOURCE).toContain(
      "import { validateChangePasswordForm } from '@/lib/change-password'",
    );
    expect(SOURCE).toContain('validateChangePasswordForm(newPassword, confirmPassword)');
    expect(SOURCE).toContain('if (!validation.valid)');
  });

  it('persists the new password through authApi.resetPassword', () => {
    expect(SOURCE).toContain("import { authApi } from '@/api/auth'");
    expect(SOURCE).toContain(
      'authApi.resetPassword({ username, newPassword: newPassword.trim() })',
    );
  });

  it('reports success and closes the dialog on a successful update', () => {
    expect(SOURCE).toContain("toast.success(t('authority.resetPasswordSuccess'))");
    expect(SOURCE).toContain('closeDialog(false)');
  });

  it('clears local state when the dialog is dismissed', () => {
    expect(SOURCE).toContain('const resetFields = ()');
    expect(SOURCE).toContain('setNewPassword(\'\')');
    expect(SOURCE).toContain('setConfirmPassword(\'\')');
    expect(SOURCE).toContain('resetFields();');
  });

  it('renders the change-password title using the shared i18n key', () => {
    expect(SOURCE).toContain("t('header.changePassword')");
    expect(EN_LOCALE).toContain('"changePassword": "Modify Password"');
    expect(ZH_LOCALE).toContain('"changePassword": "修改密码"');
  });
});
