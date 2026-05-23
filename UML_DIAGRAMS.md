# Rate-Limiter: UML Diagrams & Design

## 1. Class Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         ALGORITHM HIERARCHY                             │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────┐
│  <<interface>>                        │
│  RateLimitAlgorithm                  │
├──────────────────────────────────────┤
│ + acceptRequest(): RateLimitDecision │
│ + getRemainingRequests(): long       │
│ + getResetTime(): long               │
│ + getConfig(): Map<String, Object>   │
│ + getAlgorithmName(): String         │
└──────────────────────────────────────┘
        △                  △
        │                  │
        │ implements       │ implements
        │                  │
        └─────────┬────────┘
                  │
        ┌─────────┼─────────┬──────────────────┐
        │         │         │                  │
        ▼         ▼         ▼                  ▼
    ┌────────────────────┐ ┌────────────────────┐
    │TokenBucketAlgo     │ │LeakyBucketAlgo     │
    ├────────────────────┤ ├────────────────────┤
    │ - tokens: double   │ │ - queue: Queue     │
    │ - capacity: double │ │ - capacity: int    │
    │ - refillRate: dbl  │ │ - flowRate: double │
    │ - lastRefillTime   │ │ - lastLeakTime     │
    ├────────────────────┤ ├────────────────────┤
    │ + acceptRequest()  │ │ + acceptRequest()  │
    │ + remaining...()   │ │ + remaining...()   │
    └────────────────────┘ └────────────────────┘

    ┌────────────────────┐ ┌────────────────────┐
    │FixedCounterAlgo    │ │SlidingWindowAlgo   │
    ├────────────────────┤ ├────────────────────┤
    │ - counter: int     │ │ - timestamps: Deque│
    │ - maxRequests: int │ │ - maxRequests: int │
    │ - windowSize: long │ │ - windowTime: long │
    │ - windowStart: long│ │                    │
    ├────────────────────┤ ├────────────────────┤
    │ + acceptRequest()  │ │ + acceptRequest()  │
    │ + remaining...()   │ │ + remaining...()   │
    └────────────────────┘ └────────────────────┘
```

---

## 2. Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   SPRING BOOT APPLICATION                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    FILTER LAYER                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │  RateFilter (OncePerRequestFilter)                     │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│           │                                                             │
│           │ invokes                                                     │
│           ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              ORCHESTRATION LAYER                                 │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │  RateLimitManager                                      │   │  │
│  │  │  - Delegates to algorithms                            │   │  │
│  │  │  - Manages caches                                     │   │  │
│  │  │  - Queries configuration                             │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│      │              │                         │                        │
│      │ uses         │ creates                 │ queries                │
│      ▼              ▼                         ▼                        │
│  ┌─────────┐  ┌──────────────┐  ┌─────────────────────────────┐     │
│  │Algorithms│  │Caches        │  │Configuration Services      │     │
│  │(4 types)│  │- Algorithm   │  │- ConfigurationStoreService │     │
│  │         │  │- Config      │  │- MongoConfigService        │     │
│  │         │  │- IP          │  │- EndpointConfigService     │     │
│  └─────────┘  └──────────────┘  └─────────────────────────────┘     │
│           │                                  │                        │
└───────────┼──────────────────────────────────┼────────────────────────┘
            │                                  │
            │ persists                         │ connects
            ▼                                  ▼
   ┌─────────────────┐            ┌──────────────────────┐
   │   REDIS         │            │  MONGODB/YAML        │
   │                 │            │                      │
   │ • Algorithm     │            │ • Configurations    │
   │   state         │            │ • User limits       │
   │ • Cache         │            │ • Audit logs        │
   └─────────────────┘            └──────────────────────┘
```

---

## 3. Sequence Diagram - Request Processing

