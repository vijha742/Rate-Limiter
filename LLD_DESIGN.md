# Rate-Limiter: Low-Level Design (LLD)

## 1. Detailed Component Architecture

### 1.1 Package Structure

```
com.vikas.rate_limiter/
├── RateLimiterApplication.java           # Spring Boot entry point
├── RateLimitManager.java                  # Core orchestrator
├── RateLimitConfigRepository.java         # MongoDB repository
├── RateLimitConfigEntity.java             # MongoDB entity
│
├── algorithm/                             # Rate limiting algorithms
│   ├── RateLimitAlgorithm.java           # Interface
│   ├── TokenBucketRateLimitAlgorithm.java
│   ├── LeakyBucketRateLimitAlgorithm.java
│   ├── FixedCounterRateLimitAlgorithm.java
│   └── SlidingWindowRateLimitAlgorithm.java
│
├── config/                                # Spring configurations
│   ├── ConfigurationStoreService.java    # Abstract config store
│   ├── RedisConfiguration.java           # Redis setup
│   ├── MongoConfiguration.java           # MongoDB setup
│   └── MetricsConfig.java                # Prometheus metrics
│
├── controllers/                           # REST endpoints
│   ├── TestController.java
│   ├── ConfigController.java
│   └── StatsController.java
│
├── dto/                                   # Data transfer objects
│   ├── RequestConfigDTO.java
│   ├── RateLimitDecision.java
│   └── ConfigResponse.java
│
├── errorHandler/                          # Exception handling
│   └── GlobalExceptionHandler.java
│
├── filter/                                # Request filters
│   └── RateFilter.java
│
├── service/                               # Business logic
│   ├── ConfigurationStoreService.java    # Interface
│   ├── MongoConfigurationStoreService.java
│   └── EndpointConfigService.java
│
└── utils/                                 # Utilities
    ├── IpUtil.java
    ├── TestClock.java
    └── RateLimiterProperties.java
```

---

## 2. Core Classes - Detailed Design

### 2.1 RateLimitManager

**Responsibilities:**
- Orchestrate rate limiting operations
- Cache algorithm instances per IP
- Query configuration from multiple sources
- Execute rate limiting decision

**Key Methods:**

```java
public class RateLimitManager {
    
    // Core method: Determines if request is allowed
    public RateLimitDecision allowRequest(String ip)
    
    // Algorithm management
    private RateLimitAlgorithm getOrCreateAlgorithm(String ip)
    private RateLimitAlgorithm getAlgoWithIp(String ip, RequestConfigDTO config)
    
    // Configuration management
    private RequestConfigDTO getConfigForIp(String ip)
    private RequestConfigDTO getEndpointConfig(String endpoint)
    
    // Cache management
    private void cacheAlgorithm(String ip, RateLimitAlgorithm algo)
    private RateLimitAlgorithm getCachedAlgorithm(String ip)
    
    // State management
    private Map<String, RateLimitAlgorithm> algorithmCache
    private Map<String, RequestConfigDTO> configCache
}
```

**Algorithm Selection Logic:**
```
Input: IP address, RequestConfigDTO
│
├─ Switch on algo type:
│
├─ CASE TOKEN_BUCKET:
│   └─ new TokenBucketRateLimitAlgorithm(
│        refillRate, capacity)
│
├─ CASE LEAKY_BUCKET:
│   └─ new LeakyBucketRateLimitAlgorithm(
│        flowRate, capacity)
│
├─ CASE FIXED_WINDOW:
│   └─ new FixedCounterRateLimitAlgorithm(
│        maxRequests, windowSize)
│
└─ CASE SLIDING_WINDOW:
    └─ new SlidingWindowRateLimitAlgorithm(
        maxRequests, windowTime)
```

---

### 2.2 RateFilter (Request Interceptor)

**Type:** `OncePerRequestFilter` (Spring Security)

**Execution Order:** Early in filter chain

