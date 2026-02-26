#!/usr/bin/env python3
"""
Comprehensive API Test Script for Arcana Cloud Spring Boot
Tests all RESTful APIs across different deployment modes and generates reports.
Uses only standard library - no external dependencies required.
"""

import json
import os
import random
import string
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Dict, List, Optional, Any, Tuple
from pathlib import Path


@dataclass
class TestResult:
    """Represents a single test result."""
    name: str
    method: str
    endpoint: str
    status: str  # PASS, FAIL, SKIP
    expected_status: int
    actual_status: int
    duration_ms: float
    response_body: str = ""
    error_message: str = ""


@dataclass
class ModeTestResults:
    """Results for a deployment mode."""
    mode_name: str
    base_url: str
    timestamp: str
    total_tests: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    results: List[TestResult] = field(default_factory=list)

    @property
    def pass_rate(self) -> float:
        if self.total_tests == 0:
            return 0.0
        return round((self.passed / self.total_tests) * 100, 1)


API_AUTH_LOGIN = "/api/v1/auth/login"
API_USERS = "/api/v1/users"


class APITester:
    """API Testing class with comprehensive test coverage using curl."""

    def __init__(self, base_url: str, mode_name: str):
        self.base_url = base_url.rstrip('/')
        self.mode_name = mode_name
        self.results = ModeTestResults(
            mode_name=mode_name,
            base_url=base_url,
            timestamp=datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        )
        self.admin_token: Optional[str] = None
        self.user_token: Optional[str] = None
        self.user_refresh_token: Optional[str] = None
        self.test_user_id: Optional[int] = None
        self.created_user_id: Optional[int] = None
        self.random_suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=6))

    def log(self, level: str, message: str):
        """Log messages with formatting."""
        colors = {
            'INFO': '\033[94m',
            'PASS': '\033[92m',
            'FAIL': '\033[91m',
            'WARN': '\033[93m',
            'SECTION': '\033[96m',
        }
        reset = '\033[0m'
        color = colors.get(level, '')
        print(f"{color}[{level}]{reset} {message}")

    def curl_request(self, method: str, url: str, data: Optional[Dict] = None,
                     headers: Optional[Dict] = None, timeout: int = 10) -> Tuple[int, str, float]:
        """Execute HTTP request using curl."""
        cmd = ['curl', '-s', '-w', '\n%{http_code}', '-X', method, url,
               '--connect-timeout', str(timeout), '-H', 'Content-Type: application/json']

        if headers:
            for key, value in headers.items():
                cmd.extend(['-H', f'{key}: {value}'])

        if data and method in ['POST', 'PUT', 'PATCH']:
            cmd.extend(['-d', json.dumps(data)])

        start_time = time.time()
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
            duration_ms = round((time.time() - start_time) * 1000, 2)

            output = result.stdout.strip()
            lines = output.split('\n')
            status_code = int(lines[-1]) if lines else 0
            body = '\n'.join(lines[:-1]) if len(lines) > 1 else ''

            return status_code, body, duration_ms
        except subprocess.TimeoutExpired:
            duration_ms = round((time.time() - start_time) * 1000, 2)
            return 0, 'Request timeout', duration_ms
        except Exception as e:
            duration_ms = round((time.time() - start_time) * 1000, 2)
            return 0, str(e), duration_ms

    def run_test(self, name: str, method: str, endpoint: str,
                 data: Optional[Dict] = None, headers: Optional[Dict] = None,
                 expected_status: int = 200) -> TestResult:
        """Execute a single API test."""
        url = f"{self.base_url}{endpoint}"

        status_code, body, duration_ms = self.curl_request(method, url, data, headers)

        status = 'PASS' if status_code == expected_status else 'FAIL'
        result = TestResult(
            name=name,
            method=method,
            endpoint=endpoint,
            status=status,
            expected_status=expected_status,
            actual_status=status_code,
            duration_ms=duration_ms,
            response_body=body[:2000] if body else ""
        )

        self.results.results.append(result)
        self.results.total_tests += 1
        if result.status == 'PASS':
            self.results.passed += 1
            self.log('PASS', f"{name} ({duration_ms}ms) - Status: {status_code}")
        else:
            self.results.failed += 1
            self.log('FAIL', f"{name} ({duration_ms}ms) - Expected: {expected_status}, Got: {status_code}")

        return result

    def check_service(self) -> bool:
        """Check if the service is available."""
        status_code, _, _ = self.curl_request('GET', f"{self.base_url}/actuator/health", timeout=5)
        return status_code == 200

    def extract_tokens(self, response_body: str) -> tuple:
        """Extract access and refresh tokens from response."""
        try:
            data = json.loads(response_body)
            access_token = data.get('data', {}).get('accessToken', '')
            refresh_token = data.get('data', {}).get('refreshToken', '')
            user_id = data.get('data', {}).get('user', {}).get('id')
            return access_token, refresh_token, user_id
        except Exception as e:
            self.log('WARN', f"Failed to extract tokens: {e}")
            return '', '', None

    def extract_user_id(self, response_body: str) -> Optional[int]:
        """Extract user ID from response."""
        try:
            data = json.loads(response_body)
            return data.get('data', {}).get('id')
        except Exception as e:
            self.log('WARN', f"Failed to extract user ID: {e}")
            return None

    def run_all_tests(self):
        """Run all API tests."""
        self.log('SECTION', f"{'='*60}")
        self.log('SECTION', f"  Testing {self.mode_name} Mode - {self.base_url}")
        self.log('SECTION', f"{'='*60}")

        # Check service availability
        self.log('INFO', "Checking service availability...")
        if not self.check_service():
            self.log('FAIL', f"Service at {self.base_url} is not available")
            return False
        self.log('PASS', "Service is available")

        # Run test groups
        self.test_health_checks()
        self.test_authentication()
        self.test_current_user()
        self.test_user_management()
        self.test_logout()

        return True

    def test_health_checks(self):
        """Test health check endpoints."""
        self.log('INFO', "Running Health Check Tests...")
        self.run_test("Health Check", "GET", "/actuator/health", expected_status=200)

    def _perform_admin_login(self) -> TestResult:
        """Try multiple admin accounts and return the login test result."""
        admin_accounts = [
            {"usernameOrEmail": "sysadmin", "password": "Admin123"},
            {"usernameOrEmail": "testadmin", "password": "Admin123"},
        ]
        for admin_login in admin_accounts:
            status_code, body, duration = self.curl_request(
                'POST', f"{self.base_url}{API_AUTH_LOGIN}", admin_login
            )
            if status_code == 200:
                self.admin_token, _, _ = self.extract_tokens(body)
                return TestResult(
                    name="Admin Login",
                    method="POST",
                    endpoint=API_AUTH_LOGIN,
                    status="PASS",
                    expected_status=200,
                    actual_status=status_code,
                    duration_ms=round(duration, 2),
                    response_body=body[:500] if body else "",
                )
        return self.run_test("Admin Login", "POST", API_AUTH_LOGIN,
                             data=admin_accounts[0], expected_status=200)

    def _record_admin_login_result(self, result: TestResult):
        """Log and record the admin login result in the test suite."""
        self.log('PASS' if result.status == 'PASS' else 'FAIL',
                 f"Admin Login ({result.duration_ms}ms) - Status: {result.actual_status}")
        self.results.results.append(result)
        self.results.total_tests += 1
        if result.status == 'PASS':
            self.results.passed += 1
        else:
            self.results.failed += 1

    def _perform_refresh_token_test(self):
        """Register a fresh user and test token refresh."""
        fresh_rand = ''.join(random.choices(string.ascii_lowercase + string.digits, k=6))
        fresh_register = {
            "username": f"refresh{fresh_rand}",
            "email": f"refresh{fresh_rand}@example.com",
            "password": "Password123",
            "confirmPassword": "Password123",
            "firstName": "Refresh",
            "lastName": "Test",
        }
        status_code, body, _ = self.curl_request(
            'POST', f"{self.base_url}/api/v1/auth/register", fresh_register
        )
        if status_code != 201:
            return
        _, fresh_refresh_token, _ = self.extract_tokens(body)
        if fresh_refresh_token:
            self.run_test("Refresh Token", "POST", "/api/v1/auth/refresh",
                          data={"refreshToken": fresh_refresh_token}, expected_status=200)

    def test_authentication(self):
        """Test authentication endpoints."""
        self.log('INFO', "Running Authentication Tests...")

        # Register new user
        register_data = {
            "username": f"testuser{self.random_suffix}",
            "email": f"test{self.random_suffix}@example.com",
            "password": "Password123",
            "confirmPassword": "Password123",
            "firstName": "Test",
            "lastName": "User",
        }
        result = self.run_test("Register New User", "POST", "/api/v1/auth/register",
                               data=register_data, expected_status=201)
        if result.status == 'PASS':
            self.user_token, self.user_refresh_token, self.test_user_id = \
                self.extract_tokens(result.response_body)

        # Admin login - try multiple admin accounts
        admin_result = self._perform_admin_login()
        self._record_admin_login_result(admin_result)

        # User login
        user_login = {"usernameOrEmail": f"testuser{self.random_suffix}", "password": "Password123"}
        self.run_test("User Login", "POST", API_AUTH_LOGIN,
                      data=user_login, expected_status=200)

        # Invalid login
        invalid_login = {"usernameOrEmail": "invalid", "password": "wrongpassword"}
        self.run_test("Invalid Login (should fail)", "POST", API_AUTH_LOGIN,
                      data=invalid_login, expected_status=401)

        # Refresh token - use a fresh token from a new registration
        self._perform_refresh_token_test()

    def test_current_user(self):
        """Test current user endpoints."""
        self.log('INFO', "Running Current User Tests...")

        if self.user_token:
            headers = {"Authorization": f"Bearer {self.user_token}"}
            self.run_test("Get Current User Profile", "GET", "/api/v1/me",
                         headers=headers, expected_status=200)

    def test_user_management(self):
        """Test user management endpoints (admin only)."""
        self.log('INFO', "Running User Management Tests...")

        if not self.admin_token:
            self.log('WARN', "Skipping admin tests - no admin token available")
            return

        headers = {"Authorization": f"Bearer {self.admin_token}"}

        # Get all users
        self.run_test("Get All Users (Admin)", "GET", API_USERS,
                     headers=headers, expected_status=200)

        # Get user by ID
        if self.test_user_id:
            self.run_test("Get User By ID (Admin)", "GET", f"/api/v1/users/{self.test_user_id}",
                         headers=headers, expected_status=200)

        # Create new user
        create_data = {
            "username": f"adminuser{self.random_suffix}",
            "email": f"admin{self.random_suffix}@example.com",
            "password": "Password123",
            "firstName": "Admin",
            "lastName": "Created",
            "roles": ["USER"]
        }
        result = self.run_test("Create User (Admin)", "POST", API_USERS,
                              data=create_data, headers=headers, expected_status=201)

        if result.status == 'PASS':
            self.created_user_id = self.extract_user_id(result.response_body)

        # Update user
        if self.created_user_id:
            update_data = {"firstName": "Updated", "lastName": "Name"}
            self.run_test("Update User (Admin)", "PUT", f"/api/v1/users/{self.created_user_id}",
                         data=update_data, headers=headers, expected_status=200)

            # Delete user
            self.run_test("Delete User (Admin)", "DELETE", f"/api/v1/users/{self.created_user_id}",
                         headers=headers, expected_status=200)

        # Unauthorized access (403 Forbidden is also acceptable)
        self.run_test("Get Users Without Auth (should fail)", "GET", API_USERS,
                     expected_status=403)

    def test_logout(self):
        """Test logout endpoints."""
        self.log('INFO', "Running Logout Tests...")

        if self.user_token:
            headers = {"Authorization": f"Bearer {self.user_token}"}
            self.run_test("Logout", "POST", "/api/v1/auth/logout",
                         headers=headers, expected_status=200)