```
Client    Browser          RateFilter       RateLimitManager    Algorithm    MongoDB
  │          │                 │                   │                │           │
  │  1. HTTP │                 │                   │                │           │
  │  Request │                 │                   │                │           │
  ├─────────►│                 │                   │                │           │
  │          │  2. Intercept   │                   │                │           │
  │          ├────────────────►│                   │                │           │
  │          │                 │  3. Extract IP    │                │           │
  │          │                 │                   │                │           │
  │          │  4. Check cache │                   │                │           │
  │          │  for algorithm  │                   │                │           │
  │          │                 ├──────────────────►│                │           │
  │          │                 │  5. Get/Create    │                │           │
  │          │                 │  Algorithm        │                │           │
  │          │                 │                   │  6. Query      │           │
  │          │                 │                   │  Config (cache)│           │
  │          │                 │                   │                │           │
  │          │                 │                   │  7. If miss:   │           │
  │          │                 │                   │  Query MongoDB ├──────────►│
  │          │                 │                   │                │           │
  │          │                 │                   │◄──────────────┤           │
  │          │                 │  8. Create        │  Config        │           │
  │          │                 │  Algorithm        │                │           │
  │          │                 │◄──────────────────┤                │           │
  │          │ 9. Cache        │                   │                │           │
  │          │ Algorithm       │                   │                │           │
  │          │                 │  10. Accept       │                │           │
  │          │  11. Execute    │  Request          │                │           │
  │          │  Algorithm      ├──────────────────────────────────►│           │
  │          │                 │                   │  12. Check     │           │
  │          │                 │                   │  quota & update│           │
  │          │                 │   13. Decision    │  state         │           │
  │          │                 │◄──────────────────┤                │           │
  │          │ 14. Set Headers │                   │                │           │
  │          │ Continue/429    │                   │                │           │
  │          │                 │                   │                │           │
  │◄─────────┤                 │                   │                │           │
  │  Response│                 │                   │                │           │
  │          │                 │                   │                │           │
```

---

## 4. State Diagram - Algorithm States

### Token Bucket State Machine

```
                        ┌─────────────────┐
                        │   INITIALIZED   │
                        │ (Full capacity) │
                        └────────┬────────┘
                                 │
                  ┌──────────────┼──────────────┐
                  │              │              │
                  ▼              ▼              ▼
            ┌──────────┐   ┌──────────┐   ┌──────────┐
            │ACCEPTING │   │ REFILLING│   │ DEPLETING│
            │REQUESTS  │   │ TOKENS   │   │ TOKENS   │
            └────┬─────┘   └────┬─────┘   └────┬─────┘
                 │              │              │
                 │ tokens == 0   │ time passes  │ request comes
                 └──────────────►├─────────────►│
                                 │              │
                                 └──────────────┘
```

### Leaky Bucket State Machine

```
              ┌─────────────────┐
              │   INITIALIZED   │
              │  (Empty queue)  │
              └────────┬────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ QUEUING  │  │ LEAKING  │  │  FULL    │
   │ REQUESTS │  │ REQUESTS │  │ CAPACITY │
   └────┬─────┘  └────┬─────┘  └────┬─────┘
        │ req added   │ time pass    │ queue full
        │             │              │
        └─────────────┴──────────────┘
        
   Queue size: 0 ◄─────────────► Capacity
```

---

## 5. Entity Relationship Diagram (MongoDB)

```
┌─────────────────────────────────────────┐
│    RateLimitConfigEntity                │
├─────────────────────────────────────────┤
│ (PK) _id: ObjectId                      │
│ (UQ) ip: String                         │
│      algo: String                       │
│           [TOKEN_BUCKET,                │
│            LEAKY_BUCKET,                │
│            FIXED_WINDOW,                │
│            SLIDING_WINDOW]              │
│      parameters: Map<String, Object>    │
│           ├─ refillRate: Number         │
│           ├─ capacity: Number           │
│           ├─ flowRate: Number           │
│           ├─ maxRequests: Number        │
│           ├─ windowSize: Number         │
│           └─ windowTime: Number         │
│      createdAt: Timestamp               │
│      updatedAt: Timestamp               │
│      createdBy: String                  │
│      (V) version: Long                  │
└─────────────────────────────────────────┘

Indexes:
  - ip (UNIQUE)
  - createdAt (TTL: 30 days)
  - algo
  - updatedAt (DESC)

Relationships:
  One IP ──── One Active Config
```

---