**Key Methods:**

```java
public class RateFilter extends OncePerRequestFilter {
    
    // Main entry point
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain)
    
    // IP extraction
    private String extractClientIp(HttpServletRequest request)
    
    // Response handling
    private void setRateLimitHeaders(
        HttpServletResponse response,
        RateLimitDecision decision)
    
    private void returnTooManyRequests(
        HttpServletResponse response,
        RateLimitDecision decision)
}
```

**Request Processing Flow:**

```
1. doFilterInternal() called
   │
2. Extract IP using IpUtil
   │ Checks headers: X-Forwarded-For, X-Real-IP, X-Client-IP
   │
3. Call RateLimitManager.allowRequest(ip)
   │
4. Get RateLimitDecision
   │
5. Set Response Headers
   │ X-RateLimit-Limit: max_requests
   │ X-RateLimit-Remaining: requests_left
   │ X-RateLimit-Reset: reset_timestamp
   │
6. Decision Check
   ├─ if allowed: continue filter chain
   └─ if denied: send 429 response
```

---

### 2.3 Algorithm Interface & Implementations

**RateLimitAlgorithm Interface:**

```java
public interface RateLimitAlgorithm {
    
    // Main decision method (must be thread-safe)
    synchronized RateLimitDecision acceptRequest();
    
    // State query methods
    long getRemainingRequests();
    long getResetTime();
    
    // Configuration getters
    Map<String, Object> getConfig();
    String getAlgorithmName();
}
```

---

### 2.3.1 TokenBucketRateLimitAlgorithm

**State Variables:**
```java
private double tokens;              // Current tokens in bucket
private final double capacity;      // Max tokens
private final double refillRate;    // Tokens/second
private long lastRefillTime;        // Last refill timestamp
```

**Logic:**

```
acceptRequest():
  │
  ├─ Calculate time elapsed since last refill
  │
  ├─ Calculate tokens to add: elapsed_time * refillRate
  │
  ├─ Add tokens (capped at capacity)
  │
  ├─ Update lastRefillTime = now()
  │
  └─ Check if tokens >= 1:
      ├─ YES: Consume 1 token, return ALLOWED
      └─ NO: Return DENIED (retryAfter = 1/refillRate)
```

**Characteristics:**
- Time complexity: O(1)
- Space complexity: O(1)
- Thread-safe: Yes (synchronized)
- Distributed-safe: Yes (with Redis Lua script)

---

### 2.3.2 LeakyBucketRateLimitAlgorithm

**State Variables:**
```java
private Queue<Long> queue;          // Request timestamps
private final int capacity;         // Max queue size
private final double flowRate;      // Leak rate (requests/sec)
private long lastLeakTime;          // Last leak timestamp
```

**Logic:**

```
acceptRequest():
  │
  ├─ Calculate time elapsed since last leak
  │
  ├─ Calculate requests to leak: elapsed_time * flowRate
  │
  ├─ Remove leaked requests from queue
  │
  ├─ Update lastLeakTime = now()
  │
  └─ Check queue size < capacity:
      ├─ YES: Add request timestamp, return ALLOWED
      └─ NO: Return DENIED
```

**Characteristics:**
- Time complexity: O(leak_count) - amortized O(1)
- Space complexity: O(capacity)
- Thread-safe: Yes (synchronized)
- Distributed-safe: Requires distributed queue

---

### 2.3.3 FixedCounterRateLimitAlgorithm

**State Variables:**
```java
private int counter;                // Current request count
private final int maxRequests;      // Requests per window
private final long windowSize;      // Window duration (seconds)
private long windowStart;           // Window start time
```

**Logic:**

```
acceptRequest():
  │
  ├─ Get current time
  │
  ├─ Check if current window expired:
  │   └─ YES: Reset counter = 0, windowStart = now()
  │
  └─ Check if counter < maxRequests:
      ├─ YES: counter++, return ALLOWED
      └─ NO: Return DENIED (retryAfter = windowSize)
```

