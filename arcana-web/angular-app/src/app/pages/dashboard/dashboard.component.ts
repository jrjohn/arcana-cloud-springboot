import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '@env/environment';

interface DashboardStats {
  activeUsers: number;
  totalPlugins: number;
  systemHealth: string;
  uptime: string;
}

/**
 * Dashboard page component with SSR support.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex justify-between items-center">
        <h1>Dashboard</h1>
        <span class="text-sm text-gray-500">Welcome back!</span>
      </div>

      <!-- Stats Grid -->
      <div class="stats-grid">
        <div class="card stat-card">
          <div class="stat-icon users">👥</div>
          <div class="stat-info">
            <p class="stat-label">Active Users</p>
            <p class="stat-value">{{ stats.activeUsers }}</p>
          </div>
        </div>

        <div class="card stat-card">
          <div class="stat-icon plugins">🧩</div>
          <div class="stat-info">
            <p class="stat-label">Active Plugins</p>
            <p class="stat-value">{{ stats.totalPlugins }}</p>
          </div>
        </div>

        <div class="card stat-card">
          <div class="stat-icon health">💚</div>
          <div class="stat-info">
            <p class="stat-label">System Health</p>
            <p class="stat-value healthy">{{ stats.systemHealth }}</p>
          </div>
        </div>

        <div class="card stat-card">
          <div class="stat-icon uptime">⏱️</div>
          <div class="stat-info">
            <p class="stat-label">Uptime</p>
            <p class="stat-value">{{ stats.uptime }}</p>
          </div>
        </div>
      </div>

      <!-- Quick Actions & Activity -->
      <div class="grid-2">
        <div class="card">
          <h3 class="mb-4">Quick Actions</h3>
          <div class="action-list">
            <a routerLink="/plugins" class="action-item">
              <span>Manage Plugins</span>
              <span class="arrow">→</span>
            </a>
            <a routerLink="/users" class="action-item">
              <span>User Management</span>
              <span class="arrow">→</span>
            </a>
            <a routerLink="/audit" class="action-item">
              <span>View Audit Logs</span>
              <span class="arrow">→</span>
            </a>
          </div>
        </div>

        <div class="card">
          <h3 class="mb-4">Recent Activity</h3>
          <div class="activity-list">
            <div class="activity-item">
              <div class="activity-dot success"></div>
              <span class="activity-type">Plugin installed</span>
              <span class="activity-detail">Audit Log Plugin</span>
            </div>
            <div class="activity-item">
              <div class="activity-dot info"></div>
              <span class="activity-type">User login</span>
              <span class="activity-detail">admin&#64;arcana.cloud</span>
            </div>
            <div class="activity-item">
              <div class="activity-dot warning"></div>
              <span class="activity-type">Config updated</span>
              <span class="activity-detail">SSR settings</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
    }

    .stat-card {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
    }

    .stat-icon.users { background: #dbeafe; }
    .stat-icon.plugins { background: #d1fae5; }
    .stat-icon.health { background: #fce7f3; }
    .stat-icon.uptime { background: #e5e7eb; }

    .stat-label {
      font-size: 0.875rem;
      color: #6b7280;
    }

    .stat-value {
      font-size: 1.5rem;
      font-weight: bold;
    }

    .stat-value.healthy {
      color: #10b981;
    }

    .grid-2 {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 1.5rem;
    }

    .action-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .action-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.75rem;
      border-radius: 8px;
      transition: background 0.2s;
      text-decoration: none;
      color: inherit;
    }

    .action-item:hover {
      background: #f3f4f6;
    }

    .arrow {
      color: var(--primary-600);
    }

    .activity-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .activity-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.875rem;
    }

    .activity-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .activity-dot.success { background: #10b981; }
    .activity-dot.info { background: #3b82f6; }
    .activity-dot.warning { background: #f59e0b; }

    .activity-type {
      color: #6b7280;
    }

    .activity-detail {
      font-weight: 500;
    }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);

  stats: DashboardStats = {
    activeUsers: 0,
    totalPlugins: 0,
    systemHealth: 'Loading...',
    uptime: '0%',
  };

  ngOnInit(): void {
    this.loadStats();
  }

  private loadStats(): void {
    // In production, fetch from API
    // this.http.get<DashboardStats>(`${environment.apiUrl}/v1/dashboard/stats`)
    //   .subscribe(stats => this.stats = stats);

    // Mock data for demonstration
    this.stats = {
      activeUsers: 42,
      totalPlugins: 5,
      systemHealth: 'Healthy',
      uptime: '99.9%',
    };
  }
}
