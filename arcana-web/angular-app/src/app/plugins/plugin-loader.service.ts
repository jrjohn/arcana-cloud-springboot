import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';
import { environment } from '@env/environment';

export interface PluginSSRData {
  props?: Record<string, unknown>;
  pluginKey?: string;
  viewKey?: string;
  authenticated?: boolean;
  userId?: number;
}

/**
 * Plugin Loader Service
 *
 * Handles dynamic loading of plugin SSR views and hydration.
 */
@Injectable({
  providedIn: 'root',
})
export class PluginLoaderService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiUrl = environment.apiUrl;

  private readonly ssrDataSubject = new BehaviorSubject<PluginSSRData | null>(null);
  readonly ssrData$ = this.ssrDataSubject.asObservable();

  private readonly loadedPlugins = new Map<string, boolean>();

  constructor() {
    this.loadSSRData();
  }

  /**
   * Load SSR data from window object.
   */
  private loadSSRData(): void {
    if (isPlatformBrowser(this.platformId)) {
      const ssrData = (window as any).__ARCANA_SSR_DATA__;
      if (ssrData) {
        this.ssrDataSubject.next(ssrData);
      }
    }
  }

  /**
   * Get SSR props for the current view.
   */
  getSSRProps<T = unknown>(): T | null {
    const data = this.ssrDataSubject.value;
    return data?.props as T ?? null;
  }

  /**
   * Check if current view was server-side rendered.
   */
  isSSRView(pluginKey: string, viewKey: string): boolean {
    const data = this.ssrDataSubject.value;
    return data?.pluginKey === pluginKey && data?.viewKey === viewKey;
  }

  /**
   * Load a plugin's assets (CSS/JS).
   */
  loadPluginAssets(pluginKey: string): Observable<void> {
    if (this.loadedPlugins.get(pluginKey)) {
      return of(undefined);
    }

    return this.http
      .get<{ css: string[]; js: string[] }>(
        `${this.apiUrl}/v1/plugins/${pluginKey}/assets`
      )
      .pipe(
        tap((assets) => {
          if (isPlatformBrowser(this.platformId)) {
            this.injectAssets(pluginKey, assets);
          }
          this.loadedPlugins.set(pluginKey, true);
        }),
        catchError(() => {
          console.warn(`Failed to load assets for plugin: ${pluginKey}`);
          return of(undefined);
        })
      ) as Observable<void>;
  }

  /**
   * Inject CSS and JS assets into the document.
   */
  private injectAssets(
    pluginKey: string,
    assets: { css: string[]; js: string[] }
  ): void {
    // Inject CSS
    assets.css?.forEach((cssUrl) => {
      const link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = cssUrl;
      link.setAttribute('data-plugin', pluginKey);
      document.head.appendChild(link);
    });

    // Inject JS
    assets.js?.forEach((jsUrl) => {
      const script = document.createElement('script');
      script.src = jsUrl;
      script.async = true;
      script.setAttribute('data-plugin', pluginKey);
      document.body.appendChild(script);
    });
  }

  /**
   * Unload a plugin's assets.
   */
  unloadPluginAssets(pluginKey: string): void {
    if (isPlatformBrowser(this.platformId)) {
      document
        .querySelectorAll(`[data-plugin="${pluginKey}"]`)
        .forEach((el) => el.remove());
      this.loadedPlugins.delete(pluginKey);
    }
  }

  /**
   * Fetch plugin slot contents.
   */
  getSlotContent(
    slot: string
  ): Observable<Array<{ pluginKey: string; viewKey: string }>> {
    return this.http.get<Array<{ pluginKey: string; viewKey: string }>>(
      `${this.apiUrl}/v1/plugins/slots/${slot}`
    );
  }
}