**Characteristics:**
- Time complexity: O(1)
- Space complexity: O(1)
- Thread-safe: Yes (synchronized)
- Drawback: Boundary burst (can allow 2x limit at boundaries)

---

### 2.3.4 SlidingWindowRateLimitAlgorithm

**State Variables:**
```java
private Deque<Long> timestamps;     // Request timestamps
private final int maxRequests;      // Max requests in window
private final long windowTime;      // Window duration (ms)
```

**Logic:**

```
acceptRequest():
  │
  ├─ Get current time
  │
  ├─ Remove expired timestamps (older than windowTime):
  │   └─ while (!queue.empty() && 
  │            now() - queue.peek() > windowTime):
  │       queue.poll()
  │
  ├─ Check queue size < maxRequests:
  │   ├─ YES: Add current timestamp, return ALLOWED
  │   └─ NO: Return DENIED
  │
  └─ retryAfter = oldest_timestamp + windowTime - now()
```

**Characteristics:**
- Time complexity: O(expired_count) - amortized O(1)
- Space complexity: O(maxRequests)
- Thread-safe: Yes (synchronized)
- Advantage: No boundary burst issues

---

### 2.4 RateLimitDecision DTO

```java
public class RateLimitDecision {
    private boolean allowed;              // Allowed or denied?
    private long remainingRequests;       // Requests left in window
    private long resetTimestamp;          // When limit resets
    private long retryAfter;              // Seconds to retry (if denied)
    private String algorithmUsed;         // Which algorithm made decision
    private Map<String, Object> metadata; // Additional info
}
```

---

### 2.5 Configuration Store Services

**Abstract Service:**
```java
public abstract class ConfigurationStoreService {
    
    public abstract RequestConfigDTO getConfig(String ip);
    
    public abstract void saveConfig(String ip, RequestConfigDTO config);
    
    public abstract void deleteConfig(String ip);
    
    public abstract List<RequestConfigDTO> getAllConfigs();
}
```

**MongoDB Implementation:**
```java
public class MongoConfigurationStoreService 
    extends ConfigurationStoreService {
    
    private RateLimitConfigRepository repository;
    
    @Override
    public RequestConfigDTO getConfig(String ip) {
        return repository
            .findByIp(ip)
            .map(this::toDTO)
            .orElse(null);
    }
    
    @Override
    public void saveConfig(String ip, RequestConfigDTO config) {
        RateLimitConfigEntity entity = toEntity(config);
        repository.save(entity);
    }
    
    @Override
    public void deleteConfig(String ip) {
        repository.deleteByIp(ip);
    }
}
```

**YAML/File-based Implementation:**
```java
public class EndpointConfigService 
    extends ConfigurationStoreService {
    
    private Map<String, RequestConfigDTO> endpointConfigs;
    
    @PostConstruct
    public void loadFromYaml() {
        // Load from application.yaml or custom file
        // Build map of endpoint → config
    }
    
    @Override
    public RequestConfigDTO getConfig(String endpoint) {
        return endpointConfigs.get(endpoint);
    }
}
```

---

### 2.6 IP Utility

```java
public class IpUtil {
    
    // Extract client IP, handling reverse proxies
    public static String extractClientIp(HttpServletRequest request) {
        
        // Priority order:
        // 1. X-Forwarded-For (proxy chain)
        // 2. X-Real-IP (Nginx)
        // 3. CF-Connecting-IP (Cloudflare)
        // 4. request.getRemoteAddr()
        
        // Validation:
        // - Remove port if present
        // - Validate IPv4/IPv6
        // - Return first IP if comma-separated
    }
    
    public static boolean isValidIp(String ip) {
        // IPv4 or IPv6 validation
    }
    
    public static String getMainIp(String ips) {
        // Handle comma-separated IP list
        // Return first valid IP
    }
}
```

