#!/bin/bash

# Master test script for all Rate Limiting Algorithms
# This script provides a menu to run individual or all algorithm tests

echo "=========================================="
echo "Rate Limiter Algorithm Test Suite"
echo "=========================================="
echo ""
echo "Available Tests:"
echo "  1. Fixed Counter Algorithm"
echo "  2. Sliding Window Algorithm"
echo "  3. Leaky Bucket Algorithm"
echo "  4. Token Bucket Algorithm"
echo "  5. Run All Tests"
echo "  6. Exit"
echo ""
echo -n "Select test to run (1-6): "
read choice

case $choice in
    1)
        echo ""
        echo "Running Fixed Counter Test..."
        echo ""
        ./FixedCounterTest.sh
        ;;
    2)
        echo ""
        echo "Running Sliding Window Test..."
        echo ""
        ./SlidingWindowTest.sh
        ;;
    3)
        echo ""
        echo "Running Leaky Bucket Test..."
        echo ""
        ./LeakyBucketTest.sh
        ;;
    4)
        echo ""
        echo "Running Token Bucket Test..."
        echo ""
        ./TokenBucketTest.sh
        ;;
    5)
        echo ""
        echo "Running All Tests (This will take several minutes)..."
        echo ""
        
        echo "╔═══════════════════════════════════════╗"
        echo "║  TEST 1: Fixed Counter Algorithm     ║"
        echo "╚═══════════════════════════════════════╝"
        ./FixedCounterTest.sh
        
        echo ""
        echo "Press Enter to continue to next test..."
        read
        
        echo "╔═══════════════════════════════════════╗"
        echo "║  TEST 2: Sliding Window Algorithm    ║"
        echo "╚═══════════════════════════════════════╝"
        ./SlidingWindowTest.sh
        
        echo ""
        echo "Press Enter to continue to next test..."
        read
        
        echo "╔═══════════════════════════════════════╗"
        echo "║  TEST 3: Leaky Bucket Algorithm      ║"
        echo "╚═══════════════════════════════════════╝"
        ./LeakyBucketTest.sh
        
        echo ""
        echo "Press Enter to continue to next test..."
        read
        
        echo "╔═══════════════════════════════════════╗"
        echo "║  TEST 4: Token Bucket Algorithm      ║"
        echo "╚═══════════════════════════════════════╝"
        ./TokenBucketTest.sh
        
        echo ""
        echo "=========================================="
        echo "All Tests Completed!"
        echo "=========================================="
        ;;
    6)
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo "Invalid choice. Please run the script again."
        exit 1
        ;;
esac

echo ""
echo "Test execution completed!"