def generate_markdown_report(results: ModeTestResults, output_dir: Path) -> Path:
    """Generate a markdown report."""
    report_file = output_dir / f"api-test-report-{datetime.now().strftime('%Y%m%d_%H%M%S')}.md"

    content = f"""# API Test Report - {results.mode_name} Mode

**Generated:** {results.timestamp}
**Base URL:** {results.base_url}
**Mode:** {results.mode_name}

## Summary

| Metric | Value |
|--------|-------|
| Total Tests | {results.total_tests} |
| Passed | {results.passed} |
| Failed | {results.failed} |
| Pass Rate | {results.pass_rate}% |

## Test Results

| Test Name | Method | Endpoint | Status | Expected | Actual | Duration |
|-----------|--------|----------|--------|----------|--------|----------|
"""

    for r in results.results:
        status_icon = "✅" if r.status == "PASS" else "❌"
        content += f"| {r.name} | {r.method} | {r.endpoint} | {status_icon} {r.status} | {r.expected_status} | {r.actual_status} | {r.duration_ms}ms |\n"

    content += """
## API Endpoints Tested

### Authentication APIs
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/logout` - Logout

### User Management APIs
- `GET /api/v1/users` - Get all users (Admin)
- `GET /api/v1/users/{id}` - Get user by ID
- `POST /api/v1/users` - Create user (Admin)
- `PUT /api/v1/users/{id}` - Update user
- `DELETE /api/v1/users/{id}` - Delete user (Admin)

### Current User APIs
- `GET /api/v1/me` - Get current user profile

### Health Check
- `GET /actuator/health` - Service health status

---
*Report generated by Arcana Cloud API Test Suite*
"""

    report_file.write_text(content)
    return report_file


