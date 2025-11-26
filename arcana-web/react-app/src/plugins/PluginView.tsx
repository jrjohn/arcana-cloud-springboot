import { useEffect, useState, ReactNode, Suspense } from 'react';
import { usePluginContext } from './PluginContext';

interface PluginViewProps {
  pluginKey: string;
  viewKey: string;
  props?: Record<string, unknown>;
  fallback?: ReactNode;
}

/**
 * Plugin View Component
 *
 * Dynamically loads and renders a plugin's SSR view.
 * Supports both server-side rendered and client-side hydrated content.
 */
export function PluginView({
  pluginKey,
  viewKey,
  props = {},
  fallback,
}: PluginViewProps) {
  const { loadPluginView, ssrData } = usePluginContext();
  const [ViewComponent, setViewComponent] = useState<ReactNode | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // Check if this view was SSR'd
  const isSSRView =
    ssrData?.pluginKey === pluginKey && ssrData?.viewKey === viewKey;

  useEffect(() => {
    if (isSSRView) {
      // Already rendered on server, just hydrate
      setLoading(false);
      return;
    }

    // Load plugin view dynamically
    loadPluginView(pluginKey, viewKey)
      .then((component) => {
        setViewComponent(component);
        setLoading(false);
      })
      .catch((err) => {
        setError(err);
        setLoading(false);
      });
  }, [pluginKey, viewKey, isSSRView, loadPluginView]);

  if (error) {
    return (
      <div className="plugin-container">
        <div className="plugin-header">
          <span className="text-red-600">Plugin Error</span>
        </div>
        <div className="plugin-content">
          <p className="text-red-500">
            Failed to load plugin view: {pluginKey}/{viewKey}
          </p>
          <p className="text-sm text-gray-500 mt-2">{error.message}</p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      fallback || (
        <div className="plugin-container">
          <div className="plugin-content flex items-center justify-center py-8">
            <div className="spinner" />
          </div>
        </div>
      )
    );
  }

  return (
    <Suspense fallback={fallback || <div className="spinner" />}>
      <div className="plugin-container">
        <div className="plugin-header">
          <span className="font-medium">{pluginKey}</span>
          <span className="text-gray-400 mx-2">/</span>
          <span className="text-gray-600">{viewKey}</span>
        </div>
        <div className="plugin-content">
          {ViewComponent}
        </div>
      </div>
    </Suspense>
  );
}

/**
 * Plugin Slot Component
 *
 * Renders all plugin views registered for a specific slot.
 */
interface PluginSlotProps {
  slot: string;
  props?: Record<string, unknown>;
}

export function PluginSlot({ slot, props = {} }: PluginSlotProps) {
  const { plugins } = usePluginContext();
  const [slotViews, setSlotViews] = useState<
    Array<{ pluginKey: string; viewKey: string }>
  >([]);

  useEffect(() => {
    // Fetch views registered for this slot
    fetch(`/api/v1/plugins/slots/${slot}`)
      .then((res) => res.json())
      .then((views) => setSlotViews(views))
      .catch(console.error);
  }, [slot]);

  if (slotViews.length === 0) {
    return null;
  }

  return (
    <div className="plugin-slot space-y-4">
      {slotViews.map(({ pluginKey, viewKey }) => (
        <PluginView
          key={`${pluginKey}-${viewKey}`}
          pluginKey={pluginKey}
          viewKey={viewKey}
          props={props}
        />
      ))}
    </div>
  );
}
