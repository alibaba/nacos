import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useServerStore } from '@/stores/server-store';

export default function WelcomePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { functionMode } = useServerStore();

  useEffect(() => {
    const params = searchParams.toString();
    const qs = params ? `?${params}` : '';

    if (functionMode === 'naming') {
      navigate(`/serviceManagement${qs}`, { replace: true });
    } else if (functionMode === 'ai') {
      navigate(`/skill${qs}`, { replace: true });
    } else {
      navigate(`/configurationManagement${qs}`, { replace: true });
    }
  }, [functionMode, navigate, searchParams]);

  return null;
}