## 6. Deployment Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         PRODUCTION ENVIRONMENT                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐    │
│  │  Load Balancer  │  │  Load Balancer  │  │  Load Balancer  │    │
│  │  (Round Robin)  │  │  (Round Robin)  │  │  (Round Robin)  │    │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘    │
│           │                    │                    │              │
│  ┌────────▼────────────────────▼────────────────────▼────────┐    │
│  │                   Application Tier                       │    │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐  │    │
│  │  │ Spring Boot   │ │ Spring Boot   │ │ Spring Boot   │  │    │
│  │  │ Instance 1    │ │ Instance 2    │ │ Instance 3    │  │    │
│  │  │ (Port 8080)   │ │ (Port 8080)   │ │ (Port 8080)   │  │    │
│  │  │ RateLimiter   │ │ RateLimiter   │ │ RateLimiter   │  │    │
│  │  └───────────────┘ └───────────────┘ └───────────────┘  │    │
│  └──────────────────┬─────────────────────────────────────┘    │
│                     │                                          │
│  ┌──────────────────▼──────────────────┐                       │
│  │      Data Tier (Shared Services)    │                       │
│  │  ┌───────────────┐ ┌──────────────┐ │                       │
│  │  │ Redis Cluster │ │ MongoDB      │ │                       │
│  │  │ (Distributed) │ │ (Replicated) │ │                       │
│  │  │ • State       │ │ • Configs    │ │                       │
│  │  │ • Cache       │ │ • Audit      │ │                       │
│  │  └───────────────┘ └──────────────┘ │                       │
│  └─────────────────────────────────────┘                       │
│                                                                │
│  ┌────────────────────────────────────────────────────────┐   │
│  │    Monitoring & Observability                         │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  │   │
│  │  │ Prometheus   │ │ Grafana      │ │ ELK/Splunk  │  │   │
│  │  │ (Metrics)    │ │ (Dashboards) │ │ (Logs)      │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────┘  │   │
│  └────────────────────────────────────────────────────────┘   │
│                                                                │
└──────────────────────────────────────────────────────────────────┘
```

---

## 7. Activity Diagram - Configuration Update Flow

```
Start
  │
  ▼
┌─────────────────┐
│ Client sends   │
│ config POST    │
└────────┬────────┘
         │
         ▼
    ┌─────────────────┐
    │ Validate config │
    └────────┬────────┘
             │
        ┌────▼────┐
        │ Valid?  │
        └────┬────┘
        Yes  │  No
            │   └──────────────┐
            │                  ▼
            │            ┌──────────────┐
            │            │Return 400    │
            │            │error message │
            │            └──────────────┘
            │                  │
            ▼                  │
    ┌──────────────┐           │
    │Query MongoDB │           │
    │for old config│           │
    └──────┬───────┘           │
           │                   │
           ▼                   │
    ┌────────────────┐         │
    │Save to MongoDB │         │
    └──────┬─────────┘         │
           │                   │
           ▼                   │
    ┌──────────────┐           │
    │Clear algo    │           │
    │cache for IP  │           │
    └──────┬───────┘           │
           │                   │
           ▼                   │
    ┌──────────────┐           │
    │Clear config  │           │
    │cache for IP  │           │
    └──────┬───────┘           │
           │                   │
           ▼                   │
    ┌──────────────────┐       │
    │Return 200 OK     │       │
    │with new config   │       │
    └─────────┬────────┘       │
              │                │
              └────────┬───────┘
                       │
                       ▼
                     End
