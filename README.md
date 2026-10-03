# RouteAssign

**Automatic Delivery Partner Assignment System**

A production-grade Spring Boot backend that automatically matches incoming delivery orders to the best available delivery partner using a weighted composite scoring engine. Every configuration value — scoring weights, working hours, distance thresholds, timeouts — lives in MySQL and can be changed at runtime through an admin API without redeploying the application.

---

## Table of Contents

1. [What it does](#what-it-does)
2. [Tech stack](#tech-stack)
3. [Quick start](#quick-start)
4. [Configuration reference](#configuration-reference)
5. [Architecture overview](#architecture-overview)
6. [Assignment engine deep dive](#assignment-engine-deep-dive)
7. [API reference](#api-reference)
8. [Phase roadmap](#phase-roadmap)
9. [Design decisions](#design-decisions)
10. [Interview talking points](#interview-talking-points)

---

## What it does

When a customer places an order, RouteAssign automatically:

1. Finds all active, available delivery partners with sufficient carrying capacity
2. Runs each eligible candidate through five configurable scoring factors
3. Selects the highest-scoring partner
4. Locks the partner row (pessimistic write lock) and re-validates capacity to prevent race conditions
5. Persists the `DeliveryAssignment` and a full `AssignmentDecision` audit trail
6. If a partner rejects, times out, or fails mid-delivery, automatically reassigns to the next-best partner while excluding the failed one

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 24 |
| Framework | Spring Boot 3.5.3 |
| Persistence | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Cache | Redis (cache-aside for config rules) |
| Security | Spring Security (stateless JWT — filter wired, endpoints currently open for dev) |
| Build | Maven 3 |
| Utilities | Lombok, MapStruct, Jackson |

---

## Quick start

### Prerequisites

- Java 24+
- MySQL 8 running on `localhost:3306`
- Redis running on `localhost:6379`
- Maven 3.8+

### 1. Create the database

```sql
CREATE DATABASE routeassign_db;
CREATE USER 'springstudent'@'localhost' IDENTIFIED BY 'springstudent';
GRANT ALL PRIVILEGES ON routeassign_db.* TO 'springstudent'@'localhost';
```

### 2. Start Redis (WSL)

```bash
# inside WSL
sudo service redis-server start
redis-cli ping   # should return PONG
```

### 3. Build and run

```bash
mvn clean package -DskipTests
java -jar target/routeassign-0.0.1-SNAPSHOT.jar
```

Or from the IDE — run `RouteAssignApplication.java`.

### 4. Verify startup

On first boot, `DataInitializer` seeds all business rules and scoring configurations automatically. Check the logs for:

```
Seed [assignment_rules] complete — 9 inserted, 0 already present
Seed [assignment_scoring_rules] complete — 5 inserted, 0 already present
```

### 5. Import the Postman collection

`RouteAssign.postman_collection.json` at the project root contains four phases:

- **Phase 1** — seed users, items, store stock
- **Phase 2** — place orders and walk them through the full delivery lifecycle
- **Phase 3** — read queries (history, leaderboard, dashboard)
- **Phase 4** — edge cases (invalid transitions, reassignment, capacity errors)

---

## Configuration reference

All values below are stored in MySQL and changeable at runtime. The defaults shown are what `DataInitializer` seeds on first boot.

### Assignment rules (`PUT /api/v1/assignment-rules/{key}`)

| Key | Default | Type | Description |
|---|---|---|---|
| `HOME_VENDOR_MAX_DISTANCE` | `30` km | DOUBLE | If an assignment is placed after the late-hour cutoff and partner→vendor distance exceeds this, work defers to the next working day |
| `SAME_VENDOR_MAX_DISTANCE` | `10` km | DOUBLE | Max extra distance for same-vendor partner reuse (route consolidation) |
| `WORKING_HOUR_START` | `10` (10 AM) | INTEGER | Start of the working day, 24-hour clock |
| `WORKING_HOUR_END` | `20` (8 PM) | INTEGER | End of the working day, 24-hour clock |
| `LATE_ASSIGNMENT_HOUR` | `17` (5 PM) | INTEGER | Hour after which the HOME_VENDOR_MAX_DISTANCE rule activates |
| `REST_DURATION_MINUTES` | `30` | INTEGER | Rest time between deliveries for busy-partner reuse ETA calculation |
| `ENABLE_BUSY_PARTNER_REUSE` | `true` | BOOLEAN | Whether to consider partners already mid-delivery for a different vendor |
| `ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES` | `10` | INTEGER | Minutes before an unaccepted assignment auto-expires and triggers reassignment |
| `MAX_ASSIGNMENT_ATTEMPTS` | `3` | INTEGER | Max attempts before an order moves to `WAITING_FOR_PARTNER` |

### Scoring rules (`PUT /api/v1/scoring-rules/{factor}`)

| Factor | Weight | Method | Range | Description |
|---|---|---|---|---|
| `DISTANCE` | 40 | INVERSE_LINEAR | 0–50 km | Total route distance. Shorter = higher score |
| `CAPACITY` | 20 | LINEAR | 0–50 kg | Remaining carrying capacity. More spare = higher score |
| `RATING` | 15 | NORMALIZED | 1–5 | Partner rating. Higher = better. Scale is 1-based so a rating of 1 maps to 0, not 20 |
| `IDLE_TIME` | 15 | LINEAR | 0–10080 min (1 week) | Minutes since last delivery. Longer idle = higher score (fairness) |
| `SAME_VENDOR` | 10 | BINARY | — | Same vendor as current order? 100 : 0. Rewards route consolidation |

All weights are relative — the engine normalises them so their sum does not need to equal 100.

Redis caches both tables with a 5-minute TTL (safety backstop). Every admin write evicts the relevant Redis key immediately.

---

## Architecture overview

```
HTTP Request
     │
     ▼
Controller Layer (12 controllers)
     │
     ▼
Service Layer
  ├── OrderService           → places order, triggers assignment
  ├── DeliveryAssignmentService
  │      ├── Idempotency check
  │      ├── PartnerSelectionAlgorithmService ──────────────────────────────┐
  │      │      ├── Eligibility filter (EligibilityResult per partner)      │
  │      │      ├── AssignmentCandidate build (pre-computed measurements)    │
  │      │      ├── AssignmentScoringEngine                                  │
  │      │      │      └── 5 × AssignmentScoringStrategy (Strategy Pattern)  │
  │      │      │           ├── DistanceScoringStrategy                      │
  │      │      │           ├── CapacityScoringStrategy                      │
  │      │      │           ├── RatingScoringStrategy                        │
  │      │      │           ├── IdleTimeScoringStrategy                      │
  │      │      │           └── SameVendorScoringStrategy                    │
  │      │      └── Winner selection (score → idle-time → random)           │
  │      │                                                                   │
  │      ├── Pessimistic lock on selected partner row (Phase 6)             │
  │      ├── Re-check eligibility on fresh state                            │
  │      └── Persist DeliveryAssignment + AssignmentDecision               ◄┘
  │
  ├── AssignmentLifecycleService  → state machine (accept/reject/expire/...)
  ├── AssignmentReassignmentService → rebuilds AssignmentContext, re-runs engine
  ├── AssignmentExpiryService    → @Scheduled job: detects expired ASSIGNED rows
  │
  ├── AssignmentRuleService      → cache-aside read of assignment_rules
  └── AssignmentScoringRuleService → cache-aside read of assignment_scoring_rules
              │
              ▼
        RuleCacheService
          ├── Redis (primary, TTL 300s)
          └── MySQL (fallback on cache miss or Redis failure)

Persistence
  ├── MySQL — source of truth for all business data
  └── Redis — configuration cache only (never stores order/assignment/user data)
```

### Package structure

```
com.routeassign/
├── config/             Spring config (Security, Redis, DataInitializer, AppConstants)
├── controller/         12 REST controllers
├── domain/
│   ├── entity/         16 JPA entities
│   └── enums/          9 enums (UserRole, DeliveryStatus, OrderStatus, ...)
├── dto/
│   ├── request/        Validated request bodies
│   └── response/       Response DTOs
├── exception/          Custom exceptions + GlobalExceptionHandler
├── repository/         16 Spring Data JPA repositories
├── security/           JwtService (filter ready, endpoints currently open)
└── service/
    ├── cache/          RuleCacheService (Redis layer)
    ├── algorithm/      Scoring engine interfaces + AssignmentContext/Candidate
    │   ├── impl/       Algorithm implementations
    │   └── strategy/   5 scoring strategy @Components
    └── impl/           Business service implementations
```

---

## Assignment engine deep dive

### The full flow for one order

```
Order placed
     │
     ▼
 [ELIGIBILITY]
 Free partners:  active + available + capacity ≥ order weight
 Busy partners:  active + unavailable + capacity OK (if ENABLE_BUSY_PARTNER_REUSE=true)
 Exclusions:     previously-failed partners from AssignmentContext.excludedPartnerIds
     │
     ▼
 [CANDIDATE BUILD]
 For each eligible partner, pre-compute:
   distanceToVendorKm, distanceVendorToCustomerKm, totalDistanceKm
   remainingCapacityKg, partnerRating, idleMinutes, isSameVendor
   eta (DeliveryTimeAlgorithmService), workStartTime
     │
     ▼
 [SCORING]
 AssignmentScoringEngine dispatches to 5 strategies:
   factorScore = strategy.calculateScore(context, dbConfig)  ← [0,100]
   finalScore  = Σ(factorScore × weight) / totalWeight        ← [0,100]
     │
     ▼
 [SELECTION]
 1. Highest finalScore wins
 2. Tie → longest idle time (fairness)
 3. Still tied → random
     │
     ▼
 [LOCK + RECHECK]                     ← Phase 6 concurrency safety
 Acquire PESSIMISTIC_WRITE on partner row
 Re-read fresh state
 Re-check: isActive? capacity still sufficient?
 If yes → assign. If no → try next candidate.
     │
     ▼
 [PERSIST]
 DeliveryAssignment (attempt N, status=ASSIGNED)
 AssignmentDecision (winner scores + weight snapshot)
 AssignmentDecisionCandidate (one row per evaluated partner)
```

### ETA calculation

```
Working window: WORKING_HOUR_START (10)  to  WORKING_HOUR_END (20)
Travel speed:   2 min/km (fixed technical constant, not a business rule)

Free partner:
  workStart = now  (or 10AM tomorrow if currently past end-of-day)
  If now >= LATE_ASSIGNMENT_HOUR AND distToVendor > HOME_VENDOR_MAX_DISTANCE:
    workStart = 10AM tomorrow
  eta = scheduleWithinWorkingHours(workStart, totalDistanceKm × 2min/km)
        spilling into next day(s) if travel exceeds today's remaining window

Busy partner (cross-vendor reuse):
  restReadyTime = currentAssignment.expectedDeliveryTime + REST_DURATION_MINUTES
  workStart     = snap restReadyTime to valid working window (same rules as above)
  eta           = scheduleWithinWorkingHours(workStart, totalDistance × 2min/km)
```

### Scoring normalisation formulas

| Method | Formula | Used for |
|---|---|---|
| `INVERSE_LINEAR` | `100 × (1 − clamp((raw − min) / (max − min)))` | DISTANCE |
| `LINEAR` | `100 × clamp((raw − min) / (max − min))` | CAPACITY, IDLE_TIME |
| `NORMALIZED` | `100 × clamp((raw − min) / (max − min))` | RATING (min=1 shifts the scale) |
| `BINARY` | `positiveScore if raw > 0 else negativeScore` (both from DB) | SAME_VENDOR |

All formulas clamp output to [0, 100].

### Adding a new scoring factor

1. Add a constant to `AssignmentScoreFactor` enum
2. Create a `@Component` implementing `AssignmentScoringStrategy`
3. Add a seed row in `DataInitializer`
4. Add validation in `AssignmentScoringRuleServiceImpl.validateFields()`

No changes needed to `AssignmentScoringEngineImpl` — it discovers strategies dynamically via `List<AssignmentScoringStrategy>` injection.

### Reassignment flow

```
Partner rejects / times out / delivery fails
     │
     ▼
AssignmentLifecycleService
  (ASSIGNED/ACCEPTED/IN_TRANSIT → REJECTED/EXPIRED/DELIVERY_FAILED)
  AssignmentClosingHelper.closeAttempt() — history + weight restore
  status → REASSIGNING
     │
     ▼
AssignmentReassignmentService
  Collect all previously-failed partner IDs for this order
  Check totalAttempts < MAX_ASSIGNMENT_ATTEMPTS
  Build AssignmentContext(order, excludedIds, attempt+1, reason)
  Call DeliveryAssignmentService.assign(order, context)
     │
     ├── Partner found → new DeliveryAssignment (attempt N+1)
     └── No partner  → WAITING_FOR_PARTNER (manual intervention needed)

AssignmentExpiryService (@Scheduled every 60s):
  Queries: status=ASSIGNED AND assigned_at <= now - ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES
  Calls lifecycle.expireAssignment() for each → triggers reassignment chain
```

---

## API reference

All endpoints respond with `ApiResponse<T>` envelope:

```json
{
  "success": true,
  "message": "optional message",
  "data": { ... },
  "timestamp": "2026-10-01T10:30:00"
}
```

### Core endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register user (CUSTOMER / DELIVERY_PARTNER / VENDOR / ADMIN) |
| POST | `/api/auth/login` | Authenticate, receive JWT |
| POST | `/api/orders?customerId={id}` | Place order → auto-assigns partner immediately |
| GET | `/api/assignments/{id}` | Get assignment by ID |
| PATCH | `/api/assignments/{id}/status?newStatus=ACCEPTED` | Advance delivery status |
| POST | `/api/assignments/{id}/reassign?reason=...` | Manual admin reassignment |
| GET | `/api/users/leaderboard` | Partner leaderboard (by rating, then completed deliveries) |
| GET | `/api/dashboard/stats` | Admin system snapshot |

### Admin rule management

| Method | Path | Example body |
|---|---|---|
| GET | `/api/v1/assignment-rules` | — |
| PUT | `/api/v1/assignment-rules/HOME_VENDOR_MAX_DISTANCE` | `{"value": "40"}` |
| GET | `/api/v1/scoring-rules` | — |
| PUT | `/api/v1/scoring-rules/DISTANCE` | `{"weight": 50, "maxValue": 60.0}` |

After a PUT, the Redis cache for that key is evicted immediately. The next read repopulates from MySQL. No restart needed.

### Delivery status state machine

```
ASSIGNED → ACCEPTED → PICKED_UP → IN_TRANSIT → DELIVERED
    │            │                      │
    ├─ REJECTED  └─ CANCELLED           └─ DELIVERY_FAILED
    ├─ EXPIRED              ↓                     ↓
    └─ CANCELLED        (terminal)          REASSIGNING → new ASSIGNED attempt
                                                           └─ WAITING_FOR_PARTNER
```

---

## Phase roadmap

| Phase | What was built |
|---|---|
| **1** | DB-driven business rules (assignment_rules table, AssignmentRuleService, admin API) |
| **2** | DB-driven scoring configuration (assignment_scoring_rules, AssignmentScoringRuleService) |
| **3** | Strategy Pattern scoring engine (5 strategies, AssignmentScoringEngine, ScoringResult, AssignmentDecision) |
| **4** | Scoring formulas (INVERSE_LINEAR/LINEAR/NORMALIZED/BINARY with configurable min/max/positiveScore/negativeScore) |
| **5** | Full integration — AssignmentCandidate, EligibilityResult, per-candidate audit (AssignmentDecisionCandidate) |
| **6** | Concurrency safety — pessimistic lock + re-check, idempotency guard, @Version optimistic lock on UserDetails |
| **7** | Automatic reassignment — state machine (AssignmentLifecycleService), AssignmentContext with exclusion list, expiry scheduler, WAITING_FOR_PARTNER state, MAX_ASSIGNMENT_ATTEMPTS |
| **8** | Redis cache-aside — RuleCacheService, cache invalidation on every admin write, TTL safety backstop |

---

## Design decisions

### Why not Spring Cache / @Cacheable?

`@Cacheable` would have worked for the happy path, but RouteAssign needs explicit control over **when** cache entries are evicted. After an admin updates a scoring weight via `PUT /api/v1/scoring-rules/DISTANCE`, the Redis key must be evicted synchronously within the same transaction (after the MySQL commit, before the HTTP response). `@Cacheable` eviction with `@CacheEvict` works, but the `RuleCacheService` approach keeps the eviction logic visible in the service method where the write happens, rather than scattered across annotations.

### Why pessimistic locking for partner selection?

The problem: two concurrent requests both score Partner A as the best candidate, both find her capacity sufficient, and both try to assign. The second assignment would push her total weight past her capacity limit.

Optimistic locking (`@Version` on `UserDetails`) catches this at commit time and throws `OptimisticLockException` — but then the caller has to retry the entire selection algorithm. Instead, RouteAssign uses a **pessimistic write lock on a single row** (SELECT … FOR UPDATE on the chosen partner) and re-validates capacity with fresh data before committing. The lock window is small: only the one selected partner row, held only for the duration of the `attemptAssignment` critical section.

`@Version` is kept on `UserDetails` as a second layer of defence. Both exist and work together.

### Why Strategy Pattern for scoring?

The five scoring dimensions (distance, capacity, rating, idle time, same-vendor) have different formulas, different measurement sources, and different normalisation parameters. Putting all five formulas into a single `ScoringEngineServiceImpl` with a switch statement would make the engine hard to extend and hard to test in isolation.

The Strategy Pattern lets each factor live in its own class (`DistanceScoringStrategy`, `CapacityScoringStrategy`, etc.) that can be reasoned about, tested, and modified independently. Adding a sixth factor requires creating one new `@Component` and a seed row — no changes to the engine.

### Why does the engine score all eligible candidates (not just an ETA shortlist)?

An earlier version shortlisted candidates to those within 1 minute of the fastest ETA, then scored only that subset. This meant that a partner with a 5-minute-later ETA but a significantly better composite score (closer location, higher rating, same vendor) would be invisible to the engine.

Phase 5 removed the ETA pre-filter. The engine scores every eligible candidate and selects the highest composite score. ETA is now one of several factors the score reflects (via DISTANCE, which correlates strongly with ETA), not a hard pre-filter gate.

### Why multiple `DeliveryAssignment` rows per order instead of updating one row?

Overwriting the `partner_id` when reassigning destroys the evidence that Partner A was ever assigned. The multi-attempt design means:

```
order_id=101, attempt=1, partner=25, status=REJECTED
order_id=101, attempt=2, partner=31, status=ACCEPTED → DELIVERED
```

The full history is preserved, `AssignmentDecisionCandidate` records show every scored candidate at each attempt, and the `failureReason` column explains why each attempt ended. This turns the assignment system into a transparent audit log, not just a routing table.

### Why MySQL as the source of truth for assignment rules, not environment variables or `application.properties`?

Environment variables require a restart to change. `application.properties` requires a redeploy. For a delivery platform, rules like the acceptance timeout and working hours need to change without any deployment — a business decision (e.g. "extend working hours for a holiday") should not require an engineering team action.

MySQL + admin API gives operations teams full control. Redis caching ensures this does not come at a performance cost.

---

## Interview talking points

**"Walk me through how an order gets assigned."**

When a customer places an order, `OrderServiceImpl` persists it and calls `DeliveryAssignmentService.assign()`. That method first checks if an active assignment already exists for idempotency, then calls `PartnerSelectionAlgorithmService.selectPartnerWithEta()` with an `AssignmentContext`. The selection service runs eligibility checks, builds `AssignmentCandidate` objects for each eligible partner with pre-computed measurements, scores all candidates via the `AssignmentScoringEngine` (which dispatches to five strategy implementations), and picks the winner. Back in `DeliveryAssignmentService`, the top candidate's partner row gets a pessimistic write lock, eligibility is re-checked with fresh data, and — if still valid — the assignment is persisted. The full scoring breakdown is saved in `AssignmentDecision` for auditing.

**"How do you handle concurrent orders trying to assign the same partner?"**

Three layers. First, an idempotency guard on the order ID prevents the same order from being assigned twice. Second, a pessimistic write lock (`SELECT ... FOR UPDATE`) on the selected partner row serialises concurrent writes. Third, after acquiring the lock, the service re-reads the partner's current `currentAssignedWeight` and rechecks `freshRemaining >= order.totalWeight`. If another request already consumed the capacity, this request falls back to the next-highest-scored candidate. `@Version` on `UserDetails` is a fourth layer that catches any edge case the lock missed.

**"How are the scoring weights managed?"**

They live in the `assignment_scoring_rules` MySQL table, not in Java code. `AssignmentScoringRuleService.getEnabledScoringConfigs()` reads them — from Redis on a cache hit, from MySQL on a miss. When an admin calls `PUT /api/v1/scoring-rules/DISTANCE` with a new weight, the service validates the change, saves to MySQL, and evicts the Redis key. The next scoring call repopulates from MySQL. No restart. No redeploy.

**"Why did you use the Strategy Pattern for scoring instead of a simple switch statement?"**

A switch on `AssignmentScoreFactor` inside the engine works when there are five factors. Once a product manager asks for six, you modify the engine. With the Strategy Pattern, adding a factor is: create one new `@Component`, add a seed row, add a validation branch. The engine discovers all strategies via Spring injection of `List<AssignmentScoringStrategy>` and never needs to be touched. Each strategy is independently testable with just a `CandidateContext` and a `ScoringRuleConfig`.

**"How does reassignment work?"**

When a partner rejects or times out, `AssignmentLifecycleService` transitions the assignment status, calls `AssignmentClosingHelper` to write history records and restore the partner's availability, then triggers `AssignmentReassignmentService`. The reassignment service builds a new `AssignmentContext` that includes the failed partner's ID in `excludedPartnerIds` and increments the attempt number. It checks `MAX_ASSIGNMENT_ATTEMPTS` (configurable in DB), then calls the exact same `DeliveryAssignmentService.assign()` with the new context. If no partner is found, the order moves to `WAITING_FOR_PARTNER`. The expiry scheduler (`@Scheduled`) runs every 60 seconds and finds `ASSIGNED` rows past the `ASSIGNMENT_ACCEPTANCE_TIMEOUT_MINUTES` threshold, triggering the same chain for timed-out cases.

**"What's your Redis strategy?"**

Cache-aside, config-only. The assignment rules and scoring rules are read on every order assignment, so caching them avoids repeated MySQL round-trips. The cache key is `routeassign:rule:{KEY}` or `routeassign:scoring:{FACTOR}`. On every admin write, the key is evicted immediately (not on expiry). A 5-minute TTL is a safety backstop — if an eviction somehow fails, the stale value expires and the next read repopulates. Redis never holds core business data like orders or assignments. MySQL is always the source of truth.

**"What would you add next?"**

Testcontainers-based integration tests covering the full assignment flow, concurrent order tests (two threads, same partner, assert only one assignment succeeds), and Redis failure tests (mock Redis as unavailable, assert MySQL fallback works). After that: partner location streaming via Redis GEO commands for faster candidate discovery, and distributed locking (Redisson) to protect the lock-recheck step when running multiple application instances.
