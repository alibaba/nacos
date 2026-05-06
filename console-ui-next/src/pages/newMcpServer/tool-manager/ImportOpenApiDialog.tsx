/* eslint-disable @typescript-eslint/no-explicit-any */
import { useState, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Upload, FileText, Loader2, X } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { parseOpenAPI } from '@/utils/openapi/parseOpenApi';
import { extractToolsFromOpenAPI, transformToolsFromConfig } from '@/utils/openapi/swagger2Tools';
import type { McpTool, McpToolMeta, McpSecurityScheme } from '@/types/mcp';

interface ImportOpenApiDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImport: (
    tools: McpTool[],
    toolsMeta: Record<string, McpToolMeta>,
    securitySchemes: McpSecurityScheme[]
  ) => void;
}

export default function ImportOpenApiDialog({
  open,
  onOpenChange,
  onImport,
}: ImportOpenApiDialogProps) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<{
    toolCount: number;
    schemeCount: number;
  } | null>(null);
  const parsedRef = useRef<any>(null);

  const resetState = useCallback(() => {
    setFile(null);
    setPreview(null);
    parsedRef.current = null;
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, []);

  const processFile = async (f: File) => {
    setFile(f);
    setPreview(null);
    setLoading(true);

    try {
      const text = await f.text();
      const openapi = await parseOpenAPI(text);
      const config = extractToolsFromOpenAPI(openapi);
      const result = transformToolsFromConfig(config);

      parsedRef.current = result;
      setPreview({
        toolCount: result.tools.length,
        schemeCount: result.securitySchemes?.length || 0,
      });
    } catch (e: any) {
      toast.error(t('mcp.fileInvalidFormat'));
      setFile(null);
      parsedRef.current = null;
    } finally {
      setLoading(false);
    }
  };

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile) processFile(droppedFile);
    },
    [processFile]
  );

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (selected) processFile(selected);
  };

  const handleImport = () => {
    if (!parsedRef.current) {
      toast.error(t('mcp.pleaseSelectFile'));
      return;
    }

    const result = parsedRef.current;

    // Build security schemes
    const securitySchemes: McpSecurityScheme[] = (result.securitySchemes || []).map((s: any) => ({
      id: s.id,
      type: s.type,
      scheme: s.scheme,
      in: s.in,
      name: s.name,
    }));

    onImport(result.tools, result.toolsMeta, securitySchemes);
    toast.success(t('mcp.importToolsSuccess', { count: result.tools.length }));
    onOpenChange(false);
    resetState();
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        onOpenChange(v);
        if (!v) resetState();
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('mcp.importToolsFromOpenAPITitle')}</DialogTitle>
          <DialogDescription />
        </DialogHeader>

        <div className="space-y-4">
          {/* Drop zone */}
          <div
            className={cn(
              'relative flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 transition-colors cursor-pointer',
              dragOver ? 'border-primary bg-primary/5' : 'border-muted-foreground/25 hover:border-muted-foreground/50',
              file && 'border-primary/50'
            )}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept=".json,.yaml,.yml"
              onChange={handleFileSelect}
            />
            {loading ? (
              <Loader2 className="h-8 w-8 text-muted-foreground animate-spin" />
            ) : file ? (
              <>
                <FileText className="h-8 w-8 text-primary mb-2" />
                <span className="text-sm font-medium">{file.name}</span>
                <button
                  className="absolute top-2 right-2 p-1 rounded hover:bg-muted"
                  onClick={(e) => {
                    e.stopPropagation();
                    resetState();
                  }}
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </>
            ) : (
              <>
                <Upload className="h-8 w-8 text-muted-foreground mb-2" />
                <span className="text-sm text-muted-foreground">{t('mcp.dragOrClickToUpload')}</span>
                <span className="text-xs text-muted-foreground/70 mt-1">{t('mcp.supportedFormats')}</span>
              </>
            )}
          </div>

          {/* Preview */}
          {preview && (
            <div className="rounded-lg bg-muted/50 p-3 space-y-1">
              <p className="text-sm">
                {t('mcp.foundOperations', { count: preview.toolCount })}
              </p>
              {preview.schemeCount > 0 && (
                <p className="text-xs text-muted-foreground">
                  {t('mcp.foundSecuritySchemes', { count: preview.schemeCount })}
                </p>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              onOpenChange(false);
              resetState();
            }}
          >
            {t('common.cancel')}
          </Button>
          <Button onClick={handleImport} disabled={!preview || loading}>
            {t('mcp.importTools')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
