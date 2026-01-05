#!/bin/bash

# Script to test the Token Bucket Rate Limiting Algorithm
# This algorithm allows burst traffic while maintaining an average rate limit
# Tokens are added at a constant rate, and requests consume tokens

echo "=========================================="
echo "Token Bucket Algorithm Test"
echo "=========================================="
echo ""

# Configuration
BASE_URL="http://localhost:8080/api/test"
BUCKET_CAPACITY=10
REFILL_RATE=2  # tokens per second

echo "Configuration:"
echo "  - Bucket Capacity: $BUCKET_CAPACITY tokens"
echo "  - Refill Rate: $REFILL_RATE tokens/second"
echo "  - Test Endpoint: $BASE_URL"
echo ""

# Test 1: Burst allowance test
echo "Test 1: Testing burst traffic allowance"
echo "Expected: Full bucket allows burst up to capacity, then throttles"
echo "---"

echo "Waiting for bucket to fill completely..."
sleep 6
echo ""

echo "Sending burst of requests (bucket should be full)..."
burst_success=0
burst_fail=0

for i in $(seq 1 15); do
    start_time=$(date +%s.%N)
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    end_time=$(date +%s.%N)
    
    if [ "$response" == "200" ]; then
        echo "Burst Request $i: SUCCESS (200) - Token consumed"
        ((burst_success++))
    else
        echo "Burst Request $i: RATE LIMITED ($response) - No tokens available"
        ((burst_fail++))
    fi
    sleep 0.05
done

echo ""
echo "Burst Results: $burst_success successful, $burst_fail rate-limited"
echo "Expected: ~$BUCKET_CAPACITY successful (initial bucket capacity)"
echo ""

# Test 2: Token refill verification
echo "Test 2: Verifying token refill behavior"
echo "Expected: Tokens refill at $REFILL_RATE tokens/second"
echo "---"

echo "Waiting for some tokens to refill (3 seconds)..."
sleep 3
echo "Expected new tokens: ~$(($REFILL_RATE * 3))"
echo ""

echo "Sending requests to consume refilled tokens..."
refill_success=0

for i in $(seq 1 8); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Request $i: SUCCESS (Token available)"
        ((refill_success++))
    else
        echo "Request $i: RATE LIMITED (No tokens)"
    fi
    sleep 0.1
done

echo ""
echo "Refill Test: $refill_success requests succeeded"
echo ""

# Test 3: Sustained rate test
echo "Test 3: Testing sustained request rate"
echo "Expected: Sustainable rate limited by refill rate"
echo "---"

echo "Waiting for bucket reset..."
sleep 6
echo ""

echo "Sending requests at steady pace..."
sustained_success=0
sustained_fail=0

for i in $(seq 1 20); do
    timestamp=$(date +%H:%M:%S.%3N)
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    
    if [ "$response" == "200" ]; then
        echo "[$timestamp] Request $i: SUCCESS"
        ((sustained_success++))
    else
        echo "[$timestamp] Request $i: RATE LIMITED"
        ((sustained_fail++))
    fi
    sleep 0.4  # Slightly faster than refill rate
done

echo ""
echo "Sustained Results: $sustained_success successful, $sustained_fail rate-limited"
echo ""

# Test 4: Burst after idle period
echo "Test 4: Testing burst capacity after idle period"
echo "Expected: Bucket refills during idle time, allowing another burst"
echo "---"

echo "Idle period - letting bucket refill (5 seconds)..."
sleep 5
echo ""

echo "Sending second burst..."
second_burst_success=0

for i in $(seq 1 12); do
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    if [ "$response" == "200" ]; then
        echo "Second Burst Request $i: SUCCESS"
        ((second_burst_success++))
    else
        echo "Second Burst Request $i: RATE LIMITED"
    fi
    sleep 0.05
done

echo ""
echo "Second Burst Results: $second_burst_success successful"
echo ""

# Test 5: Empty bucket recovery
echo "Test 5: Testing recovery from empty bucket"
echo "---"

echo "Depleting bucket completely..."
for i in $(seq 1 15); do
    curl -s -o /dev/null $BASE_URL
    sleep 0.01
done

echo "Bucket depleted. Monitoring recovery..."
echo ""

for attempt in $(seq 1 5); do
    sleep 1
    response=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL)
    timestamp=$(date +%H:%M:%S)
    echo "[$timestamp] Recovery attempt $attempt: $response"
done

echo ""
echo "Test completed!"
echo ""
echo "=========================================="
echo "Token Bucket Advantages Demonstrated:"
echo "1. Allows controlled burst traffic"
echo "2. Maintains average rate limit over time"
echo "3. Flexible - handles varying traffic patterns"
echo "4. Smooth recovery after bursts"
echo "5. Better user experience than strict limiting"
echo "=========================================="
