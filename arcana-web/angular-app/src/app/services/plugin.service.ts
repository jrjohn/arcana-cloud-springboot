import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';

export interface PluginInfo {
  key: string;
  name: string;
  version: string;
  description?: string;
  vendor?: string;
  enabled: boolean;
  state: 'INSTALLED' | 'RESOLVED' | 'STARTING' | 'ACTIVE' | 'STOPPING';
  views: string[];
  extensions: string[];
}

/**
 * Plugin Management Service
 */
@Injectable({
  providedIn: 'root',
})
export class PluginService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  /**
   * Get all plugins.
   */
  getPlugins(): Observable<PluginInfo[]> {
    return this.http.get<PluginInfo[]>(`${this.apiUrl}/v1/plugins`);
  }

  /**
   * Get a single plugin by key.
   */
  getPlugin(key: string): Observable<PluginInfo> {
    return this.http.get<PluginInfo>(`${this.apiUrl}/v1/plugins/${key}`);
  }

  /**
   * Enable a plugin.
   */
  enablePlugin(key: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/v1/plugins/${key}/enable`, {});
  }

  /**
   * Disable a plugin.
   */
  disablePlugin(key: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/v1/plugins/${key}/disable`, {});
  }

  /**
   * Install a plugin from file.
   */
  installPlugin(file: File): Observable<PluginInfo> {
    const formData = new FormData();
    formData.append('plugin', file);
    return this.http.post<PluginInfo>(
      `${this.apiUrl}/v1/plugins/install`,
      formData
    );
  }

  /**
   * Uninstall a plugin.
   */
  uninstallPlugin(key: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/v1/plugins/${key}`);
  }

  /**
   * Get plugin configuration.
   */
  getPluginConfig(key: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(
      `${this.apiUrl}/v1/plugins/${key}/config`
    );
  }

  /**
   * Update plugin configuration.
   */
  updatePluginConfig(
    key: string,
    config: Record<string, unknown>
  ): Observable<void> {
    return this.http.put<void>(
      `${this.apiUrl}/v1/plugins/${key}/config`,
      config
    );
  }
}
