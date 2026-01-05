#!/bin/bash

# Script to test the Leaky Bucket Rate Limiting Algorithm
# This algorithm processes requests at a constant rate, queuing excess requests
# and "leaking" them out at a steady pace, ensuring smooth traffic flow

echo "=========================================="
echo "Leaky Bucket Algorithm Test"
echo "=========================================="
echo ""

# Configuration
BASE_URL="http://localhost:8080/api/test"
BUCKET_CAPACITY=10
LEAK_RATE=2  # requests per second

echo "Configuration:"
echo "  - Bucket Capacity: $BUCKET_CAPACITY requests"
echo "  - Leak Rate: $LEAK_RATE requests/second"
echo "  - Test Endpoint: $BASE_URL"
echo ""

# Test 1: Burst handling with queue
echo "Test 1: Testing burst handling and queuing behavior"
echo "Expected: Excess requests queued and processed at constant rate"
echo "---"
success_count=0
fail_count=0

echo "Sending burst of 15 requests rapidly..."
for i in $(seq 1 15); do
    start_time=$(date +%s.%N)
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    
    if [ "$response" == "200" ]; then
        printf "Request %2d: SUCCESS (200) - Response time: %.3fs\n" $i $duration
        ((success_count++))
    else
        printf "Request %2d: RATE LIMITED (%s) - Response time: %.3fs\n" $i $response $duration
        ((fail_count++))
    fi
done

echo ""
echo "Results: $success_count successful, $fail_count rate-limited/queued"
echo ""

# Test 2: Constant rate verification
echo "Test 2: Verifying constant leak rate"
echo "Expected: Requests processed at steady $LEAK_RATE req/sec rate"
echo "---"

echo "Waiting for bucket to drain..."
sleep 6
echo ""

echo "Sending requests at varied intervals..."
for i in $(seq 1 8); do
    timestamp=$(date +%H:%M:%S.%3N)
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "[$timestamp] Request $i: $response"
    
    # Vary the send rate
    if [ $i -lt 4 ]; then
        sleep 0.2  # Fast initially
    else
        sleep 0.8  # Slower later
    fi
done

echo ""

# Test 3: Bucket overflow test
echo "Test 3: Testing bucket capacity overflow"
echo "Expected: Requests exceeding bucket capacity are rejected"
echo "---"

echo "Waiting for bucket to drain..."
sleep 6
echo ""

echo "Sending rapid burst exceeding bucket capacity..."
overflow_success=0
overflow_fail=0

for i in $(seq 1 20); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Request $i: ACCEPTED"
        ((overflow_success++))
    else
        echo "Request $i: REJECTED (Bucket full)"
        ((overflow_fail++))
    fi
    sleep 0.01
done

echo ""
echo "Overflow Test Results: $overflow_success accepted, $overflow_fail rejected"
echo ""

# Test 4: Steady state behavior
echo "Test 4: Testing steady-state behavior"
echo "Expected: Sustainable rate matches leak rate"
echo "---"

echo "Waiting for bucket to stabilize..."
sleep 6
echo ""

echo "Sending requests at sustainable rate..."
for i in $(seq 1 10); do
    timestamp=$(date +%H:%M:%S)
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    echo "[$timestamp] Steady Request $i: $response"
    sleep 0.5  # Slightly less than 1/leak_rate
done

echo ""
echo "Test completed!"
echo ""
echo "=========================================="
echo "Leaky Bucket Characteristics Demonstrated:"
echo "1. Smooths out burst traffic"
echo "2. Processes requests at constant rate"
echo "3. Provides bounded queue for excess requests"
echo "4. Prevents downstream system overload"
echo "5. Predictable and consistent behavior"
echo "=========================================="
