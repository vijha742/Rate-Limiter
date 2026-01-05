#!/bin/bash

# Script to test the Sliding Window Rate Limiting Algorithm
# This algorithm provides smoother rate limiting by considering a rolling time window
# rather than fixed boundaries, preventing the burst problem of Fixed Counter

echo "=========================================="
echo "Sliding Window Algorithm Test"
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

# Test 1: Uniform distribution test
echo "Test 1: Uniform request distribution"
echo "Expected: Smooth rate limiting across rolling window"
echo "---"
success_count=0
fail_count=0

for i in $(seq 1 20); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Request $i: SUCCESS (200)"
        ((success_count++))
    else
        echo "Request $i: RATE LIMITED ($response)"
        ((fail_count++))
    fi
    sleep 0.5
done

echo ""
echo "Results: $success_count successful, $fail_count rate-limited"
echo ""

# Test 2: Burst prevention at boundaries
echo "Test 2: Testing burst prevention at window boundaries"
echo "Expected: Unlike Fixed Counter, sliding window prevents double-rate bursts"
echo "---"

echo "Waiting for window to clear..."
sleep $(($WINDOW_LENGTH + 1))
echo ""

# Send burst of requests
echo "Sending rapid burst of requests..."
burst_success=0
burst_fail=0

for i in $(seq 1 25); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Burst Request $i: SUCCESS (200)"
        ((burst_success++))
    else
        echo "Burst Request $i: RATE LIMITED ($response)"
        ((burst_fail++))
    fi
    sleep 0.05
done

echo ""
echo "Burst Results: $burst_success successful, $burst_fail rate-limited"
echo ""

# Test 3: Sliding behavior verification
echo "Test 3: Verifying sliding window behavior"
echo "Sending requests, waiting, then resuming..."
echo "---"

echo "Sending initial batch..."
for i in $(seq 1 5); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "Initial Request $i: $response"
    sleep 0.1
done

echo ""
echo "Waiting 3 seconds (partial window slide)..."
sleep 3

echo "Sending second batch (should have room due to sliding)..."
for i in $(seq 1 8); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "Second Batch Request $i: $response"
    sleep 0.1
done

echo ""
echo "Test completed!"
echo ""
echo "=========================================="
echo "Sliding Window Advantages Demonstrated:"
echo "1. Smooth rate limiting across time"
echo "2. Prevents burst attacks at boundaries"
echo "3. More accurate than Fixed Counter"
echo "4. Better user experience with predictable limits"
echo "=========================================="