---

### 2.7 Request/Response DTOs

**RequestConfigDTO:**
```java
@Data
public class RequestConfigDTO {
    private String ip;                      // Optional: defaults to request IP
    private Algorithm algo;                 // ENUM: Algorithm type
    private Map<String, Object> parameters; // Algorithm-specific params
    
    public enum Algorithm {
        TOKEN_BUCKET,
        LEAKY_BUCKET,
        FIXED_WINDOW,
        SLIDING_WINDOW
    }
}
```

**Configuration by Algorithm:**

| Algorithm | Required Parameters | Optional |
|-----------|------------------|----------|
| TOKEN_BUCKET | refillRate, capacity | - |
| LEAKY_BUCKET | flowRate, capacity | - |
| FIXED_WINDOW | maxRequests, windowSize | - |
| SLIDING_WINDOW | maxRequests, windowTime | - |

---

### 2.8 REST Controllers

**TestController:**
```java
@RestController
@RequestMapping("/api")
public class TestController {
    
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Hello Vikas Jha...!");
    }
}
```

**ConfigController:**
```java
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    
    @PostMapping
    public ResponseEntity<ConfigResponse> configure(
        @RequestBody RequestConfigDTO config,
        HttpServletRequest request) {
        // Validate config
        // Save to MongoDB
        // Clear algorithm cache for IP
        // Return success response
    }
    
    @GetMapping("/{ip}")
    public ResponseEntity<RequestConfigDTO> getConfig(
        @PathVariable String ip) {
        // Retrieve config from MongoDB
        // Return to client
    }
    
    @DeleteMapping("/{ip}")
    public ResponseEntity<Void> deleteConfig(
        @PathVariable String ip) {
        // Delete config from MongoDB
        // Clear cache
        // Return 204 No Content
    }
}
```

**StatsController:**
```java
@RestController
@RequestMapping("/api/stats")
public class StatsController {
    
    @GetMapping
    public ResponseEntity<StatsResponse> getStats() {
        // Aggregate metrics from Prometheus
        // Calculate request rates
        // Top IPs by request volume
        // Algorithm usage distribution
    }
}
```

---

### 2.9 Exception Handling

**Global Exception Handler:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidConfigException.class)
    public ResponseEntity<ErrorResponse> handleInvalidConfig(
        InvalidConfigException e) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(
                "INVALID_CONFIG",
                e.getMessage()));
    }
    
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
        ConfigNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(
                "CONFIG_NOT_FOUND",
                e.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
        Exception e) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred"));
    }
}
```

---

## 3. Data Model

### MongoDB Schema

**RateLimitConfigEntity:**
```java
@Document(collection = "rate_limit_configs")
@Data
public class RateLimitConfigEntity {
    
    @Id
    private String id;                      // MongoDB ID
    
    @Indexed(unique = true)
    private String ip;                      // IP address
    
    private String algo;                    // Algorithm type
    
    private Map<String, Object> parameters; // Algorithm params
    
    private Long createdAt;                 // Creation timestamp
    
    private Long updatedAt;                 // Update timestamp
    
    private String createdBy;               // User/Admin who created
    
    @Version
    private Long version;                   // Optimistic locking
}
```

**MongoDB Indexes:**
```
- ip (UNIQUE)
- createdAt (TTL: 30 days)
- algo
- updatedAt (DESCENDING)
```

---

## 4. Thread Safety & Concurrency

### Synchronization Strategy

**Algorithm Methods (Synchronized):**
```java
public synchronized RateLimitDecision acceptRequest() {
    // All state access/modification is synchronized
    // Ensures only one thread modifies state at a time
}
```

**Why Synchronized?**
- Multiple requests can arrive concurrently for same IP
- Algorithm state must be consistent
- Counter/timestamp modifications must be atomic

**Performance Impact:**
- Lock contention only for same IP
- Different IPs don't block each other
- Latency: < 1 microsecond (with lock)

---

### Distributed Synchronization (Redis)

**Problem:** Synchronized works only within single JVM

**Solution:** Redis Lua Scripts for distributed atomicity

**Example - Token Bucket Lua:**
```lua
-- Atomic token bucket operation
local key = KEYS[1]
local now = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])

