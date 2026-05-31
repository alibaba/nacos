import { ImportAiResourceDialog } from '@/components/ai/resource-import/ImportAiResourceDialog';

interface ImportMcpDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
}

export function ImportMcpDialog(props: ImportMcpDialogProps) {
  return (
    <ImportAiResourceDialog
      {...props}
      resourceType="mcp"
      translationPrefix="mcp"
      metadataKeys={['protocol']}
      showMetadataLabels={false}
    />
  );
}
