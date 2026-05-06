import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

export default function PublishPromptVersionPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const promptKey = searchParams.get('promptKey') || '';
    const namespaceId = searchParams.get('namespaceId') || searchParams.get('namespace') || '';
    const params = new URLSearchParams({ mode: 'version' });
    if (promptKey) params.set('promptKey', promptKey);
    if (namespaceId) params.set('namespaceId', namespaceId);
    navigate(`/newPrompt?${params}`, { replace: true });
  }, [navigate, searchParams]);

  return null;
}
