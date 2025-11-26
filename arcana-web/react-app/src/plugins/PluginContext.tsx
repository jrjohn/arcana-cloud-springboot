import {
  createContext,
  useContext,
  ReactNode,
  useState,
  useEffect,
} from 'react';

interface PluginSSRData {
  props?: Record<string, unknown>;
  pluginKey?: string;
  viewKey?: string;
  authenticated?: boolean;
  userId?: number;
}

interface PluginContextValue {
  ssrData: PluginSSRData | null;
  isHydrated: boolean;
  plugins: PluginInfo[];
  loadPluginView: (pluginKey: string, viewKey: string) => Promise<ReactNode>;
}

interface PluginInfo {
  key: string;
  name: string;
  version: string;
  enabled: boolean;
  views: string[];
}

const PluginContext = createContext<PluginContextValue | null>(null);

interface PluginProviderProps {
  children: ReactNode;
  ssrData?: PluginSSRData;
}

/**
 * Plugin Context Provider
 *
 * Provides plugin context for SSR hydration and plugin view loading.
 */
export function PluginProvider({ children, ssrData }: PluginProviderProps) {
  const [isHydrated, setIsHydrated] = useState(false);
  const [plugins, setPlugins] = useState<PluginInfo[]>([]);

  useEffect(() => {
    // Check for SSR data in window
    const windowSSRData =
      typeof window !== 'undefined'
        ? (window as any).__ARCANA_SSR_DATA__
        : null;

    if (windowSSRData || ssrData) {
      setIsHydrated(true);
    }

    // Load plugin list
    loadPlugins();
  }, [ssrData]);

  const loadPlugins = async () => {
    try {
      const response = await fetch('/api/v1/plugins');
      if (response.ok) {
        const data = await response.json();
        setPlugins(data);
      }
    } catch (error) {
      console.error('Failed to load plugins:', error);
    }
  };

  const loadPluginView = async (
    pluginKey: string,
    viewKey: string
  ): Promise<ReactNode> => {
    try {
      // Dynamic import of plugin view
      const module = await import(
        /* webpackChunkName: "plugin-[request]" */
        `./views/${pluginKey}/${viewKey}`
      );
      return module.default;
    } catch (error) {
      console.error(`Failed to load plugin view: ${pluginKey}/${viewKey}`, error);
      return null;
    }
  };

  const value: PluginContextValue = {
    ssrData: ssrData || null,
    isHydrated,
    plugins,
    loadPluginView,
  };

  return (
    <PluginContext.Provider value={value}>{children}</PluginContext.Provider>
  );
}

/**
 * Hook to access plugin context.
 */
export function usePluginContext() {
  const context = useContext(PluginContext);
  if (!context) {
    throw new Error('usePluginContext must be used within a PluginProvider');
  }
  return context;
}

/**
 * Hook to get SSR hydration data.
 */
export function useSSRData<T = unknown>(): T | null {
  const { ssrData, isHydrated } = usePluginContext();

  if (!isHydrated || !ssrData?.props) {
    return null;
  }

  return ssrData.props as T;
}
