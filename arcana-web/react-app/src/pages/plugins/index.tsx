import { useState } from 'react';
import Head from 'next/head';
import { Layout } from '@/components/Layout';
import { Card } from '@/components/Card';
import {
  usePlugins,
  useEnablePlugin,
  useDisablePlugin,
  useInstallPlugin,
} from '@/hooks/usePlugins';
import { Puzzle, Power, Upload, Settings, Trash2, Check, X } from 'lucide-react';
import clsx from 'clsx';

/**
 * Plugins Management Page
 *
 * Lists all installed plugins and allows enabling/disabling.
 */
export default function PluginsPage() {
  const { data: plugins, isLoading, error } = usePlugins();
  const enablePlugin = useEnablePlugin();
  const disablePlugin = useDisablePlugin();
  const installPlugin = useInstallPlugin();
  const [showInstall, setShowInstall] = useState(false);

  const handleTogglePlugin = async (key: string, enabled: boolean) => {
    if (enabled) {
      await disablePlugin.mutateAsync(key);
    } else {
      await enablePlugin.mutateAsync(key);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      await installPlugin.mutateAsync(file);
      setShowInstall(false);
    }
  };

  return (
    <>
      <Head>
        <title>Plugins - Arcana Cloud</title>
      </Head>

      <Layout>
        <div className="space-y-6">
          <div className="flex justify-between items-center">
            <h1>Plugins</h1>
            <button
              className="btn btn-primary flex items-center space-x-2"
              onClick={() => setShowInstall(true)}
            >
              <Upload className="w-4 h-4" />
              <span>Install Plugin</span>
            </button>
          </div>

          {/* Install Modal */}
          {showInstall && (
            <Card className="border-2 border-dashed border-primary-300">
              <div className="text-center py-8">
                <Upload className="w-12 h-12 text-primary-500 mx-auto mb-4" />
                <p className="text-lg font-medium mb-2">Install Plugin</p>
                <p className="text-gray-500 mb-4">
                  Upload a plugin JAR file to install
                </p>
                <input
                  type="file"
                  accept=".jar"
                  onChange={handleFileUpload}
                  className="hidden"
                  id="plugin-upload"
                />
                <div className="flex justify-center space-x-3">
                  <label
                    htmlFor="plugin-upload"
                    className="btn btn-primary cursor-pointer"
                  >
                    Choose File
                  </label>
                  <button
                    className="btn btn-secondary"
                    onClick={() => setShowInstall(false)}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            </Card>
          )}

          {/* Loading State */}
          {isLoading && (
            <div className="flex justify-center py-12">
              <div className="spinner" />
            </div>
          )}

          {/* Error State */}
          {error && (
            <Card className="bg-red-50 border border-red-200">
              <p className="text-red-600">
                Failed to load plugins. Please try again.
              </p>
            </Card>
          )}

          {/* Plugins Grid */}
          {plugins && plugins.length > 0 && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {plugins.map((plugin) => (
                <PluginCard
                  key={plugin.key}
                  plugin={plugin}
                  onToggle={() =>
                    handleTogglePlugin(plugin.key, plugin.enabled)
                  }
                  isToggling={
                    enablePlugin.isPending || disablePlugin.isPending
                  }
                />
              ))}
            </div>
          )}

          {/* Empty State */}
          {plugins && plugins.length === 0 && (
            <Card className="text-center py-12">
              <Puzzle className="w-16 h-16 text-gray-300 mx-auto mb-4" />
              <h3 className="text-lg font-medium text-gray-900 mb-2">
                No Plugins Installed
              </h3>
              <p className="text-gray-500 mb-4">
                Get started by installing your first plugin.
              </p>
              <button
                className="btn btn-primary"
                onClick={() => setShowInstall(true)}
              >
                Install Plugin
              </button>
            </Card>
          )}
        </div>
      </Layout>
    </>
  );
}

interface PluginCardProps {
  plugin: {
    key: string;
    name: string;
    version: string;
    description?: string;
    vendor?: string;
    enabled: boolean;
    state: string;
  };
  onToggle: () => void;
  isToggling: boolean;
}

function PluginCard({ plugin, onToggle, isToggling }: PluginCardProps) {
  const stateColors: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-800',
    INSTALLED: 'bg-yellow-100 text-yellow-800',
    RESOLVED: 'bg-blue-100 text-blue-800',
    STOPPING: 'bg-orange-100 text-orange-800',
    STARTING: 'bg-blue-100 text-blue-800',
  };

  return (
    <Card hover>
      <div className="flex items-start justify-between mb-4">
        <div className="flex items-center space-x-3">
          <div
            className={clsx(
              'w-10 h-10 rounded-lg flex items-center justify-center',
              plugin.enabled ? 'bg-primary-100' : 'bg-gray-100'
            )}
          >
            <Puzzle
              className={clsx(
                'w-5 h-5',
                plugin.enabled ? 'text-primary-600' : 'text-gray-400'
              )}
            />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900 dark:text-white">
              {plugin.name}
            </h3>
            <p className="text-sm text-gray-500">v{plugin.version}</p>
          </div>
        </div>
        <span
          className={clsx(
            'px-2 py-1 text-xs font-medium rounded-full',
            stateColors[plugin.state] || 'bg-gray-100 text-gray-800'
          )}
        >
          {plugin.state}
        </span>
      </div>

      {plugin.description && (
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4 line-clamp-2">
          {plugin.description}
        </p>
      )}

      {plugin.vendor && (
        <p className="text-xs text-gray-400 mb-4">by {plugin.vendor}</p>
      )}

      <div className="flex items-center justify-between pt-4 border-t border-gray-100 dark:border-gray-700">
        <div className="flex space-x-2">
          <button
            className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
            title="Settings"
          >
            <Settings className="w-4 h-4" />
          </button>
          <button
            className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50"
            title="Uninstall"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>

        <button
          className={clsx(
            'flex items-center space-x-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
            plugin.enabled
              ? 'bg-green-100 text-green-700 hover:bg-green-200'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          )}
          onClick={onToggle}
          disabled={isToggling}
        >
          {plugin.enabled ? (
            <>
              <Check className="w-4 h-4" />
              <span>Enabled</span>
            </>
          ) : (
            <>
              <X className="w-4 h-4" />
              <span>Disabled</span>
            </>
          )}
        </button>
      </div>
    </Card>
  );
}
