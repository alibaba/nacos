import { ImportAiResourceDialog } from '@/components/ai/resource-import/ImportAiResourceDialog';

interface ImportSkillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
}

export function ImportSkillDialog(props: ImportSkillDialogProps) {
  return (
    <ImportAiResourceDialog
      {...props}
      resourceType="skill"
      translationPrefix="skill"
      metadataKeys={['fileCount', 'source']}
    />
  );
}
