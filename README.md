# Rate-Limiter in Java

A configurable, production-ready rate-limiter implementation in Spring Boot that supports multiple rate-limiting algorithms with IP-based request throttling. This project demonstrates different approaches to rate limiting with extensible architecture for custom algorithms.

## 📋 Table of Contents
- [Features](#features)
- [Supported Algorithms](#supported-algorithms)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Future Scope](#future-scope)
- [FAQ](#faq)

## ✨ Features

- **Multiple Rate Limiting Algorithms**: Support for 4 different algorithms (Fixed Window, Sliding Window, Leaky Bucket, Token Bucket)
- **IP-Based Rate Limiting**: Automatic IP detection and per-IP rate limiting
- **Dynamic Configuration**: Runtime configuration via REST API endpoints
- **Extensible Design**: Easy to add new rate limiting algorithms through interface-based design
- **Redis Integration**: Ready for distributed rate limiting with Redis support
- **Production Ready**: Built with Spring Boot 4.0.1 and Java 21
- **Comprehensive Testing**: Includes shell scripts for testing each algorithm's behavior
- **Graceful Degradation**: Returns HTTP 429 (Too Many Requests) when rate limit is exceeded

## 🔧 Supported Algorithms

### 1. Fixed Window Counter
A simple counter that resets at fixed time intervals.

**Pros:**
- ✅ Memory efficient
- ✅ Easy to implement
- ✅ Simple to understand

**Cons:**
- ❌ Allows burst traffic at window boundaries
- ❌ Can permit up to 2x the rate limit across window boundaries

**Use Case:** Low traffic APIs where precision isn't critical

**Configuration Parameters:**
- `Max Requests`: Maximum number of requests allowed in the window
- `Window Length`: Time window duration in seconds

### 2. Sliding Window Log
Uses a rolling time window for accurate rate limiting across time boundaries.

**Pros:**
- ✅ Smooth rate limiting across time
- ✅ Prevents burst attacks at boundaries
- ✅ More accurate than Fixed Window
- ✅ Better user experience

**Cons:**
- ❌ More memory intensive (stores request timestamps)
- ❌ Slightly more complex

**Use Case:** APIs requiring accurate rate limiting without boundary issues

**Configuration Parameters:**
- `Max Requests`: Maximum number of requests in the sliding window
- `Window Length`: Window duration in seconds

### 3. Leaky Bucket
Processes requests at a constant rate, queuing excess requests.

**Pros:**
- ✅ Smooths out burst traffic
- ✅ Constant processing rate
- ✅ Provides bounded queue
- ✅ Prevents downstream overload

**Cons:**
- ❌ Can introduce latency
- ❌ Requests can be dropped if queue is full

**Use Case:** Background job processing, message queues

**Configuration Parameters:**
- `Processing Rate`: Rate at which requests are processed per second
- `Max Capacity`: Maximum queue size

### 4. Token Bucket
Allows burst traffic up to a maximum capacity with tokens refilling at a constant rate.

**Pros:**
- ✅ Allows controlled bursts
- ✅ Flexible and intuitive
- ✅ Good for variable traffic patterns
- ✅ Industry standard (used by AWS, Google Cloud)

**Cons:**
- ❌ More complex to tune
- ❌ Requires careful capacity planning

**Use Case:** APIs with bursty traffic, public APIs, CDNs

**Configuration Parameters:**
- `Refill Rate`: Number of tokens added per second
- `Max Capacity`: Maximum bucket capacity

## 🛠 Tech Stack

- **Java 21** - Latest LTS version with modern language features
- **Spring Boot 4.0.1** - Application framework
- **Spring Web MVC** - REST API implementation
- **Spring Data Redis** - Distributed rate limiting support
- **Lombok** - Reduces boilerplate code
- **Jakarta Validation** - Request validation
- **Maven** - Build and dependency management
- **Docker Compose** - Container orchestration for Redis

## 🏗 Architecture

The rate limiter follows a clean, layered architecture:

```
┌─────────────────────────────────────────────┐
│          HTTP Request (Client)              │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│          RateFilter (Servlet Filter)        │
│  - Intercepts all incoming requests         │
│  - Extracts client IP address               │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│         RateLimitManager                    │
│  - Retrieves/Creates algorithm for IP       │
│  - Delegates rate limiting decision         │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│    ConfigurationStoreService                │
│  - Stores IP → Config mapping               │
│  - Stores IP → Algorithm instance mapping   │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│      RateLimitAlgorithm (Interface)         │
│         ├─ TokenBucketAlgorithm             │
│         ├─ LeakyBucketAlgorithm             │
│         ├─ FixedCounterAlgorithm            │
│         └─ SlidingWindowAlgorithm           │
└─────────────────────────────────────────────┘
```

### Component Responsibilities:

- **RateFilter**: Entry point, intercepts all HTTP requests and enforces rate limits
- **RateLimitManager**: Coordinates rate limiting operations, manages algorithm lifecycle
- **ConfigurationStoreService**: Maintains mappings between IPs, configurations, and algorithm instances
- **RateLimitAlgorithm**: Interface defining the contract for all algorithms
- **Algorithm Implementations**: Concrete implementations of rate limiting logic

## 🚀 Getting Started

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Redis** (optional, for distributed setup)

### Installation & Running

1. **Clone the repository:**
```bash
git clone https://github.com/vijha742/Rate-Limiter
cd rate-limiter
```

2. **Build the project:**
```bash
./mvnw clean install
```

3. **Run the application:**
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

4. **Optional - Start Redis (for distributed mode):**
```bash
docker-compose up -d
```

## ⚙️ Configuration

### Application Configuration

Edit [src/main/resources/application.yaml](src/main/resources/application.yaml):

```yaml
spring:
  application:
    name: rate-limiter

server:
  address: 0.0.0.0
  port: 8080
```

### Runtime Configuration

Configure rate limiting for your IP using the configuration endpoint:

```bash
curl -X POST http://localhost:8080/api/config \
  -H "Content-Type: application/json" \
  -d '{
    "algo": "TOKEN_BUCKET",
    "parameters": {
      "Refill Rate": 5,
      "Max Capacity": 10
    }
  }'
```

## 📡 API Endpoints

### 1. Test Endpoint
**GET** `/api/test`

A simple endpoint to test the rate limiter.

**Response:**
```
Hello Vikas Jha...!
```

**Rate Limited Response (429):**
```
All requests have been exhausted. Try again later!!!
```

### 2. Configure Rate Limit
**POST** `/api/config`

Configure rate limiting algorithm and parameters for your IP.

**Request Body:**
```json
{
  "algo": "TOKEN_BUCKET | FIXED_WINDOW | LEAKY_BUCKET | SLIDING_WINDOW",
  "parameters": {
    "parameter_name": value
  }
}
```

**Examples:**

**Token Bucket:**
```json
{
  "algo": "TOKEN_BUCKET",
  "parameters": {
    "Refill Rate": 5,
    "Max Capacity": 10
  }
}
```

**Fixed Window:**
```json
{
  "algo": "FIXED_WINDOW",
  "parameters": {
    "Max Requests": 10,
    "Window Length": 60
  }
}
```

**Leaky Bucket:**
```json
{
  "algo": "LEAKY_BUCKET",
  "parameters": {
    "Processing Rate": 5,
    "Max Capacity": 20
  }
}
```

**Sliding Window:**
```json
{
  "algo": "SLIDING_WINDOW",
  "parameters": {
    "Max Requests": 100,
    "Window Length": 60
  }
}
```

## 🧪 Testing

The project includes comprehensive test scripts in the [rate-limit_testing_scripts/](rate-limit_testing_scripts/) directory.

### Available Test Scripts:

1. **FixedCounterTest.sh** - Tests Fixed Window algorithm with boundary scenarios
2. **SlidingWindowTest.sh** - Tests Sliding Window with burst prevention
3. **LeakyBucketTest.sh** - Tests Leaky Bucket with constant processing rate
4. **TokenBucketTest.sh** - Tests Token Bucket with burst handling
5. **RunAllTests.sh** - Executes all test scripts

### Running Tests:

```bash
cd rate-limit_testing_scripts
chmod +x *.sh
./RunAllTests.sh
```

Or run individual tests:
```bash
./FixedCounterTest.sh
./TokenBucketTest.sh
```

For detailed test documentation, see [rate-limit_testing_scripts/README.md](rate-limit_testing_scripts/README.md).

## 📁 Project Structure

```
rate-limiter/
├── compose.yaml                          # Docker Compose for Redis
├── pom.xml                               # Maven dependencies
├── rate-limit_testing_scripts/           # Test scripts for each algorithm
│   ├── FixedCounterTest.sh
│   ├── LeakyBucketTest.sh
│   ├── SlidingWindowTest.sh
│   ├── TokenBucketTest.sh
│   ├── TokenBucketRaceTest.java
│   ├── RunAllTests.sh
│   └── README.md
└── src/
    ├── main/
    │   ├── java/com/vikas/rate_limiter/
    │   │   ├── RateLimiterApplication.java    # Spring Boot entry point
    │   │   ├── RateLimitManager.java          # Orchestrates rate limiting
    │   │   ├── algorithm/                     # Rate limiting algorithms
    │   │   │   ├── RateLimitAlgorithm.java    # Interface
    │   │   │   ├── FixedCounterRateLimitAlgorithm.java
    │   │   │   ├── LeakyBucketRateLimitAlgorithm.java
    │   │   │   ├── SlidingWindowRateLimitAlgorithm.java
    │   │   │   └── TokenBucketRateLimitAlgorithm.java
    │   │   ├── config/
    │   │   │   └── ConfigurationStoreService.java  # Configuration store
    │   │   ├── controllers/
    │   │   │   └── TestController.java        # REST endpoints
    │   │   ├── dto/
    │   │   │   └── RequestConfigDTO.java      # Configuration DTO
    │   │   ├── filter/
    │   │   │   └── RateFilter.java            # Request interceptor
    │   │   └── utils/
    │   │       └── IpUtil.java                # IP extraction utility
    │   └── resources/
    │       └── application.yaml               # Application configuration
    └── test/
        └── java/com/vikas/rate_limiter/
            └── RateLimiterApplicationTests.java
```

## 🔍 How It Works

### Request Flow:

1. **Request Arrives**: A client makes an HTTP request to any endpoint
2. **Filter Intercepts**: `RateFilter` (a `OncePerRequestFilter`) intercepts the request
3. **IP Extraction**: The client's IP address is extracted using `IpUtil`
4. **Rate Limit Check**: `RateLimitManager.allowRequest(ip)` is called
5. **Algorithm Selection/Creation**:
   - If configuration exists for the IP, the corresponding algorithm is retrieved/created
   - If no configuration exists, a default Fixed Counter algorithm is used (5 requests per 10 seconds)
6. **Decision**: The algorithm's `acceptRequest()` method determines if the request should be allowed
7. **Response**:
   - **Allowed**: Request proceeds through the filter chain to the controller
   - **Denied**: HTTP 429 status is returned with an error message

### Algorithm Lifecycle:

```java
IP → Configuration (stored) → Algorithm Instance (created once) → Rate Limiting Decision
```

Each IP gets its own algorithm instance, maintaining independent state across requests.

### Thread Safety:

All algorithm implementations use `synchronized` methods to ensure thread-safe operations in concurrent environments.

## 🚧 Future Scope

### Planned Features:

1. **Distributed Rate Limiting**
   - Full Redis integration for multi-instance deployments
   - Consistent hashing for scalability

2. **Advanced Features**
   - User/API key-based rate limiting (beyond IP)
   - Hierarchical rate limits (per-user, per-API, global)
   - Rate limit headers (`X-RateLimit-Remaining`, `X-RateLimit-Reset`)
   - Whitelist/Blacklist support

3. **Monitoring & Observability**
   - Metrics integration (Prometheus, Micrometer)
   - Real-time dashboards
   - Alert on rate limit violations
   - Request analytics

4. **Configuration Enhancements**
   - YAML/Properties file-based configuration
   - Admin UI for configuration management
   - A/B testing different algorithms
   - Dynamic algorithm switching

5. **Additional Algorithms**
   - Sliding Window Counter (hybrid approach)
   - Adaptive rate limiting (ML-based)
   - Circuit breaker integration

6. **Performance Optimizations**
   - Memory-efficient data structures
   - Async processing for leaky bucket
   - Cache warming strategies

7. **Testing & Documentation**
   - Load testing benchmarks
   - Performance comparison reports
   - API documentation (Swagger/OpenAPI)
   - Integration with Spring Security

## ❓ FAQ

### Q: Which algorithm should I use?
**A:** It depends on your use case:
- **Token Bucket**: General purpose, allows bursts (recommended for most APIs)
- **Sliding Window**: Accurate rate limiting, prevents boundary issues
- **Fixed Window**: Simple, low memory, acceptable for low-precision needs
- **Leaky Bucket**: Constant processing rate, good for queuing scenarios

### Q: How do I set different limits for different endpoints?
**A:** Currently, rate limiting is IP-based globally. For endpoint-specific limits, you'll need to extend the `RequestConfigDTO` and filtering logic.

### Q: Can I use this in a distributed environment?
**A:** The Redis dependencies are included but need to be configured. The current implementation uses in-memory storage. For distributed setups, implement `ConfigurationStoreService` with Redis.

### Q: What happens when rate limit is exceeded?
**A:** The server returns HTTP 429 (Too Many Requests) with the message: "All requests have been exhausted. Try again later!!!"

### Q: How do I add a new rate limiting algorithm?
**A:** 
1. Create a new class implementing `RateLimitAlgorithm` interface
2. Add the algorithm to the `Algorithm` enum in `RequestConfigDTO`
3. Add a case in the switch statement in `RateLimitManager.getAlgoWithIp()`

### Q: Is this production-ready?
**A:** The core algorithms are solid, but you should consider:
- Switching to Redis for distributed environments
- Adding monitoring and metrics
- Implementing proper configuration management
- Adding comprehensive logging

### Q: How accurate are the algorithms?
**A:** Token Bucket and Sliding Window are highly accurate. Fixed Window has boundary issues but is efficient. Leaky Bucket provides constant rate processing.

### Q: How to setup IP based configuration?
**A:** You can set up a limiting configuration for any IP by sending a `POST` request to `localhost:8080/api/config` with the following body structure (if the `ip` field is omitted, the configuration applies to the IP associated with the request):

```json
{
  "ip": "string | null",
  "algo": "TOKEN_BUCKET | FIXED_WINDOW | LEAKY_BUCKET | SLIDING_WINDOW",
  "parameters": {
    "key": "int"
  }
}
```

**Required parameters by algorithm:**
- **TOKEN_BUCKET**: `Refill Rate`, `Max Capacity`
- **LEAKY_BUCKET**: `Processing Rate`, `Max Capacity`
- **SLIDING_WINDOW**: `Max Requests`, `Window Length`
- **FIXED_WINDOW**: `Max Requests`, `Window Length`

---

**Built with Spring Boot and Java 21 to understand how rate-limiting in real-life systems work.**
