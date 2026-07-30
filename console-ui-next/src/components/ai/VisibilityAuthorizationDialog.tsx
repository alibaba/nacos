import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { authApi, type VisibilityAuthorizationRequest } from '@/api/auth';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

type VisibilityAction = VisibilityAuthorizationRequest['action'];
type VisibilityOperation = 'grant' | 'revoke';

interface VisibilityAuthorizationDialogProps {
  open: boolean;
  namespaceId: string;
  resourceType: string;
  resourceName: string;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void | Promise<void>;
}

function getErrorMessage(error: unknown): string {
  const response = (error as { response?: { data?: { data?: unknown; message?: unknown } } })
    ?.response;
  const detail = response?.data?.data;
  if (typeof detail === 'string' && detail.trim()) {
    return detail;
  }
  const message = response?.data?.message;
  if (typeof message === 'string' && message.trim()) {
    return message;
  }
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }
  return '';
}

export function VisibilityAuthorizationDialog({
  open,
  namespaceId,
  resourceType,
  resourceName,
  onOpenChange,
  onSuccess,
}: VisibilityAuthorizationDialogProps) {
  const { t } = useTranslation();
  const [operation, setOperation] = useState<VisibilityOperation>('grant');
  const [action, setAction] = useState<VisibilityAction>('r');
  const [username, setUsername] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) {
      setOperation('grant');
      setAction('r');
      setUsername('');
      setError('');
    }
  }, [open]);

  const handleSubmit = async () => {
    const grantee = username.trim();
    if (!grantee) {
      setError(t('common.visibilityAuthorization.usernameRequired'));
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const request: VisibilityAuthorizationRequest = {
        namespaceId,
        resourceType,
        resourceName,
        username: grantee,
        action,
      };
      if (operation === 'grant') {
        await authApi.grantVisibility(request);
      } else {
        await authApi.revokeVisibility(request);
      }
      toast.success(t(`common.visibilityAuthorization.${operation}Success`));
      await onSuccess?.();
      onOpenChange(false);
    } catch (e) {
      const message = getErrorMessage(e) || t('common.requestFailed');
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{t('common.visibilityAuthorization.title')}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-2">
          <div className="grid gap-2">
            <Label>{t('common.selectNamespace')}</Label>
            <Input value={namespaceId} disabled />
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label>{t('common.visibilityAuthorization.resourceType')}</Label>
              <Input value={resourceType} disabled />
            </div>
            <div className="grid gap-2">
              <Label>{t('common.visibilityAuthorization.resourceName')}</Label>
              <Input value={resourceName} disabled />
            </div>
          </div>
          <div className="grid gap-2">
            <Label>{t('authority.username')}</Label>
            <Input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder={t('authority.usernamePlaceholder')}
              disabled={submitting}
            />
          </div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label>{t('authority.action')}</Label>
              <Select
                value={action}
                onValueChange={(value) => setAction(value as VisibilityAction)}
                disabled={submitting}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="r">{t('common.visibilityAuthorization.read')}</SelectItem>
                  <SelectItem value="w">{t('common.visibilityAuthorization.readWrite')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>{t('common.operation')}</Label>
              <Select
                value={operation}
                onValueChange={(value) => setOperation(value as VisibilityOperation)}
                disabled={submitting}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="grant">{t('common.visibilityAuthorization.grant')}</SelectItem>
                  <SelectItem value="revoke">{t('common.visibilityAuthorization.revoke')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          {error && (
            <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t('common.confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
