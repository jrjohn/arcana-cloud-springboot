/**
 * Audit Plugin JavaScript
 * Client-side functionality for audit log UI components
 */

(function() {
    'use strict';

    // Audit Plugin namespace
    window.ArcanaAudit = window.ArcanaAudit || {};

    const API_BASE = '/api/v1/plugins/audit';

    /**
     * Fetches recent audit entries.
     * @param {number} limit - Maximum entries to return
     * @returns {Promise<Array>} - Array of audit entries
     */
    ArcanaAudit.getRecentEntries = async function(limit = 100) {
        const response = await fetch(`${API_BASE}/entries?limit=${limit}`);
        const data = await response.json();
        return data.success ? data.data : [];
    };

    /**
     * Fetches audit statistics.
     * @returns {Promise<Object>} - Statistics object
     */
    ArcanaAudit.getStatistics = async function() {
        const response = await fetch(`${API_BASE}/statistics`);
        const data = await response.json();
        return data.success ? data.data : null;
    };

    /**
     * Fetches audit entries for an entity.
     * @param {string} entityType - Entity type
     * @param {string} entityId - Entity ID
     * @returns {Promise<Array>} - Array of audit entries
     */
    ArcanaAudit.getEntityHistory = async function(entityType, entityId) {
        const response = await fetch(`${API_BASE}/entities/${entityType}/${entityId}`);
        const data = await response.json();
        return data.success ? data.data : [];
    };

    /**
     * Renders the audit summary widget.
     * @param {HTMLElement} container - Container element
     */
    ArcanaAudit.renderSummaryWidget = async function(container) {
        try {
            const stats = await this.getStatistics();
            if (!stats) {
                container.innerHTML = '<p>Failed to load audit statistics</p>';
                return;
            }

            container.innerHTML = `
                <div class="audit-widget">
                    <div class="audit-widget-header">
                        <h3 class="audit-widget-title">Audit Summary</h3>
                        <svg class="audit-widget-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"/>
                        </svg>
                    </div>
                    <div class="audit-stats">
                        <div class="audit-stat-card today">
                            <div class="audit-stat-value">${stats.todayCount}</div>
                            <div class="audit-stat-label">Today</div>
                        </div>
                        <div class="audit-stat-card week">
                            <div class="audit-stat-value">${stats.thisWeekCount}</div>
                            <div class="audit-stat-label">This Week</div>
                        </div>
                        <div class="audit-stat-card month">
                            <div class="audit-stat-value">${stats.thisMonthCount}</div>
                            <div class="audit-stat-label">This Month</div>
                        </div>
                        <div class="audit-stat-card total">
                            <div class="audit-stat-value">${stats.totalCount}</div>
                            <div class="audit-stat-label">Total</div>
                        </div>
                    </div>
                </div>
            `;
        } catch (error) {
            console.error('Failed to render audit widget:', error);
            container.innerHTML = '<p>Error loading audit data</p>';
        }
    };

    /**
     * Renders the audit log table.
     * @param {HTMLElement} container - Container element
     * @param {number} limit - Maximum entries
     */
    ArcanaAudit.renderLogTable = async function(container, limit = 50) {
        try {
            const entries = await this.getRecentEntries(limit);

            if (entries.length === 0) {
                container.innerHTML = '<p>No audit entries found</p>';
                return;
            }

            const rows = entries.map(entry => `
                <tr>
                    <td class="audit-timestamp">${this.formatTimestamp(entry.timestamp)}</td>
                    <td><span class="audit-action ${this.getActionClass(entry.action)}">${entry.action}</span></td>
                    <td>${entry.entityType || '-'}</td>
                    <td>${entry.username || 'System'}</td>
                    <td><span class="audit-result ${entry.result.toLowerCase()}">${entry.result}</span></td>
                    <td>${entry.details || '-'}</td>
                </tr>
            `).join('');

            container.innerHTML = `
                <table class="audit-table">
                    <thead>
                        <tr>
                            <th>Time</th>
                            <th>Action</th>
                            <th>Entity</th>
                            <th>User</th>
                            <th>Result</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            `;
        } catch (error) {
            console.error('Failed to render audit table:', error);
            container.innerHTML = '<p>Error loading audit data</p>';
        }
    };

    /**
     * Formats a timestamp for display.
     * @param {string} timestamp - ISO timestamp
     * @returns {string} - Formatted timestamp
     */
    ArcanaAudit.formatTimestamp = function(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleString();
    };

    /**
     * Gets the CSS class for an action.
     * @param {string} action - Action name
     * @returns {string} - CSS class
     */
    ArcanaAudit.getActionClass = function(action) {
        if (action.includes('LOGIN')) return 'login';
        if (action.includes('LOGOUT')) return 'logout';
        if (action.includes('CREATED')) return 'created';
        if (action.includes('UPDATED')) return 'updated';
        if (action.includes('DELETED')) return 'deleted';
        return '';
    };

    // Auto-initialize widgets
    document.addEventListener('DOMContentLoaded', function() {
        // Find and render summary widgets
        document.querySelectorAll('[data-audit-widget="summary"]').forEach(el => {
            ArcanaAudit.renderSummaryWidget(el);
        });

        // Find and render log tables
        document.querySelectorAll('[data-audit-widget="table"]').forEach(el => {
            const limit = parseInt(el.dataset.limit) || 50;
            ArcanaAudit.renderLogTable(el, limit);
        });
    });

})();
