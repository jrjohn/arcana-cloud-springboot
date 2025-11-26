import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="card"><h1>Settings</h1><p>Application settings coming soon.</p></div>`,
})
export class SettingsComponent {}
