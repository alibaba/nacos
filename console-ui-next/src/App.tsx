import { useEffect } from 'react';
import { Toaster } from 'sonner';
import { AppRouter } from './router';
import { useAppStore } from './stores/app-store';

function App() {
  const { initFromStorage } = useAppStore();

  useEffect(() => {
    initFromStorage();
  }, [initFromStorage]);

  return (
    <>
      <AppRouter />
      <Toaster position="top-right" richColors closeButton />
    </>
  );
}

export default App;
