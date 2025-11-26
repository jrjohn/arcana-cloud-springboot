import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="card"><h1>Audit Logs</h1><p>Audit log viewer coming soon.</p></div>`,
})
export class AuditComponent {}