```

---

## 8. Package Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│              com.vikas.rate_limiter                              │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ algorithm (Implementations)                                │ │
│  │ ├─ RateLimitAlgorithm (interface)                         │ │
│  │ ├─ TokenBucketRateLimitAlgorithm                          │ │
│  │ ├─ LeakyBucketRateLimitAlgorithm                          │ │
│  │ ├─ FixedCounterRateLimitAlgorithm                         │ │
│  │ └─ SlidingWindowRateLimitAlgorithm                        │ │
│  └────────────────────────────────────────────────────────────┘ │
│                          ▲ imports                                │
│                          │                                        │
│  ┌──────────────────────┴────────────────────────────────────┐  │
│  │ RateLimitManager (Orchestrator)                           │  │
│  │ └─ Uses: Algorithms, Config Services, Caches             │  │
│  └──────────────────────┬─────────────────────────────────────┘  │
│                         │ imports                                 │
│              ┌──────────┴──────────┐                              │
│              │                     │                              │
│  ┌───────────▼──────────┐ ┌────────▼──────────┐                 │
│  │ filter (Request)     │ │ controllers (API) │                 │
│  │ ├─ RateFilter        │ │ ├─ TestController│                 │
│  │ └─ ...               │ │ ├─ ConfigCtl     │                 │
│  └──────────────────────┘ │ └─ StatsCtl      │                 │
│                           └─────────────────────┘                │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ config (Configuration & Setup)                             │ │
│  │ ├─ ConfigurationStoreService (abstract)                   │ │
│  │ ├─ MongoConfigurationStoreService                         │ │
│  │ ├─ EndpointConfigService                                 │ │
│  │ ├─ RedisConfiguration                                    │ │
│  │ ├─ MongoConfiguration                                    │ │
│  │ └─ MetricsConfig                                         │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ dto (Data Transfer Objects)                                │ │
│  │ ├─ RequestConfigDTO                                       │ │
│  │ ├─ RateLimitDecision                                     │ │
│  │ ├─ ConfigResponse                                        │ │
│  │ └─ ErrorResponse                                         │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ utils (Utilities)                                          │ │
│  │ ├─ IpUtil                                                 │ │
│  │ ├─ TestClock                                              │ │
│  │ └─ RateLimiterProperties                                 │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ service (Business Logic)                                   │ │
│  │ ├─ MongoConfigurationStoreService                         │ │
│  │ └─ EndpointConfigService                                 │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ errorHandler (Exception Handling)                          │ │
│  │ └─ GlobalExceptionHandler                                 │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 9. Use Case Diagram

```
                            ┌─────────────────┐
                            │   Rate Limiter  │
                            │    System       │
                            └─────────────────┘
                                     △
              ┌──────────────┬────────┼────────┬──────────────┐
              │              │        │        │              │
              ▼              ▼        ▼        ▼              ▼
        ┌────────────┐ ┌──────────┐┌────────┐┌──────────┐┌─────────┐
        │   Client   │ │  Admin   ││ Monitor││ DevOps   ││ System  │
        │(API User)  │ │(Config)  ││(Metrics││(Config)  ││(Testing)│
        └────────────┘ └──────────┘└────────┘└──────────┘└─────────┘
              │              │        │        │              │
              ├──────────────┼────────┼────────┼──────────────┤
              │              │        │        │              │
              ▼              ▼        ▼        ▼              ▼
        ┌──────────────┐ ┌──────────────┐ ┌────────────────┐
        │Make Request  │ │Configure     │ │View Metrics    │
        │              │ │Algorithm     │ │                │
        └──────┬───────┘ └──────┬───────┘ └────────┬───────┘
               │                │                   │
               ├────────────────┼───────────────────┤
               │                │                   │
               ▼                ▼                   ▼
        ┌──────────────────┐ ┌──────────────────┐
        │Check Rate Limit  │ │Update Config     │
        │                  │ │in MongoDB        │
        └──────┬───────────┘ └──────┬───────────┘
               │                    │
               ├────────────────────┤
               │                    │
               ▼                    ▼
        ┌────────────────────────────────────┐
        │Execute Algorithm Decision          │
        │(Token Bucket, Leaky Bucket, etc)   │
        └────────┬─────────────────────────┘
                 │
        ┌────────▼──────────┐
        │Allow or Reject    │
        │Request            │
        └───────────────────┘
```

---

## 10. Interaction Diagram - Multi-Instance Scenario

```
┌────────────────┐         ┌────────────────┐         ┌────────────────┐
│  Instance 1    │         │  Instance 2    │         │  Instance 3    │
│ (Port 8080)    │         │ (Port 8081)    │         │ (Port 8082)    │
└────────┬───────┘         └────────┬───────┘         └────────┬───────┘
         │                          │                          │
         │  Client IP: 192.168.1.1  │                          │
         │  Request 1               │                          │
         ├─────────────────────────►│                          │
         │                          │ Check Redis             │
         │                          ├──────────┐              │
         │                          │          │              │
         │                          │ Get state from Redis    │
         │                          │◄─────────┘              │
         │                          │                          │
         │                          │ Update Redis            │
         │                          ├──────────┐              │
         │                          │          │              │
         │                          │ Redis updated           │
         │                          │◄─────────┘              │
         │                          │                          │
         │                     Response                        │
         │◄─────────────────────────┤                          │
         │  429 (Rate Limited)      │                          │
         │                          │                          │
         │  Client IP: 192.168.1.1  │                          │
         │  Request 2               │                          │
         │                          │                          │
         │                          │                    Request 3
         │                          │                          ├─────►
         │                          │                          │
         │                          │  Check Redis            │
         │                          │                          ├──────────┐
         │                          │                          │          │
         │ All three instances      │ Get same state          │          │
         │ see consistent state     │ from Redis              │          │
         │ across all IPs           │◄──────────┘              │          │
         │                          │                          │          │
         │  Redis acts as           │                          │          │
         │  single source of truth  │                          │          │
         │                          │                          │          │
         │                   ┌──────▼──────────┐              │          │
         │                   │  Shared Redis   │              │          │
         │                   │  Cluster        │              │          │
         │                   │                 │              │          │
         │                   │ 192.168.1.1:    │              │          │
         │                   │ tokens=0        │              │          │
         │                   │ reset=T+45s     │              │          │
         │                   └─────────────────┘              │          │
         │                                                     │          │
         │                                          Response   │          │
         │                                          429 (RL)   │          │
         │                                                     │◄─────────┘
         │
```

---

## 11. State Chart - Request Lifecycle

```
START
  │
  ▼
┌─────────────────────────┐
│ HttpServletRequest      │
│ arrives at filter chain │
└──────────┬──────────────┘
           │
           ▼
    ┌─────────────────┐
    │ Is RateFilter   │
    │ in chain?       │
    └────────┬────────┘
         Yes │  No
             │   └─────────────────────┐
             │                         │ Skip rate limiting
             ▼                         │
    ┌──────────────────┐               │
    │ Extract IP from  │               │
    │ request headers  │               │
    └────────┬─────────┘               │
             │                         │
             ▼                         │
    ┌──────────────────┐               │
    │ Check algo cache │               │
    │ for IP           │               │
    └────────┬─────────┘               │
             │                         │
        ┌────▼──────┐                  │
        │ In cache? │                  │
    Yes │           │ No              │
        │           └────────┐        │
        │                    ▼        │
        │            ┌──────────────┐ │
        │            │ Query config │ │
        │            │ from MongoDB │ │
        │            └──────┬───────┘ │
        │                   │         │
        │                   ▼         │
        │           ┌──────────────┐  │
        │           │Create algo   │  │
        │           │instance      │  │
        │           └──────┬───────┘  │
        │                  │          │
        └──────────┬───────┘          │
                   │                  │
                   ▼                  │
            ┌──────────────┐          │
            │ Cache algo   │          │
            │ instance     │          │
            └──────┬───────┘          │
                   │                  │
                   ▼                  │
            ┌──────────────┐          │
            │ Execute algo │          │
            │.acceptReq()  │          │
            └──────┬───────┘          │
                   │                  │
              ┌────▼────┐             │
              │ Allowed? │             │
          Yes │         │ No          │
              │         │             │
              ▼         ▼             │
        ┌──────────┐ ┌────────────┐   │
        │ Continue │ │Return 429  │   │
        │ filter   │ │Too Many    │   │
        │ chain    │ │Requests    │   │
        └────┬─────┘ └─────┬──────┘   │
             │             │          │
             └─────┬───────┘          │
                   │                  │
                   ▼                  │
        ┌──────────────────────┐      │
        │ Set Rate Limit Headers│     │
        │ X-RateLimit-Limit    │      │
        │ X-RateLimit-Remaining│      │
        │ X-RateLimit-Reset    │      │
        └────────┬─────────────┘      │
                 │                    │
                 │                    │
                 └────────┬───────────┘
                          │
                          ▼
                    ┌──────────────┐
                    │ Send Response│
                    │ to Client    │
                    └──────┬───────┘
                           │
                           ▼
                         END
```

---

## 12. Implementation Class Diagram with Annotations

```
@Repository
┌─────────────────────────────────────┐
│ RateLimitConfigRepository           │
├─────────────────────────────────────┤
│ extends CrudRepository<...>         │
├─────────────────────────────────────┤
│ + findByIp(String): Optional        │
│ + deleteByIp(String): void          │
│ + findAll(): List                   │
└─────────────────────────────────────┘
         │ injects
         ▼
@Service
┌─────────────────────────────────────┐
│ RateLimitManager                    │
├─────────────────────────────────────┤
│ @Autowired ConfigService            │
│ @Autowired Repository               │
│ @Autowired MetricsService           │
├─────────────────────────────────────┤
│ + allowRequest(String): Decision    │
│ + configure(Config): Response       │
├─────────────────────────────────────┤
│ - algorithmCache: Map               │
│ - configCache: Map (Caffeine)       │
└─────────────────────────────────────┘
         │ contains
         ▼
┌─────────────────────────────────────┐
│ + RateLimitAlgorithm instances      │
│   (one per IP)                      │
├─────────────────────────────────────┤
│ Cached:                             │
│ - 192.168.1.1 → TokenBucket        │
│ - 192.168.1.2 → LeakyBucket        │
│ - 192.168.1.3 → FixedCounter       │
└─────────────────────────────────────┘
         ▲
         │ registers
         │
@Component
┌─────────────────────────────────────┐
│ RateFilter                          │
├─────────────────────────────────────┤
│ @Autowired RateLimitManager         │
│ @Autowired IpUtil                   │
├─────────────────────────────────────┤
│ # doFilterInternal(...)             │
│ - extractClientIp(...)              │
│ - setRateLimitHeaders(...)          │
└─────────────────────────────────────┘
         │ calls
         ▼
@RestController
┌─────────────────────────────────────┐
│ TestController                      │
├─────────────────────────────────────┤
│ @Autowired RateLimitManager         │
├─────────────────────────────────────┤
│ @GetMapping("/api/test")            │
│ + test(): ResponseEntity            │
└─────────────────────────────────────┘

@RestController
┌─────────────────────────────────────┐
│ ConfigController                    │
├─────────────────────────────────────┤
│ @Autowired RateLimitManager         │
│ @Autowired ConfigService            │
├─────────────────────────────────────┤
│ @PostMapping("/api/config")         │
│ + configure(...): ResponseEntity    │
│ @GetMapping("/api/config/{ip}")     │
│ + getConfig(...): ResponseEntity    │
└─────────────────────────────────────┘
```

---

## 13. Design Patterns Used

### 1. **Strategy Pattern**
- **Context**: RateLimitManager
- **Strategies**: Algorithm implementations (Token Bucket, Leaky Bucket, etc.)
- **Benefit**: Switch algorithms at runtime based on configuration

### 2. **Factory Pattern**
- **Factory**: RateLimitManager (getAlgoWithIp method)
- **Products**: Different algorithm instances
- **Benefit**: Centralized creation logic

### 3. **Template Method Pattern**
- **Template**: RateLimitAlgorithm interface
- **Implementations**: Concrete algorithms
- **Benefit**: Common contract, different implementations

### 4. **Singleton Pattern**
- **Instance**: RateLimitManager (Spring Bean)
- **Benefit**: One central orchestrator for entire application

### 5. **Proxy Pattern**
- **Proxy**: RateFilter
- **Real Subject**: Actual endpoint handlers
- **Benefit**: Rate limiting without modifying endpoint code

### 6. **Observer Pattern**
- **Subject**: MetricsService
- **Observers**: Prometheus collectors
- **Benefit**: Metrics collection without tight coupling

### 7. **Repository Pattern**
- **Repository**: RateLimitConfigRepository
- **Benefit**: Abstraction over data access

### 8. **Decorator Pattern**
- **Decorator**: RateFilter decorates FilterChain
- **Benefit**: Add rate limiting behavior transparently

---

## Summary

This comprehensive UML documentation provides:
- **Class relationships** and hierarchies
- **Component dependencies** and interactions
- **Request flow** through the system
- **Algorithm state machines** and behaviors
- **Configuration management** data model
- **Deployment architecture** for production
- **Design patterns** employed
- **Use cases** and actor interactions

This serves as a complete reference for understanding the system design.
