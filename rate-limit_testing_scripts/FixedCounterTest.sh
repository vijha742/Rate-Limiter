#!/bin/bash

# Script to test the Fixed Counter Rate Limiting Algorithm
# This script demonstrates the edge case behavior where burst traffic at window boundaries
# can exceed the rate limit (e.g., receiving 2x requests across two consecutive windows)

echo "=========================================="
echo "Fixed Counter Algorithm Test"
echo "=========================================="
echo ""

# Configuration
BASE_URL="http://localhost:8080/api/test"
MAX_REQUESTS=10
WINDOW_LENGTH=5  # seconds

echo "Configuration:"
echo "  - Max Requests per Window: $MAX_REQUESTS"
echo "  - Window Length: $WINDOW_LENGTH seconds"
echo "  - Test Endpoint: $BASE_URL"
echo ""

# Test 1: Normal behavior within a single window
echo "Test 1: Sending requests within a single window"
echo "Expected: First $MAX_REQUESTS requests succeed, rest fail"
echo "---"
success_count=0
fail_count=0

for i in $(seq 1 15); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Request $i: SUCCESS (200)"
        ((success_count++))
    else
        echo "Request $i: RATE LIMITED ($response)"
        ((fail_count++))
    fi
    sleep 0.1
done

echo ""
echo "Results: $success_count successful, $fail_count rate-limited"
echo ""

# Wait for window to reset
echo "Waiting for window to reset ($(($WINDOW_LENGTH + 1)) seconds)..."
sleep $(($WINDOW_LENGTH + 1))
echo ""

# Test 2: Edge case - burst at window boundary
echo "Test 2: Testing edge case - burst traffic at window boundary"
echo "Expected: Can send $MAX_REQUESTS at end of window + $MAX_REQUESTS at start of new window"
echo "This demonstrates the 'burst' problem of Fixed Counter algorithm"
echo "---"

# Send requests near end of current window
echo "Sending $MAX_REQUESTS requests..."
for i in $(seq 1 $MAX_REQUESTS); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "Request $i: $response"
    sleep 0.1
done

echo ""
echo "Waiting 1 second before window boundary..."
sleep 1

echo "Calculating time until window reset..."
# Wait until very close to window reset
sleep $(($WINDOW_LENGTH - 2))

echo "Sending burst at window boundary..."
# Send burst right at window boundary
for i in $(seq 1 $MAX_REQUESTS); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "Boundary Request $i: $response"
    sleep 0.01
done

echo ""
echo "Test completed!"
echo ""
echo "=========================================="
echo "Fixed Counter Trade-offs Demonstrated:"
echo "1. Simple and memory efficient"
echo "2. Edge case: Burst at boundaries allows 2x limit"
echo "3. Uneven distribution across time windows"
echo "=========================================="

