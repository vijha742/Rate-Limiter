# Rate-Limiter: High-Level Design (HLD)

## 1. System Overview

The Rate-Limiter is a **distributed, multi-algorithm rate limiting service** built with Spring Boot. It provides flexible request throttling with IP-based tracking and supports four distinct rate-limiting algorithms. The system is designed for high-concurrency scenarios with Redis as the distributed state store and MongoDB for persistent configuration management.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT REQUESTS                               │
│                      (HTTP / External Services)                         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      SPRING BOOT APPLICATION                           │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │                 REQUEST LAYER (Filter)                         │   │
│  │  ┌────────────────────────────────────────────────────────┐   │   │
│  │  │  RateFilter (OncePerRequestFilter)                    │   │   │
│  │  │  • Intercepts all incoming HTTP requests              │   │   │
│  │  │  • Extracts client IP (handles reverse proxies)       │   │   │
│  │  │  • Delegates to RateLimitManager                      │   │   │
│  │  │  • Sets response headers (X-RateLimit-*)              │   │   │
│  │  │  • Returns 429 if rate limit exceeded                 │   │   │
│  │  └────────────────────────────────────────────────────────┘   │   │
│  └────────────────────────────────────────────────────────────────┘   │
│             │                                                          │
│             ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │            ORCHESTRATION LAYER (Manager)                       │   │
│  │  ┌────────────────────────────────────────────────────────┐   │   │
│  │  │  RateLimitManager                                      │   │   │
│  │  │  • Retrieves/Creates algorithm for IP                 │   │   │
│  │  │  • Queries configuration stores                       │   │   │
│  │  │  • Selects appropriate algorithm (Strategy Pattern)   │   │   │
│  │  │  • Executes rate limiting decision                    │   │   │
│  │  │  • Handles algorithm caching & lifecycle              │   │   │
│  │  └────────────────────────────────────────────────────────┘   │   │
│  └────────────────────────────────────────────────────────────────┘   │
│      │              │                    │                 │           │
│      ▼              ▼                    ▼                 ▼           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────┐   │
│  │  CONFIG      │  │   ALGORITHM  │  │   ENDPOINTS  │  │ HEALTH │   │
│  │ LAYER        │  │   LAYER      │  │   LAYER      │  │ CHECK  │   │
│  │              │  │              │  │              │  │        │   │
│  │ConfigSvc     │  │ TokenBucket  │  │TestController│  │ /act   │   │
│  │ | MongoSvc   │  │ LeakyBucket  │  │ConfigCtl     │  │ uator  │   │
│  │ | YamlSvc    │  │ FixedWindow  │  │StatsCtl      │  │        │   │
│  │              │  │ SlidingWin   │  │              │  │        │   │
│  └──────────────┘  └──────────────┘  └──────────────┘  └────────┘   │
│                                                                        │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │            UTILITY & INFRASTRUCTURE LAYER                      │   │
│  │  ┌────────────────────────────────────────────────────────┐   │   │
│  │  │  • IpUtil: IP extraction & validation                 │   │   │
│  │  │  • MetricsConfig: Prometheus integration              │   │   │
│  │  │  • RedisConfiguration: Distributed cache setup        │   │   │
│  │  │  • MongoConfiguration: Persistent store setup         │   │   │
│  │  │  • Error Handlers: Custom exception handling          │   │   │
│  │  └────────────────────────────────────────────────────────┘   │   │
│  └────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
             │                    │                     │
             ▼                    ▼                     ▼
    ┌─────────────────┐ ┌─────────────────┐ ┌──────────────────┐
    │      REDIS      │ │     MONGODB     │ │  PROMETHEUS/    │
    │                 │ │                 │ │  GRAFANA        │
    │  • Rate state   │ │ • Configurations│ │                  │
    │  • Algorithms   │ │ • User configs  │ │ • Metrics        │
    │  • Cache        │ │ • Audit logs    │ │ • Dashboards     │
    └─────────────────┘ └─────────────────┘ └──────────────────┘
