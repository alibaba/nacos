import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { authApi } from '@/api/auth';
import { useAuthStore } from '@/stores/auth-store';
import { validateChangePasswordForm } from '@/lib/change-password';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

interface ChangePasswordDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ChangePasswordDialog({ open, onOpenChange }: ChangePasswordDialogProps) {
  const { t } = useTranslation();
  const username = useAuthStore((state) => state.username) || '';
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [saving, setSaving] = useState(false);

  const resetFields = () => {
    setNewPassword('');
    setConfirmPassword('');
  };

  const closeDialog = (next: boolean) => {
    if (!next) {
      resetFields();
    }
    onOpenChange(next);
  };

  const handleSubmit = async () => {
    const validation = validateChangePasswordForm(newPassword, confirmPassword);
    if (!validation.valid) {
      toast.error(t(validation.errorKey ?? 'authority.passwordRequired'));
      return;
    }
    setSaving(true);
    try {
      await authApi.resetPassword({ username, newPassword: newPassword.trim() });
      toast.success(t('authority.resetPasswordSuccess'));
      closeDialog(false);
    } catch {
      // Error toasts are already handled by the global axios interceptor.
    } finally {
      setSaving(false);
    }
  };

  const submitDisabled = saving || !newPassword || newPassword !== confirmPassword;

  return (
    <Dialog open={open} onOpenChange={closeDialog}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('header.changePassword')}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label>{t('authority.username')}</Label>
            <Input value={username} disabled />
          </div>
          <div className="flex flex-col gap-2">
            <Label>
              {t('authority.newPassword')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <Input
              type="password"
              placeholder={t('authority.newPasswordPlaceholder')}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label>
              {t('authority.confirmPassword')}
              <span className="text-destructive ml-1">*</span>
            </Label>
            <Input
              type="password"
              placeholder={t('authority.newPasswordPlaceholder')}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
            {confirmPassword && newPassword !== confirmPassword && (
              <p className="text-xs text-destructive">{t('authority.passwordMismatch')}</p>
            )}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => closeDialog(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={submitDisabled}>
            {saving ? t('common.loading') : t('common.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
