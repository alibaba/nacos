import { useState, useCallback, useRef } from 'react';
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
import { Upload } from 'lucide-react';
import { agentSpecApi } from '@/api/agentspec';
import { isValidZipFile } from './upload-utils';

interface UploadAgentSpecDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
}

export function UploadAgentSpecDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: UploadAgentSpecDialogProps) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const reset = useCallback(() => {
    setFile(null);
    setError(null);
    setLoading(false);
  }, []);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) reset();
      onOpenChange(nextOpen);
    },
    [onOpenChange, reset],
  );

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selected = e.target.files?.[0] ?? null;
      setError(null);
      if (selected && !isValidZipFile(selected)) {
        setError(t('agentSpec.invalidZipFile'));
        setFile(null);
        return;
      }
      setFile(selected);
    },
    [t],
  );

  const handleUpload = useCallback(async () => {
    if (!file) return;
    setLoading(true);
    try {
      await agentSpecApi.upload(namespaceId, file);
      toast.success(t('agentSpec.uploadSuccess'));
      handleClose(false);
      onSuccess();
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : t('agentSpec.uploadFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [file, namespaceId, t, handleClose, onSuccess]);

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('agentSpec.uploadZip')}</DialogTitle>
          <DialogDescription>
            {t('agentSpec.uploadZipDesc')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div
            className="flex flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-6 cursor-pointer hover:border-primary/50 transition-colors"
            onClick={() => inputRef.current?.click()}
          >
            <Upload className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">
              {file
                ? file.name
                : t('agentSpec.dragOrClick')}
            </p>
            <input
              ref={inputRef}
              type="file"
              accept=".zip,application/zip"
              className="hidden"
              onChange={handleFileChange}
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleUpload} disabled={!file || !!error || loading}>
            {loading ? t('common.loading') : t('agentSpec.upload')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
