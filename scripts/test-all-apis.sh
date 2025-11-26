#!/bin/bash

# Comprehensive API Test Script for Arcana Cloud Spring Boot
# Tests all RESTful APIs across different deployment modes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DOCS_DIR="$PROJECT_DIR/docs"

# Test results storage
declare -A TEST_RESULTS
declare -A TEST_TIMES
declare -A TEST_RESPONSES
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Timestamp for reports
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
REPORT_DATE=$(date +"%Y-%m-%d %H:%M:%S")

# Function to log with color
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_error() { echo -e "${RED}[FAIL]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_section() { echo -e "\n${CYAN}═══════════════════════════════════════════════════════════════${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"; }

# Function to run API test
run_test() {
    local test_name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local headers="$5"
    local expected_status="$6"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    local start_time=$(python3 -c "import time; print(int(time.time() * 1000))")

    if [ "$method" == "GET" ]; then
        if [ -n "$headers" ]; then
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X GET "$url" -H "Content-Type: application/json" -H "$headers" 2>&1)
        else
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X GET "$url" -H "Content-Type: application/json" 2>&1)
        fi
    elif [ "$method" == "POST" ]; then
        if [ -n "$headers" ]; then
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X POST "$url" -H "Content-Type: application/json" -H "$headers" -d "$data" 2>&1)
        else
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X POST "$url" -H "Content-Type: application/json" -d "$data" 2>&1)
        fi
    elif [ "$method" == "PUT" ]; then
        if [ -n "$headers" ]; then
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X PUT "$url" -H "Content-Type: application/json" -H "$headers" -d "$data" 2>&1)
        else
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X PUT "$url" -H "Content-Type: application/json" -d "$data" 2>&1)
        fi
    elif [ "$method" == "DELETE" ]; then
        if [ -n "$headers" ]; then
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X DELETE "$url" -H "Content-Type: application/json" -H "$headers" 2>&1)
        else
            response=$(curl -s -w "\n%{http_code}\n%{time_total}" -X DELETE "$url" -H "Content-Type: application/json" 2>&1)
        fi
    fi

    local end_time=$(python3 -c "import time; print(int(time.time() * 1000))")
    local duration=$((end_time - start_time))

    # Parse response
    local body=$(echo "$response" | head -n -2)
    local status_code=$(echo "$response" | tail -n 2 | head -n 1)
    local curl_time=$(echo "$response" | tail -n 1)

    # Store results
    TEST_TIMES["$test_name"]="${duration}ms"
    TEST_RESPONSES["$test_name"]="$body"

    # Check status
    if [ "$status_code" == "$expected_status" ]; then
        TEST_RESULTS["$test_name"]="PASS"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        log_success "$test_name (${duration}ms) - Status: $status_code"
        return 0
    else
        TEST_RESULTS["$test_name"]="FAIL"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        log_error "$test_name (${duration}ms) - Expected: $expected_status, Got: $status_code"
        return 1
    fi
}

