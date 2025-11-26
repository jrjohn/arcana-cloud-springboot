import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { pluginApi, PluginInfo } from '@/services/api';

/**
 * Hook for fetching plugins list.
 */
export function usePlugins() {
  return useQuery({
    queryKey: ['plugins'],
    queryFn: pluginApi.list,
  });
}

/**
 * Hook for fetching a single plugin.
 */
export function usePlugin(key: string) {
  return useQuery({
    queryKey: ['plugins', key],
    queryFn: () => pluginApi.get(key),
    enabled: !!key,
  });
}

/**
 * Hook for enabling a plugin.
 */
export function useEnablePlugin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (key: string) => pluginApi.enable(key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });
}

/**
 * Hook for disabling a plugin.
 */
export function useDisablePlugin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (key: string) => pluginApi.disable(key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });
}

/**
 * Hook for installing a plugin.
 */
export function useInstallPlugin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File) => pluginApi.install(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });
}

/**
 * Hook for uninstalling a plugin.
 */
export function useUninstallPlugin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (key: string) => pluginApi.uninstall(key),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
    },
  });
}

/**
 * Hook for fetching plugin configuration.
 */
export function usePluginConfig(key: string) {
  return useQuery({
    queryKey: ['plugins', key, 'config'],
    queryFn: () => pluginApi.getConfig(key),
    enabled: !!key,
  });
}

/**
 * Hook for updating plugin configuration.
 */
export function useUpdatePluginConfig(key: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (config: Record<string, unknown>) =>
      pluginApi.updateConfig(key, config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins', key, 'config'] });
    },
  });
}
