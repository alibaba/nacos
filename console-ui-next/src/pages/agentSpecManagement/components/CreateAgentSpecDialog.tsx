import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { agentSpecApi } from '@/api/agentspec';

interface CreateAgentSpecDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: (agentSpecName: string) => void;
}

export function CreateAgentSpecDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: CreateAgentSpecDialogProps) {
  const { t } = useTranslation();

  const [agentSpecName, setAgentSpecName] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        setAgentSpecName('');
        setDescription('');
        setError(null);
        setLoading(false);
      }
      onOpenChange(nextOpen);
    },
    [onOpenChange],
  );

  const handleCreate = useCallback(async () => {
    const trimmedName = agentSpecName.trim();
    if (!trimmedName) {
      setError(t('agentSpec.nameRequired'));
      return;
    }
    if (!description.trim()) {
      setError(t('agentSpec.descriptionRequired'));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const agentSpecCard = JSON.stringify({
        name: trimmedName,
        description: description.trim(),
        content: '',
        resource: {},
      });
      await agentSpecApi.updateDraft({ namespaceId, agentSpecCard });
      toast.success(t('agentSpec.createSuccess'));
      handleClose(false);
      onSuccess(trimmedName);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : t('agentSpec.createFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [agentSpecName, description, namespaceId, t, handleClose, onSuccess]);

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('agentSpec.createAgentSpec')}</DialogTitle>
          <DialogDescription>{t('agentSpec.createAgentSpecDesc')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="space-y-2">
            <Label htmlFor="agentspec-name">{t('agentSpec.agentSpecName')} *</Label>
            <Input
              id="agentspec-name"
              placeholder={t('agentSpec.namePlaceholder')}
              value={agentSpecName}
              onChange={(e) => {
                setAgentSpecName(e.target.value);
                setError(null);
              }}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="agentspec-desc">{t('agentSpec.description')} *</Label>
            <Textarea
              id="agentspec-desc"
              placeholder={t('agentSpec.descriptionPlaceholder')}
              value={description}
              onChange={(e) => {
                setDescription(e.target.value);
                setError(null);
              }}
              className="min-h-24 resize-y"
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => handleClose(false)}
              disabled={loading}
            >
              {t('common.cancel')}
            </Button>
            <Button
              onClick={handleCreate}
              disabled={!agentSpecName.trim() || !description.trim() || loading}
            >
              {loading ? t('common.loading') : t('agentSpec.createAgentSpec')}
            </Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  );
}
