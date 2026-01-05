> [!warning] AI
> Created using AI...

# Rate Limiter Testing Scripts

This directory contains comprehensive test scripts for various rate limiting algorithms implemented in the rate-limiter application.

## Test Scripts

### 1. FixedCounterTest.sh
Tests the **Fixed Counter Algorithm** which maintains a counter that resets at fixed time intervals.

**Key Tests:**
- Normal behavior within a single window
- Edge case demonstration: burst traffic at window boundaries
- Shows the "double rate" problem where 2x limit can be achieved across window boundaries

**Trade-offs Demonstrated:**
- ✅ Simple and memory efficient
- ❌ Allows burst at window boundaries
- ❌ Uneven distribution across time

### 2. SlidingWindowTest.sh
Tests the **Sliding Window Algorithm** which uses a rolling time window for more accurate rate limiting.

**Key Tests:**
- Uniform request distribution
- Burst prevention at window boundaries
- Sliding behavior verification

**Advantages Demonstrated:**
- ✅ Smooth rate limiting across time
- ✅ Prevents burst attacks at boundaries
- ✅ More accurate than Fixed Counter
- ✅ Better user experience

### 3. LeakyBucketTest.sh
Tests the **Leaky Bucket Algorithm** which processes requests at a constant rate, queuing excess requests.

**Key Tests:**
- Burst handling with queue
- Constant leak rate verification
- Bucket overflow test
- Steady-state behavior

**Characteristics Demonstrated:**
- ✅ Smooths out burst traffic
- ✅ Processes requests at constant rate
- ✅ Provides bounded queue
- ✅ Prevents downstream overload
- ✅ Predictable behavior

### 4. TokenBucketTest.sh
Tests the **Token Bucket Algorithm** which allows controlled bursts while maintaining average rate.

**Key Tests:**
- Burst allowance test
- Token refill verification
- Sustained rate test
- Burst after idle period
- Empty bucket recovery

**Advantages Demonstrated:**
- ✅ Allows controlled burst traffic
- ✅ Maintains average rate over time
- ✅ Handles varying traffic patterns
- ✅ Smooth recovery after bursts
- ✅ Flexible and user-friendly

### 5. RunAllTests.sh
Master script that provides an interactive menu to run individual tests or all tests sequentially.

## Prerequisites

1. **Rate Limiter Application Running**
   ```bash
   # From the project root
   mvn spring-boot:run
   ```
   The application should be running on `http://localhost:8080`

2. **Required Tools**
   - `curl` - for making HTTP requests
   - `bc` - for floating-point calculations (used in some tests)

## Usage

### Make Scripts Executable
```bash
chmod +x *.sh
```

### Run Individual Tests
```bash
# Test Fixed Counter Algorithm
./FixedCounterTest.sh

# Test Sliding Window Algorithm
./SlidingWindowTest.sh

# Test Leaky Bucket Algorithm
./LeakyBucketTest.sh

# Test Token Bucket Algorithm
./TokenBucketTest.sh
```

### Run All Tests with Interactive Menu
```bash
./RunAllTests.sh
```

## Configuration

Each test script has configurable parameters at the top:

```bash
BASE_URL="http://localhost:8080/api/test"  # API endpoint
MAX_REQUESTS=10                             # Rate limit
WINDOW_LENGTH=5                             # Time window in seconds
```

Modify these values to match your rate limiter configuration.

## Understanding the Output

Each test script provides:
- **Test description**: What is being tested
- **Expected behavior**: What should happen
- **Real-time results**: Success/failure of each request
- **Summary statistics**: Success and failure counts
- **Trade-offs section**: Key characteristics of the algorithm

## Algorithm Comparison

| Algorithm | Burst Handling | Memory Usage | Accuracy | Complexity |
|-----------|---------------|--------------|----------|------------|
| Fixed Counter | Poor (allows 2x at boundaries) | Low | Low | Simple |
| Sliding Window | Good | Medium | High | Medium |
| Leaky Bucket | Excellent (queues) | Medium | High | Medium |
| Token Bucket | Good (controlled) | Low | High | Simple |

## Troubleshooting

**Connection Refused:**
- Ensure the rate-limiter application is running
- Verify the port in BASE_URL matches your application

**Unexpected Results:**
- Check rate limiter configuration in the application
- Verify algorithm implementation matches test assumptions
- Ensure no other processes are consuming the rate limit

**Script Errors:**
- Install `bc` if missing: `sudo apt-get install bc` (Linux) or `brew install bc` (Mac)
- Ensure scripts have execute permissions

## Notes

- Tests may take several minutes to complete (especially RunAllTests.sh)
- Tests include strategic delays to demonstrate time-based behaviors
- Results may vary slightly based on system load and network latency
- Each algorithm test is independent and can be run separately
