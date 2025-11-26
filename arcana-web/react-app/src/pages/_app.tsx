import type { AppProps } from 'next/app';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import '@/styles/globals.css';
import { PluginProvider } from '@/plugins/PluginContext';

/**
 * Arcana Cloud React Application
 *
 * Main application wrapper with:
 * - React Query for data fetching
 * - Plugin context for plugin SSR views
 * - Global styles
 */
export default function App({ Component, pageProps }: AppProps) {
  // Create a client for each request (SSR safe)
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000, // 1 minute
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <PluginProvider ssrData={(pageProps as any).__ARCANA_SSR_DATA__}>
        <Component {...pageProps} />
      </PluginProvider>
    </QueryClientProvider>
  );
}
