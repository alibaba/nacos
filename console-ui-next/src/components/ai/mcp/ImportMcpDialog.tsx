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
import { Switch } from '@/components/ui/switch';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { mcpApi } from '@/api/mcp';
import type { McpImportType, McpImportValidationItem } from '@/types/mcp';

interface ImportMcpDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
}

export function ImportMcpDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: ImportMcpDialogProps) {
  const { t } = useTranslation();
  const [importType, setImportType] = useState<McpImportType>('url');
  const [data, setData] = useState('');
  const [overrideExisting, setOverrideExisting] = useState(false);
  const [skipInvalid, setSkipInvalid] = useState(false);
  const [loading, setLoading] = useState(false);
  const [validationResult, setValidationResult] = useState<McpImportValidationItem[] | null>(null);
  const [selectedServers, setSelectedServers] = useState<Set<string>>(new Set());

  const resetForm = useCallback(() => {
    setImportType('url');
    setData('');
    setOverrideExisting(false);
    setSkipInvalid(false);
    setValidationResult(null);
    setSelectedServers(new Set());
  }, []);

  const handleClose = useCallback(
    (open: boolean) => {
      if (!open) resetForm();
      onOpenChange(open);
    },
    [onOpenChange, resetForm]
  );

  const handleValidate = async () => {
    if (!data.trim()) return;
    setLoading(true);
    try {
      const response = await mcpApi.validateImport({
        importType,
        data: data.trim(),
        namespaceId,
        overrideExisting,
        validateOnly: true,
        skipInvalid,
      });
      const result = response as unknown as { data: { servers?: McpImportValidationItem[] } };
      const servers = result.data?.servers || [];
      setValidationResult(servers);
      // Auto-select all valid servers
      const validNames = servers.filter((s) => s.valid).map((s) => s.name);
      setSelectedServers(new Set(validNames));
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  const handleExecute = async () => {
    setLoading(true);
    try {
      await mcpApi.executeImport({
        importType,
        data: data.trim(),
        namespaceId,
        overrideExisting,
        skipInvalid,
        selectedServers: Array.from(selectedServers),
      });
      toast.success(t('mcp.importSuccess'));
      handleClose(false);
      onSuccess();
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  const toggleServer = (name: string) => {
    setSelectedServers((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('mcp.importFromRegistry')}</DialogTitle>
          <DialogDescription>
            {t('mcp.importUrl')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Import type */}
          <div className="space-y-2">
            <Label>{t('mcp.importType')}</Label>
            <RadioGroup
              value={importType}
              onValueChange={(v) => {
                setImportType(v as McpImportType);
                setValidationResult(null);
              }}
              className="flex gap-4"
            >
              <div className="flex items-center gap-2">
                <RadioGroupItem value="url" id="import-url" />
                <Label htmlFor="import-url" className="text-sm font-normal cursor-pointer">
                  {t('mcp.importTypeUrl')}
                </Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="json" id="import-json" />
                <Label htmlFor="import-json" className="text-sm font-normal cursor-pointer">
                  {t('mcp.importTypeJson')}
                </Label>
              </div>
            </RadioGroup>
          </div>

          {/* Data input */}
          <div className="space-y-2">
            <Label>{importType === 'url' ? t('mcp.importUrl') : 'JSON'}</Label>
            {importType === 'url' ? (
              <Input
                value={data}
                onChange={(e) => {
                  setData(e.target.value);
                  setValidationResult(null);
                }}
                placeholder="https://registry.example.com/servers"
              />
            ) : (
              <Textarea
                value={data}
                onChange={(e) => {
                  setData(e.target.value);
                  setValidationResult(null);
                }}
                placeholder='[{"name": "...", "protocol": "...", ...}]'
                rows={6}
                className="font-mono text-xs"
              />
            )}
          </div>

          {/* Options */}
          <div className="flex gap-6">
            <div className="flex items-center gap-2">
              <Switch checked={overrideExisting} onCheckedChange={setOverrideExisting} />
              <Label className="text-sm font-normal">{t('mcp.importOverride')}</Label>
            </div>
            <div className="flex items-center gap-2">
              <Switch checked={skipInvalid} onCheckedChange={setSkipInvalid} />
              <Label className="text-sm font-normal">{t('mcp.importSkipInvalid')}</Label>
            </div>
          </div>

          {/* Validation results */}
          {validationResult && (
            <div className="space-y-2">
              <Label>{t('mcp.selectedServers')}</Label>
              <ScrollArea className="h-48 border rounded-md p-2">
                <div className="space-y-1">
                  {validationResult.map((item) => (
                    <div
                      key={item.name}
                      className="flex items-center gap-2 px-2 py-1 rounded hover:bg-muted/50"
                    >
                      <Checkbox
                        checked={selectedServers.has(item.name)}
                        onCheckedChange={() => toggleServer(item.name)}
                        disabled={!item.valid}
                      />
                      <span className="text-sm flex-1 truncate">{item.name}</span>
                      {item.valid ? (
                        <Badge variant="outline" className="text-[10px] text-emerald-600">
                          valid
                        </Badge>
                      ) : (
                        <Badge variant="destructive" className="text-[10px]">
                          {item.message || 'invalid'}
                        </Badge>
                      )}
                    </div>
                  ))}
                </div>
              </ScrollArea>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)} disabled={loading}>
            {t('common.cancel')}
          </Button>
          {!validationResult ? (
            <Button onClick={handleValidate} disabled={!data.trim() || loading}>
              {loading ? t('common.loading') : t('mcp.importValidate')}
            </Button>
          ) : (
            <Button
              onClick={handleExecute}
              disabled={selectedServers.size === 0 || loading}
            >
              {loading ? t('common.loading') : t('mcp.importExecute')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