# Function to test a deployment mode
test_mode() {
    local mode_name="$1"
    local base_url="$2"
    local output_dir="$3"

    log_section "Testing $mode_name Mode - $base_url"

    # Reset counters for this mode
    TOTAL_TESTS=0
    PASSED_TESTS=0
    FAILED_TESTS=0
    declare -A TEST_RESULTS
    declare -A TEST_TIMES
    declare -A TEST_RESPONSES

    local report_file="$output_dir/api-test-report-${TIMESTAMP}.md"
    local json_file="$output_dir/api-test-results-${TIMESTAMP}.json"

    # Check if service is available
    log_info "Checking service availability..."
    if ! curl -s --connect-timeout 5 "$base_url/actuator/health" > /dev/null 2>&1; then
        log_error "Service at $base_url is not available"
        echo "Service unavailable" > "$report_file"
        return 1
    fi
    log_success "Service is available"

    # Generate random suffix for unique test data
    RAND=$RANDOM

    # ============================================================
    # Health Check Tests
    # ============================================================
    log_info "Running Health Check Tests..."

    run_test "Health Check" "GET" "$base_url/actuator/health" "" "" "200" || true
    HEALTH_RESPONSE="${TEST_RESPONSES["Health Check"]}"

    # ============================================================
    # Authentication Tests
    # ============================================================
    log_info "Running Authentication Tests..."

    # Register new user
    local register_data="{\"username\":\"testuser${RAND}\",\"email\":\"test${RAND}@example.com\",\"password\":\"Password123!\",\"confirmPassword\":\"Password123!\",\"firstName\":\"Test\",\"lastName\":\"User\"}"
    run_test "Register New User" "POST" "$base_url/api/v1/auth/register" "$register_data" "" "201" || true
    REGISTER_RESPONSE="${TEST_RESPONSES["Register New User"]}"

    # Extract token from registration response
    ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")
    REFRESH_TOKEN=$(echo "$REGISTER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('refreshToken',''))" 2>/dev/null || echo "")
    USER_ID=$(echo "$REGISTER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('user',{}).get('id',''))" 2>/dev/null || echo "")

    # Login with admin credentials
    local login_data='{"usernameOrEmail":"admin","password":"Admin@123"}'
    run_test "Admin Login" "POST" "$base_url/api/v1/auth/login" "$login_data" "" "200" || true
    ADMIN_LOGIN_RESPONSE="${TEST_RESPONSES["Admin Login"]}"

    # Extract admin token
    ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")

    # Login with new user
    local user_login_data="{\"usernameOrEmail\":\"testuser${RAND}\",\"password\":\"Password123!\"}"
    run_test "User Login" "POST" "$base_url/api/v1/auth/login" "$user_login_data" "" "200" || true

    # Invalid login
    local invalid_login='{"usernameOrEmail":"invalid","password":"wrongpassword"}'
    run_test "Invalid Login (should fail)" "POST" "$base_url/api/v1/auth/login" "$invalid_login" "" "401" || true

    # Refresh token
    if [ -n "$REFRESH_TOKEN" ]; then
        local refresh_data="{\"refreshToken\":\"$REFRESH_TOKEN\"}"
        run_test "Refresh Token" "POST" "$base_url/api/v1/auth/refresh" "$refresh_data" "" "200" || true
    fi

    # ============================================================
    # Current User Tests (Requires Auth)
    # ============================================================
    log_info "Running Current User Tests..."

    if [ -n "$ACCESS_TOKEN" ]; then
        run_test "Get Current User Profile" "GET" "$base_url/api/v1/me" "" "Authorization: Bearer $ACCESS_TOKEN" "200" || true
    fi

    # ============================================================
    # User Management Tests (Admin Only)
    # ============================================================
    log_info "Running User Management Tests..."

    if [ -n "$ADMIN_TOKEN" ]; then
        # Get all users
        run_test "Get All Users (Admin)" "GET" "$base_url/api/v1/users" "" "Authorization: Bearer $ADMIN_TOKEN" "200" || true
        USERS_RESPONSE="${TEST_RESPONSES["Get All Users (Admin)"]}"

        # Get user by ID
        if [ -n "$USER_ID" ]; then
            run_test "Get User By ID (Admin)" "GET" "$base_url/api/v1/users/$USER_ID" "" "Authorization: Bearer $ADMIN_TOKEN" "200" || true
        fi

        # Create new user (admin)
        local create_user_data="{\"username\":\"adminuser${RAND}\",\"email\":\"admin${RAND}@example.com\",\"password\":\"Password123!\",\"firstName\":\"Admin\",\"lastName\":\"Created\",\"roles\":[\"USER\"]}"
        run_test "Create User (Admin)" "POST" "$base_url/api/v1/users" "$create_user_data" "Authorization: Bearer $ADMIN_TOKEN" "201" || true
        CREATE_USER_RESPONSE="${TEST_RESPONSES["Create User (Admin)"]}"

        CREATED_USER_ID=$(echo "$CREATE_USER_RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

        # Update user
        if [ -n "$CREATED_USER_ID" ]; then
            local update_user_data='{"firstName":"Updated","lastName":"Name"}'
            run_test "Update User (Admin)" "PUT" "$base_url/api/v1/users/$CREATED_USER_ID" "$update_user_data" "Authorization: Bearer $ADMIN_TOKEN" "200" || true

            # Delete user
            run_test "Delete User (Admin)" "DELETE" "$base_url/api/v1/users/$CREATED_USER_ID" "" "Authorization: Bearer $ADMIN_TOKEN" "200" || true
        fi

        # Unauthorized access tests
        run_test "Get Users Without Auth (should fail)" "GET" "$base_url/api/v1/users" "" "" "401" || true
    fi

    # ============================================================
    # Logout Tests
    # ============================================================
    log_info "Running Logout Tests..."

    if [ -n "$ACCESS_TOKEN" ]; then
        run_test "Logout" "POST" "$base_url/api/v1/auth/logout" "" "Authorization: Bearer $ACCESS_TOKEN" "200" || true
    fi

    # ============================================================
    # Generate Reports
    # ============================================================
    log_section "Generating Reports for $mode_name"

    # Calculate pass rate
    local pass_rate=0
    if [ $TOTAL_TESTS -gt 0 ]; then
        pass_rate=$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)
    fi

    # Generate Markdown Report
    cat > "$report_file" << EOF
# API Test Report - $mode_name Mode

**Generated:** $REPORT_DATE
**Base URL:** $base_url
**Mode:** $mode_name

## Summary

| Metric | Value |
|--------|-------|
| Total Tests | $TOTAL_TESTS |
| Passed | $PASSED_TESTS |
| Failed | $FAILED_TESTS |
| Pass Rate | ${pass_rate}% |

## Test Results

| Test Name | Status | Duration |
|-----------|--------|----------|
EOF

    for test_name in "${!TEST_RESULTS[@]}"; do
        local status="${TEST_RESULTS[$test_name]}"
        local duration="${TEST_TIMES[$test_name]}"
        local status_icon="✅"
        [ "$status" == "FAIL" ] && status_icon="❌"
        echo "| $test_name | $status_icon $status | $duration |" >> "$report_file"
    done

    cat >> "$report_file" << EOF

## API Endpoints Tested

### Authentication APIs
- \`POST /api/v1/auth/register\` - Register new user
- \`POST /api/v1/auth/login\` - User login
- \`POST /api/v1/auth/refresh\` - Refresh access token
- \`POST /api/v1/auth/logout\` - Logout

### User Management APIs
- \`GET /api/v1/users\` - Get all users (Admin)
- \`GET /api/v1/users/{id}\` - Get user by ID
- \`POST /api/v1/users\` - Create user (Admin)
- \`PUT /api/v1/users/{id}\` - Update user
- \`DELETE /api/v1/users/{id}\` - Delete user (Admin)

### Current User APIs
- \`GET /api/v1/me\` - Get current user profile

### Health Check
- \`GET /actuator/health\` - Service health status

## Health Check Response

\`\`\`json
$(echo "$HEALTH_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$HEALTH_RESPONSE")
\`\`\`

---
*Report generated by Arcana Cloud API Test Suite*
EOF

    # Generate JSON Report
    cat > "$json_file" << EOF
{
  "reportMetadata": {
    "generatedAt": "$REPORT_DATE",
    "baseUrl": "$base_url",
    "mode": "$mode_name",
    "timestamp": "$TIMESTAMP"
  },
  "summary": {
    "totalTests": $TOTAL_TESTS,
    "passed": $PASSED_TESTS,
    "failed": $FAILED_TESTS,
    "passRate": $pass_rate
  },
  "results": [
EOF

    local first=true
    for test_name in "${!TEST_RESULTS[@]}"; do
        local status="${TEST_RESULTS[$test_name]}"
        local duration="${TEST_TIMES[$test_name]}"

        if [ "$first" = true ]; then
            first=false
        else
            echo "," >> "$json_file"
        fi

        cat >> "$json_file" << EOF
    {
      "name": "$test_name",
      "status": "$status",
      "duration": "$duration"
    }
EOF
    done

    cat >> "$json_file" << EOF

  ]
}
EOF

    log_success "Reports generated:"
    log_info "  Markdown: $report_file"
    log_info "  JSON: $json_file"

    return 0
}

# Main execution
main() {
    log_section "Arcana Cloud API Test Suite"
    log_info "Starting comprehensive API tests..."
    log_info "Timestamp: $TIMESTAMP"

    # Create docs directories
    mkdir -p "$DOCS_DIR/monolithic"
    mkdir -p "$DOCS_DIR/layered-grpc"
    mkdir -p "$DOCS_DIR/k8s-grpc"

    # Test configurations
    declare -A MODES
    MODES["monolithic"]="http://localhost:30080"
    MODES["layered-grpc"]="http://localhost:8080"
    MODES["k8s-grpc"]="http://localhost:30080"

    declare -A MODE_DIRS
    MODE_DIRS["monolithic"]="$DOCS_DIR/monolithic"
    MODE_DIRS["layered-grpc"]="$DOCS_DIR/layered-grpc"
    MODE_DIRS["k8s-grpc"]="$DOCS_DIR/k8s-grpc"

    # Store results for HTML report
    declare -A MODE_RESULTS

    # Test each mode
    for mode in "${!MODES[@]}"; do
        local url="${MODES[$mode]}"
        local dir="${MODE_DIRS[$mode]}"

        if test_mode "$mode" "$url" "$dir"; then
            MODE_RESULTS[$mode]="PASS:$PASSED_TESTS:$TOTAL_TESTS"
        else
            MODE_RESULTS[$mode]="FAIL:$PASSED_TESTS:$TOTAL_TESTS"
        fi
    done

    log_section "Test Suite Complete"
    log_info "Reports generated in $DOCS_DIR"
}

# Run main function
main "$@"
