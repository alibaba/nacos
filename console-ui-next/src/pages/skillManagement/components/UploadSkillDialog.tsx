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
import { skillApi } from '@/api/skill';

function isValidZipFile(file: File): boolean {
  return file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip';
}

interface UploadSkillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
}

export function UploadSkillDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: UploadSkillDialogProps) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const reset = useCallback(() => {
    setFile(null);
    setError(null);
    setLoading(false);
    setIsDragOver(false);
  }, []);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) reset();
      onOpenChange(nextOpen);
    },
    [onOpenChange, reset],
  );

  const handleFileSelect = useCallback(
    (selected: File | null) => {
      setError(null);
      if (selected && !isValidZipFile(selected)) {
        setError(t('skill.invalidZipFile'));
        setFile(null);
        return;
      }
      setFile(selected);
    },
    [t],
  );

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleFileSelect(e.target.files?.[0] ?? null);
    },
    [handleFileSelect],
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);
      const droppedFile = e.dataTransfer?.files?.[0] ?? null;
      handleFileSelect(droppedFile);
    },
    [handleFileSelect],
  );

  const showBatchResult = useCallback((data: { succeeded?: string[]; failed?: { name: string; reason: string }[] }) => {
    const succeededList = data.succeeded ?? [];
    const failedList = data.failed ?? [];
    if (failedList.length === 0) {
      toast.success(t('skill.batchUploadAllSuccess', { count: succeededList.length }), { duration: 5000 });
    } else {
      const title = succeededList.length > 0
        ? t('skill.batchUploadResult', { succeeded: succeededList.length, failed: failedList.length })
        : t('skill.batchUploadAllFailed', { count: failedList.length });
      const description = (
        <div className="flex flex-col gap-0.5 text-xs">
          {succeededList.map((name) => (
            <div key={name} style={{ color: '#16a34a' }}>✓ {name}</div>
          ))}
          {failedList.map((item) => (
            <div key={item.name} style={{ color: '#dc2626' }}>
              ✗ {item.name}<span style={{ opacity: 0.8 }}> — {item.reason}</span>
            </div>
          ))}
        </div>
      );
      const toastFn = succeededList.length > 0 ? toast.warning : toast.error;
      toastFn(title, { description, duration: 8000 });
    }
  }, [t]);

  const handleUpload = useCallback(async () => {
    if (!file) return;
    setLoading(true);
    try {
      const res = await skillApi.batchUpload(namespaceId, file);
      const data = (res as any)?.data;
      if (data && (data.succeeded || data.failed)) {
        showBatchResult(data);
      } else {
        toast.success(t('skill.uploadSuccess'));
      }
      handleClose(false);
      onSuccess();
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : t('skill.uploadFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [file, namespaceId, t, handleClose, onSuccess, showBatchResult]);

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('skill.uploadZip')}</DialogTitle>
          <DialogDescription>
            {t('skill.uploadZipDesc')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div
            className={`flex flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-6 cursor-pointer transition-colors ${
              isDragOver
                ? 'border-primary bg-primary/5'
                : 'hover:border-primary/50'
            }`}
            onClick={() => inputRef.current?.click()}
            onDragOver={handleDragOver}
            onDragEnter={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            <Upload className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground text-center">
              {isDragOver
                ? t('skill.dropFileHere')
                : file
                  ? file.name
                  : t('skill.dragOrClick')}
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
            {loading ? t('common.loading') : t('skill.upload')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