```

---

## 3. Core Components

### 3.1 Request Interception Layer

**RateFilter** (Servlet Filter)
- Entry point for all HTTP requests
- IP extraction (handles X-Forwarded-For, X-Real-IP, etc.)
- Delegates to RateLimitManager
- Sets response headers on both allowed and rejected requests
- Returns HTTP 429 for rate-limited requests
- Integration point for metrics collection

### 3.2 Orchestration Layer

**RateLimitManager**
- Central coordinator for rate limiting operations
- Configuration retrieval from multiple sources:
  - MongoDB (user-specific configs)
  - YAML files (endpoint-specific configs)
  - Defaults (fallback configs)
- Algorithm selection using Strategy pattern
- Algorithm instance caching per IP
- Execution of rate limiting decision
- Lifecycle management of algorithm instances

### 3.3 Configuration Layer

**ConfigurationStoreService** (Abstract/Interface)
- Abstraction for configuration storage
- Multiple implementations:
  - **MongoConfigurationStoreService**: Persistent MongoDB storage
  - **YamlEndpointConfigService**: File-based endpoint configs
  - **InMemoryConfigurationStoreService**: Cache layer

### 3.4 Algorithm Layer

**RateLimitAlgorithm** (Interface)
- Contract for all rate limiting algorithms
- Thread-safe implementations:
  - **TokenBucketRateLimitAlgorithm**: Industry standard
  - **LeakyBucketRateLimitAlgorithm**: Constant rate processing
  - **FixedCounterRateLimitAlgorithm**: Simple window-based
  - **SlidingWindowRateLimitAlgorithm**: Accurate without boundaries

### 3.5 Controller/Endpoint Layer

**REST Controllers**
- **TestController**: Basic test endpoint with rate limiting
- **ConfigController**: Dynamic configuration endpoint
- **StatsController**: Metrics and statistics
- **ActuatorController**: Health checks and diagnostics

### 3.6 Infrastructure Layer

**Supporting Services**
- **IpUtil**: IP extraction with reverse proxy handling
- **MetricsConfig**: Prometheus metrics integration
- **RedisConfiguration**: Redis client setup
- **MongoConfiguration**: MongoDB connection setup
- **ErrorHandler**: Global exception handling

---

## 4. Data Flow

### Request Processing Flow

```
1. HTTP Request arrives
      │
      ▼
2. RateFilter intercepts
      │
      ▼
3. Extract client IP (IpUtil)
      │
      ▼
4. Call RateLimitManager.allowRequest(ip)
      │
      ├─→ Check if algorithm instance cached for IP
      │
      ├─→ If cached: Skip to step 6
      │
      └─→ If not cached: Continue to step 5
      
5. Query configuration (MongoDB → YAML → Default)
      │
      ├─→ Create appropriate algorithm instance
      │
      └─→ Cache instance for future requests
      
6. Execute algorithm.acceptRequest()
      │
      ├─→ Check current request quota
      │
      └─→ Update internal state
      
7. Return RateLimitDecision
      │
      ├─→ allowed = true: Proceed to endpoint
      │
      └─→ allowed = false: Return HTTP 429
      
8. Set response headers
      │
      ├─→ X-RateLimit-Limit
      │
      ├─→ X-RateLimit-Remaining
      │
      └─→ X-RateLimit-Reset
      
9. Send response to client
```

---

## 5. Rate Limiting Algorithms Overview

### 5.1 Token Bucket
- **Best For**: General-purpose APIs with burst traffic
- **How It Works**: 
  - Tokens refill at a constant rate up to max capacity
  - Each request consumes one token
  - Request rejected if bucket empty
- **Characteristics**: Allows bursts, fair overall rate

### 5.2 Leaky Bucket
- **Best For**: Protecting backend from overload
- **How It Works**:
  - Requests queue up (leak out bottom at constant rate)
  - Queue has maximum size
  - Requests dropped if queue full
- **Characteristics**: Constant output rate, smooths traffic

### 5.3 Fixed Counter
- **Best For**: Simple, low-memory scenarios
- **How It Works**:
  - Counter for fixed time window (e.g., 60 seconds)
  - Counter resets at window boundary
  - Request rejected if counter exceeded
- **Characteristics**: Simple, but boundary burst possible

### 5.4 Sliding Window
- **Best For**: Accurate rate limiting without boundary issues
- **How It Works**:
  - Maintains rolling window of request timestamps
  - Drops old timestamps outside window
  - Checks queue size for new requests
- **Characteristics**: Accurate, memory intensive

---

## 6. Configuration Management

### Configuration Sources (Priority Order)

1. **MongoDB** (User/IP-specific)
   - Runtime configurations
   - Can be updated via API
   - Persists across restarts

2. **YAML/Properties** (Endpoint-specific)
   - Configuration per endpoint
   - Requires restart to change
   - File-based management

3. **Defaults** (Fallback)
   - Hard-coded defaults
   - Applied when no specific config found
   - Example: 5 requests per 10 seconds

### Configuration Structure

```json
{
  "ip": "192.168.1.1",
  "algo": "TOKEN_BUCKET | LEAKY_BUCKET | FIXED_WINDOW | SLIDING_WINDOW",
  "parameters": {
    "refillRate": 10,      // TOKEN_BUCKET
    "capacity": 20,        // TOKEN_BUCKET / LEAKY_BUCKET
    "flowRate": 5,         // LEAKY_BUCKET
    "maxRequests": 100,    // FIXED_WINDOW / SLIDING_WINDOW
    "windowSize": 60,      // FIXED_WINDOW (seconds)
    "windowTime": 60000    // SLIDING_WINDOW (milliseconds)
  }
}
```

---

## 7. Distributed System Considerations

### Redis Integration
- **State Management**: Algorithm state stored in Redis
- **Atomic Operations**: Lua scripts ensure atomicity
- **Distributed Locks**: Prevent race conditions
- **Cache**: Fast retrieval of algorithm instances

### MongoDB Integration
- **Persistent Storage**: Configuration survives restarts
- **User Management**: Per-user/IP limits
- **Audit Trail**: Historical configuration changes
- **Scalability**: Sharding support for large deployments

### Multi-Instance Coordination
- Shared Redis state ensures consistency
- No data loss on instance failure
- Configuration changes propagate automatically
- Metrics aggregated from all instances

---

## 8. Response Headers

All responses include rate limit information:

```
X-RateLimit-Limit: 100        (Max requests in window)
X-RateLimit-Remaining: 42     (Requests left in window)
X-RateLimit-Reset: 1234567890 (Unix timestamp of reset)
```

When rate limit exceeded:
```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1234567890
Content-Type: application/json