def generate_json_report(results: ModeTestResults, output_dir: Path) -> Path:
    """Generate a JSON report."""
    report_file = output_dir / f"api-test-results-{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"

    data = {
        "reportMetadata": {
            "generatedAt": results.timestamp,
            "baseUrl": results.base_url,
            "mode": results.mode_name
        },
        "summary": {
            "totalTests": results.total_tests,
            "passed": results.passed,
            "failed": results.failed,
            "passRate": results.pass_rate
        },
        "results": [asdict(r) for r in results.results]
    }

    report_file.write_text(json.dumps(data, indent=2))
    return report_file


def generate_html_report(all_results: Dict[str, ModeTestResults], output_dir: Path) -> Path:
    """Generate a fancy HTML report with all test results."""
    report_file = output_dir / f"api-test-report-{datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # Calculate totals
    total_tests = sum(r.total_tests for r in all_results.values())
    total_passed = sum(r.passed for r in all_results.values())
    total_failed = sum(r.failed for r in all_results.values())
    overall_pass_rate = round((total_passed / total_tests * 100), 1) if total_tests > 0 else 0

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Arcana Cloud API Test Report</title>
    <style>
        :root {{
            --primary-color: #6366f1;
            --primary-light: #818cf8;
            --success-color: #10b981;
            --error-color: #ef4444;
            --warning-color: #f59e0b;
            --bg-color: #0f172a;
            --card-bg: #1e293b;
            --text-color: #e2e8f0;
            --text-muted: #94a3b8;
            --border-color: #334155;
        }}

        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}

        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: var(--bg-color);
            color: var(--text-color);
            line-height: 1.6;
            min-height: 100vh;
        }}

        .container {{
            max-width: 1400px;
            margin: 0 auto;
            padding: 2rem;
        }}

        header {{
            text-align: center;
            margin-bottom: 3rem;
            padding: 2rem;
            background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
            border-radius: 16px;
            box-shadow: 0 10px 40px rgba(99, 102, 241, 0.3);
        }}

        header h1 {{
            font-size: 2.5rem;
            margin-bottom: 0.5rem;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }}

        header p {{
            font-size: 1.1rem;
            opacity: 0.9;
        }}

        .summary-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1.5rem;
            margin-bottom: 3rem;
        }}

        .summary-card {{
            background: var(--card-bg);
            border-radius: 12px;
            padding: 1.5rem;
            text-align: center;
            border: 1px solid var(--border-color);
            transition: transform 0.3s, box-shadow 0.3s;
        }}

        .summary-card:hover {{
            transform: translateY(-5px);
            box-shadow: 0 10px 30px rgba(0,0,0,0.3);
        }}

        .summary-card .value {{
            font-size: 2.5rem;
            font-weight: bold;
            margin-bottom: 0.5rem;
        }}

        .summary-card .label {{
            color: var(--text-muted);
            font-size: 0.9rem;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}

        .summary-card.total .value {{ color: var(--primary-light); }}
        .summary-card.passed .value {{ color: var(--success-color); }}
        .summary-card.failed .value {{ color: var(--error-color); }}
        .summary-card.rate .value {{ color: var(--warning-color); }}

        .mode-section {{
            background: var(--card-bg);
            border-radius: 16px;
            margin-bottom: 2rem;
            overflow: hidden;
            border: 1px solid var(--border-color);
        }}

        .mode-header {{
            background: linear-gradient(90deg, var(--primary-color), transparent);
            padding: 1.5rem 2rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            cursor: pointer;
        }}

        .mode-header h2 {{
            font-size: 1.5rem;
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }}

        .mode-header .icon {{
            width: 32px;
            height: 32px;
            background: rgba(255,255,255,0.2);
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
        }}

        .mode-stats {{
            display: flex;
            gap: 2rem;
            font-size: 0.9rem;
        }}

        .mode-stats span {{
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }}

        .mode-stats .pass {{ color: var(--success-color); }}
        .mode-stats .fail {{ color: var(--error-color); }}

        .mode-content {{
            padding: 1.5rem 2rem;
        }}

        .test-table {{
            width: 100%;
            border-collapse: collapse;
        }}

        .test-table th,
        .test-table td {{
            padding: 1rem;
            text-align: left;
            border-bottom: 1px solid var(--border-color);
        }}

        .test-table th {{
            background: rgba(99, 102, 241, 0.1);
            font-weight: 600;
            text-transform: uppercase;
            font-size: 0.8rem;
            letter-spacing: 1px;
            color: var(--text-muted);
        }}

        .test-table tr:hover {{
            background: rgba(255,255,255,0.02);
        }}

        .status-badge {{
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.35rem 0.75rem;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 500;
        }}

        .status-badge.pass {{
            background: rgba(16, 185, 129, 0.2);
            color: var(--success-color);
        }}

        .status-badge.fail {{
            background: rgba(239, 68, 68, 0.2);
            color: var(--error-color);
        }}

        .method-badge {{
            display: inline-block;
            padding: 0.25rem 0.5rem;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 600;
            font-family: monospace;
        }}

        .method-badge.GET {{ background: rgba(16, 185, 129, 0.3); color: #10b981; }}
        .method-badge.POST {{ background: rgba(99, 102, 241, 0.3); color: #818cf8; }}
        .method-badge.PUT {{ background: rgba(245, 158, 11, 0.3); color: #f59e0b; }}
        .method-badge.DELETE {{ background: rgba(239, 68, 68, 0.3); color: #ef4444; }}

        .endpoint {{
            font-family: 'Consolas', monospace;
            color: var(--text-muted);
            font-size: 0.9rem;
        }}

        .duration {{
            color: var(--text-muted);
            font-family: monospace;
        }}

        .progress-bar {{
            height: 8px;
            background: var(--border-color);
            border-radius: 4px;
            overflow: hidden;
            margin-top: 0.5rem;
        }}

        .progress-bar .fill {{
            height: 100%;
            background: linear-gradient(90deg, var(--success-color), var(--primary-color));
            border-radius: 4px;
            transition: width 1s ease-out;
        }}

        footer {{
            text-align: center;
            padding: 2rem;
            color: var(--text-muted);
            font-size: 0.9rem;
        }}

        footer a {{
            color: var(--primary-light);
            text-decoration: none;
        }}

        .chart-container {{
            display: flex;
            justify-content: center;
            gap: 3rem;
            margin: 2rem 0;
            flex-wrap: wrap;
        }}

        .donut-chart {{
            position: relative;
            width: 150px;
            height: 150px;
        }}

        .donut-chart svg {{
            transform: rotate(-90deg);
        }}

        .donut-chart circle {{
            fill: none;
            stroke-width: 20;
        }}

        .donut-chart .bg {{
            stroke: var(--border-color);
        }}

        .donut-chart .progress {{
            stroke: var(--success-color);
            stroke-dasharray: 0 314;
            transition: stroke-dasharray 1s ease-out;
        }}

        .donut-chart .center-text {{
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            text-align: center;
        }}

        .donut-chart .percentage {{
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--success-color);
        }}

        .donut-chart .label {{
            font-size: 0.75rem;
            color: var(--text-muted);
        }}

        @keyframes fadeIn {{
            from {{ opacity: 0; transform: translateY(20px); }}
            to {{ opacity: 1; transform: translateY(0); }}
        }}

        .animate-in {{
            animation: fadeIn 0.5s ease-out forwards;
        }}

        .mode-section:nth-child(1) {{ animation-delay: 0.1s; }}
        .mode-section:nth-child(2) {{ animation-delay: 0.2s; }}
        .mode-section:nth-child(3) {{ animation-delay: 0.3s; }}

        .unavailable {{
            background: rgba(239, 68, 68, 0.1);
            border: 1px dashed var(--error-color);
        }}

        .unavailable .mode-header {{
            background: linear-gradient(90deg, rgba(239, 68, 68, 0.5), transparent);
        }}

        .unavailable .mode-content {{
            text-align: center;
            padding: 2rem;
            color: var(--text-muted);
        }}
    </style>
</head>
<body>
    <div class="container">
        <header class="animate-in">
            <h1>Arcana Cloud API Test Report</h1>
            <p>Comprehensive API Testing Results - {timestamp}</p>
        </header>

        <div class="summary-grid">
            <div class="summary-card total animate-in">
                <div class="value">{total_tests}</div>
                <div class="label">Total Tests</div>
            </div>
            <div class="summary-card passed animate-in">
                <div class="value">{total_passed}</div>
                <div class="label">Passed</div>
            </div>
            <div class="summary-card failed animate-in">
                <div class="value">{total_failed}</div>
                <div class="label">Failed</div>
            </div>
            <div class="summary-card rate animate-in">
                <div class="value">{overall_pass_rate}%</div>
                <div class="label">Pass Rate</div>
            </div>
        </div>

        <div class="chart-container animate-in">
            <div class="donut-chart">
                <svg width="150" height="150" viewBox="0 0 150 150">
                    <circle class="bg" cx="75" cy="75" r="50"></circle>
                    <circle class="progress" cx="75" cy="75" r="50"
                            style="stroke-dasharray: {overall_pass_rate * 3.14} 314"></circle>
                </svg>
                <div class="center-text">
                    <div class="percentage">{overall_pass_rate}%</div>
                    <div class="label">Overall</div>
                </div>
            </div>
        </div>
"""

    # Generate mode sections
    mode_icons = {
        'monolithic': '🏢',
        'layered': '📡',
        'microservices': '☸️'
    }

    for mode_name, results in all_results.items():
        icon = mode_icons.get(mode_name, '🔧')

        if results.total_tests == 0:
            # Service unavailable
            html_content += f"""
        <div class="mode-section unavailable animate-in">
            <div class="mode-header">
                <h2>
                    <span class="icon">{icon}</span>
                    {mode_name.replace('-', ' ').title()} Mode
                </h2>
                <div class="mode-stats">
                    <span class="fail">⚠️ Service Unavailable</span>
                </div>
            </div>
            <div class="mode-content">
                <p>Service at <code>{results.base_url}</code> is not available.</p>
                <p style="margin-top: 1rem;">Please ensure the service is running and try again.</p>
            </div>
        </div>
"""
        else:
            html_content += f"""
        <div class="mode-section animate-in">
            <div class="mode-header" onclick="this.parentElement.querySelector('.mode-content').classList.toggle('hidden')">
                <h2>
                    <span class="icon">{icon}</span>
                    {mode_name.replace('-', ' ').title()} Mode
                </h2>
                <div class="mode-stats">
                    <span class="pass">✓ {results.passed} Passed</span>
                    <span class="fail">✗ {results.failed} Failed</span>
                    <span>{results.pass_rate}%</span>
                </div>
            </div>
            <div class="mode-content">
                <p style="margin-bottom: 1rem; color: var(--text-muted);">
                    Base URL: <code>{results.base_url}</code>
                </p>
                <div class="progress-bar">
                    <div class="fill" style="width: {results.pass_rate}%"></div>
                </div>
                <table class="test-table">
                    <thead>
                        <tr>
                            <th>Test Name</th>
                            <th>Method</th>
                            <th>Endpoint</th>
                            <th>Expected</th>
                            <th>Actual</th>
                            <th>Duration</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
"""

            for r in results.results:
                status_class = 'pass' if r.status == 'PASS' else 'fail'
                status_text = '✓ PASS' if r.status == 'PASS' else '✗ FAIL'
                html_content += f"""
                        <tr>
                            <td>{r.name}</td>
                            <td><span class="method-badge {r.method}">{r.method}</span></td>
                            <td class="endpoint">{r.endpoint}</td>
                            <td>{r.expected_status}</td>
                            <td>{r.actual_status}</td>
                            <td class="duration">{r.duration_ms}ms</td>
                            <td><span class="status-badge {status_class}">{status_text}</span></td>
                        </tr>
"""

            html_content += """
                    </tbody>
                </table>
            </div>
        </div>
"""

    html_content += f"""
        <footer>
            <p>Generated by Arcana Cloud API Test Suite</p>
            <p>Report generated at {timestamp}</p>
        </footer>
    </div>

    <script>
        // Animate progress bars on load
        document.addEventListener('DOMContentLoaded', function() {{
            const progressBars = document.querySelectorAll('.progress-bar .fill');
            setTimeout(() => {{
                progressBars.forEach(bar => {{
                    bar.style.width = bar.style.width;
                }});
            }}, 500);
        }});
    </script>
</body>
</html>
"""

    report_file.write_text(html_content)
    return report_file


def main():
    """Main entry point."""
    script_dir = Path(__file__).parent
    project_dir = script_dir.parent
    docs_dir = project_dir / "docs"

    # Create directories
    for mode_dir in ['monolithic', 'layered', 'microservices']:
        (docs_dir / mode_dir).mkdir(parents=True, exist_ok=True)

    print("\n" + "="*60)
    print("  Arcana Cloud API Test Suite")
    print("  Spring Boot | Java 21 | gRPC-first Architecture")
    print("="*60 + "\n")

    # Test configurations
    # - Monolithic: All layers in single application (Docker standalone or K8s single pod)
    # - Layered: Separate layers communicating via gRPC (Docker Compose)
    # - Microservices: K8s deployment with gRPC inter-service communication
    modes = {
        'monolithic': 'http://localhost:8080',
        'layered': 'http://localhost:8090',  # Layered mode (gRPC or HTTP inter-service communication)
        'microservices': 'http://localhost:30080',
    }

    all_results: Dict[str, ModeTestResults] = {}

    for mode_name, base_url in modes.items():
        output_dir = docs_dir / mode_name

        tester = APITester(base_url, mode_name)
        success = tester.run_all_tests()

        all_results[mode_name] = tester.results

        if success and tester.results.total_tests > 0:
            # Generate individual reports
            md_file = generate_markdown_report(tester.results, output_dir)
            json_file = generate_json_report(tester.results, output_dir)
            print(f"\n[INFO] Reports generated for {mode_name}:")
            print(f"  - Markdown: {md_file}")
            print(f"  - JSON: {json_file}")
        else:
            print(f"\n[WARN] {mode_name} service not available, skipping detailed reports...")

    # Generate combined HTML report
    html_file = generate_html_report(all_results, docs_dir)
    print(f"\n[INFO] Combined HTML Report: {html_file}")

    # Summary
    print("\n" + "="*60)
    print("  Test Suite Complete")
    print("="*60)

    total_tests = sum(r.total_tests for r in all_results.values())
    total_passed = sum(r.passed for r in all_results.values())
    total_failed = sum(r.failed for r in all_results.values())

    print(f"\nOverall Summary:")
    print(f"  Total Tests: {total_tests}")
    print(f"  Passed: {total_passed}")
    print(f"  Failed: {total_failed}")
    if total_tests > 0:
        print(f"  Pass Rate: {round(total_passed/total_tests*100, 1)}%")

    print(f"\nReports saved to: {docs_dir}")

    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
