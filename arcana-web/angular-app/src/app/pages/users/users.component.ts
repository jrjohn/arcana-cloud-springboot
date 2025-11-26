import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="card"><h1>Users</h1><p>User management coming soon.</p></div>`,
})
export class UsersComponent {}