{
  "error": "Rate limit exceeded",
  "message": "All requests have been exhausted. Try again later!!!",
  "retryAfter": 45
}
```

---

## 9. Monitoring & Observability

### Metrics (Prometheus)
- Request count (labeled by IP, algorithm, status)
- Rate limit violations
- Algorithm latency
- Cache hit/miss rates
- Configuration updates

### Logging
- Request interception (IPs, decisions)
- Configuration changes
- Algorithm state changes
- Error conditions

### Health Checks
- Redis connectivity
- MongoDB connectivity
- Configuration service availability
- Algorithm performance

### Grafana Dashboards
- Real-time request rates
- Top IPs by request volume
- Algorithm effectiveness
- Error rate trends

---

## 10. Security Considerations

### IP Detection & Validation
- Support for reverse proxies (X-Forwarded-For)
- IP spoofing protection
- IPv4 and IPv6 support
- Graceful handling of invalid IPs

### Configuration Security
- Input validation on all parameters
- Type checking for algorithm parameters
- Range validation (no negative values)
- Protected configuration endpoints

### Distributed Security
- Redis authentication (if configured)
- MongoDB authentication
- SSL/TLS support for connections
- Request signing (future enhancement)

---

## 11. Scalability & Performance

### In-Memory Caching
- Algorithm instances cached per IP
- Configuration cached with TTL
- Memory-efficient data structures
- Automatic cache eviction

### Horizontal Scaling
- Stateless application design
- Shared Redis state
- MongoDB as distributed store
- No sticky sessions required

### Performance Targets
- Latency: < 1ms per request (including decision)
- Throughput: > 10,000 requests/second per instance
- Redis operations: Sub-millisecond
- Configuration reload: Zero-downtime

---

## 12. Failure Handling & Resilience

### Graceful Degradation
- MongoDB unavailable → Use in-memory + YAML configs
- Redis unavailable → Fall back to in-memory state
- Configuration service failure → Use cached config

### Circuit Breaker Pattern
- Detect repeated failures to external services
- Fail fast instead of cascading delays
- Automatic recovery with exponential backoff

### Error Recovery
- Automatic retry with exponential backoff
- Fallback to safe defaults
- Detailed error logging
- Alerting on critical failures

---

## 13. Extensibility

### Adding New Algorithms
1. Implement `RateLimitAlgorithm` interface
2. Add enum to `Algorithm` enum class
3. Register in RateLimitManager factory
4. Add tests and documentation

### Custom Configuration Stores
1. Extend `ConfigurationStoreService`
2. Implement storage logic
3. Register in Spring context
4. Configure priority order

### Custom Metrics
1. Use Micrometer API
2. Register custom meters
3. Tag with relevant dimensions
4. Export to Prometheus

---

## Summary

The Rate-Limiter is designed as a **modular, extensible, and production-ready system** that:
- Supports multiple rate-limiting algorithms
- Scales horizontally with Redis/MongoDB
- Provides comprehensive observability
- Handles edge cases and failures gracefully
- Maintains thread safety at high concurrency
- Allows runtime configuration without restarts

This HLD serves as the blueprint for implementation and future enhancements.
