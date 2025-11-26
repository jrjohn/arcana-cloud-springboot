import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PluginService, PluginInfo } from '@services/plugin.service';

/**
 * Plugins management page.
 */
@Component({
  selector: 'app-plugins',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <h1>Plugins</h1>
        <button class="btn btn-primary" (click)="showInstallModal = true">
          📤 Install Plugin
        </button>
      </div>

      <!-- Install Modal -->
      @if (showInstallModal) {
        <div class="card install-card">
          <div class="text-center p-6">
            <div class="upload-icon">📤</div>
            <p class="upload-title">Install Plugin</p>
            <p class="upload-subtitle">Upload a plugin JAR file to install</p>
            <input
              type="file"
              accept=".jar"
              (change)="onFileSelected($event)"
              #fileInput
              style="display: none"
            />
            <div class="flex justify-center gap-4 mt-4">
              <button class="btn btn-primary" (click)="fileInput.click()">
                Choose File
              </button>
              <button class="btn btn-secondary" (click)="showInstallModal = false">
                Cancel
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Loading -->
      @if (loading) {
        <div class="flex justify-center p-6">
          <div class="spinner"></div>
        </div>
      }

      <!-- Error -->
      @if (error) {
        <div class="card error-card">
          <p>Failed to load plugins. Please try again.</p>
        </div>
      }

      <!-- Plugins Grid -->
      @if (plugins.length > 0) {
        <div class="plugins-grid">
          @for (plugin of plugins; track plugin.key) {
            <div class="card plugin-card">
              <div class="plugin-header">
                <div class="plugin-icon" [class.active]="plugin.enabled">🧩</div>
                <div class="plugin-title">
                  <h3>{{ plugin.name }}</h3>
                  <span class="plugin-version">v{{ plugin.version }}</span>
                </div>
                <span class="plugin-state" [attr.data-state]="plugin.state">
                  {{ plugin.state }}
                </span>
              </div>

              @if (plugin.description) {
                <p class="plugin-description">{{ plugin.description }}</p>
              }

              @if (plugin.vendor) {
                <p class="plugin-vendor">by {{ plugin.vendor }}</p>
              }

              <div class="plugin-actions">
                <div class="plugin-tools">
                  <button class="tool-btn" title="Settings" [routerLink]="['/plugins', plugin.key]">
                    ⚙️
                  </button>
                  <button class="tool-btn danger" title="Uninstall" (click)="uninstallPlugin(plugin.key)">
                    🗑️
                  </button>
                </div>
                <button
                  class="toggle-btn"
                  [class.enabled]="plugin.enabled"
                  (click)="togglePlugin(plugin)"
                >
                  @if (plugin.enabled) {
                    ✓ Enabled
                  } @else {
                    ✕ Disabled
                  }
                </button>
              </div>
            </div>
          }
        </div>
      }

      <!-- Empty State -->
      @if (!loading && plugins.length === 0) {
        <div class="card text-center p-6">
          <div class="empty-icon">🧩</div>
          <h3>No Plugins Installed</h3>
          <p class="text-gray-500">Get started by installing your first plugin.</p>
          <button class="btn btn-primary mt-4" (click)="showInstallModal = true">
            Install Plugin
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .plugins-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
    }

    .plugin-card {
      display: flex;
      flex-direction: column;
    }

    .plugin-header {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .plugin-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: #e5e7eb;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
    }

    .plugin-icon.active {
      background: var(--primary-100);
    }

    .plugin-title {
      flex: 1;
    }

    .plugin-title h3 {
      font-size: 1rem;
      margin: 0;
    }

    .plugin-version {
      font-size: 0.75rem;
      color: #6b7280;
    }

    .plugin-state {
      font-size: 0.75rem;
      padding: 0.25rem 0.5rem;
      border-radius: 9999px;
      font-weight: 500;
    }

    .plugin-state[data-state="ACTIVE"] {
      background: #d1fae5;
      color: #065f46;
    }

    .plugin-state[data-state="INSTALLED"],
    .plugin-state[data-state="RESOLVED"] {
      background: #fef3c7;
      color: #92400e;
    }

    .plugin-description {
      font-size: 0.875rem;
      color: #4b5563;
      margin-bottom: 0.5rem;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .plugin-vendor {
      font-size: 0.75rem;
      color: #9ca3af;
      margin-bottom: 1rem;
    }

    .plugin-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 1rem;
      border-top: 1px solid #e5e7eb;
      margin-top: auto;
    }

    .plugin-tools {
      display: flex;
      gap: 0.5rem;
    }

    .tool-btn {
      width: 32px;
      height: 32px;
      border: none;
      background: #f3f4f6;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s;
    }

    .tool-btn:hover {
      background: #e5e7eb;
    }

    .tool-btn.danger:hover {
      background: #fee2e2;
    }

    .toggle-btn {
      padding: 0.5rem 0.75rem;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 500;
      border: none;
      cursor: pointer;
      transition: all 0.2s;
      background: #e5e7eb;
      color: #4b5563;
    }

    .toggle-btn.enabled {
      background: #d1fae5;
      color: #065f46;
    }

    .install-card {
      border: 2px dashed var(--primary-300);
    }

    .upload-icon {
      font-size: 3rem;
      margin-bottom: 1rem;
    }

    .upload-title {
      font-size: 1.125rem;
      font-weight: 600;
      margin-bottom: 0.5rem;
    }

    .upload-subtitle {
      color: #6b7280;
      margin-bottom: 1rem;
    }

    .error-card {
      background: #fee2e2;
      border: 1px solid #fecaca;
      color: #b91c1c;
    }

    .empty-icon {
      font-size: 4rem;
      opacity: 0.5;
      margin-bottom: 1rem;
    }
  `],
})
export class PluginsComponent implements OnInit {
  private readonly pluginService = inject(PluginService);

  plugins: PluginInfo[] = [];
  loading = true;
  error = false;
  showInstallModal = false;

  ngOnInit(): void {
    this.loadPlugins();
  }

  private loadPlugins(): void {
    this.loading = true;
    this.error = false;

    this.pluginService.getPlugins().subscribe({
      next: (plugins) => {
        this.plugins = plugins;
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
        // Mock data for demonstration
        this.plugins = [
          {
            key: 'com.arcana.plugin.audit',
            name: 'Audit Log Plugin',
            version: '1.0.0',
            description: 'Comprehensive audit logging for Arcana Cloud platform',
            vendor: 'Arcana Cloud',
            enabled: true,
            state: 'ACTIVE',
            views: ['audit-dashboard'],
            extensions: ['rest', 'event-listener', 'scheduled-job'],
          },
        ];
        this.loading = false;
        this.error = false;
      },
    });
  }

  togglePlugin(plugin: PluginInfo): void {
    const action = plugin.enabled
      ? this.pluginService.disablePlugin(plugin.key)
      : this.pluginService.enablePlugin(plugin.key);

    action.subscribe({
      next: () => this.loadPlugins(),
      error: (err) => console.error('Failed to toggle plugin', err),
    });
  }

  uninstallPlugin(key: string): void {
    if (confirm('Are you sure you want to uninstall this plugin?')) {
      this.pluginService.uninstallPlugin(key).subscribe({
        next: () => this.loadPlugins(),
        error: (err) => console.error('Failed to uninstall plugin', err),
      });
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.pluginService.installPlugin(file).subscribe({
        next: () => {
          this.showInstallModal = false;
          this.loadPlugins();
        },
        error: (err) => console.error('Failed to install plugin', err),
      });
    }
  }
}