local current = redis.call('HGETALL', key)
-- ... token calculation ...
-- ... consistency checks ...
redis.call('HSET', key, 'tokens', new_tokens)
return {allowed, remaining, resetTime}
```

---

## 5. Memory Management

### Cache Eviction Strategy

**Algorithm Cache:**
```java
private Map<String, RateLimitAlgorithm> algorithmCache
    = Collections.synchronizedMap(
        new LinkedHashMap<String, RateLimitAlgorithm>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_CACHED_IPS; // Default: 10,000
            }
        });
```

**Strategy:** LRU (Least Recently Used)
- Remove least-used IPs when cache full
- Prevents unbounded memory growth
- Typical scenario: 10,000+ unique IPs/day

**Configuration Cache:**
```java
private Map<String, RequestConfigDTO> configCache
    = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(this::loadConfigFromDb);
```

---

## 6. Error Handling & Recovery

### Exception Hierarchy

```
Exception
├── RuntimeException
│   ├── InvalidConfigException
│   │   └── InvalidAlgorithmException
│   │   └── InvalidParametersException
│   ├── ConfigNotFoundException
│   ├── RateLimitException
│   │   └── TooManyRequestsException
│   └── ExternalServiceException
│       ├── RedisException
│       └── MongoDBException
```

### Recovery Mechanisms

**MongoDB Failure:**
```
Try: Query MongoDB for config
└─ Catch: Try YAML/Properties cache
    └─ If miss: Use default config
        └─ Create default algorithm
```

**Redis Failure (Distributed Mode):**
```
Try: Execute Lua script on Redis
└─ Catch: Fall back to in-memory algorithm
    └─ Log incident
    └─ Alert operations
```

---

## 7. Performance Optimizations

### 1. Algorithm Caching
- Per-IP algorithm instances cached
- Eliminates repeated instantiation
- Typical hit ratio: 95%+

### 2. Configuration Caching
- 1-hour TTL on config lookups
- Reduces MongoDB queries by 99%
- Configurable refresh interval

### 3. IP Validation Caching
- Cache validated IP results
- Avoid repeated regex matching
- Negligible memory overhead

### 4. Lazy Initialization
- Algorithm instances created on first request
- Configuration loaded on demand
- Reduces startup time

---

## 8. Testing Strategy

### Unit Tests

```java
public class TokenBucketAlgorithmTest {
    
    @Test
    public void testTokenRefill() {
        // Verify tokens refill at correct rate
    }
    
    @Test
    public void testCapacityLimit() {
        // Verify tokens don't exceed capacity
    }
    
    @Test
    public void testConcurrentRequests() {
        // Stress test with 1000 concurrent threads
    }
}
```

### Integration Tests

```java
public class RateLimiterIntegrationTest {
    
    @Test
    public void testEndToEndRateLimiting() {
        // Configure limit
        // Make requests
        // Verify 429 response when exceeded
    }
    
    @Test
    public void testAlgorithmSwitching() {
        // Switch algorithms dynamically
        // Verify new algorithm applied
    }
}
```

### Load Tests

```bash
# k6 script for load testing
for i in {1..10000}; do
    curl http://localhost:8080/api/test &
done
wait

# Verify correct percentage rejected (429)
```

---

## Summary

The LLD provides detailed specification for:
- Each component's responsibilities
- Data structures and state management
- Algorithm implementations
- API contracts
- Exception handling
- Performance optimizations
- Thread safety mechanisms
- Testing strategies

This level of detail enables developers to implement the system with minimal ambiguity.
