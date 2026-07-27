# Muigo Wallet API - Complete Source Code Documentation

**Generated:** 2026-03-03  
**Project:** muigo-wallet v1.0.0  
**Language:** Java 21 | **Framework:** Spring Boot 3.5.14  
**Database:** PostgreSQL | **Build Tool:** Maven

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture Diagram](#architecture-diagram)
3. [Directory Structure](#directory-structure)
4. [Core Components](#core-components)
5. [Models & Entities](#models--entities)
6. [DTOs](#dtos)
7. [Repositories](#repositories)
8. [Services](#services)
9. [Controllers & REST API](#controllers--rest-api)
10. [Security Layer](#security-layer)
11. [Exception Handling](#exception-handling)
12. [Database Schema](#database-schema)
13. [Concurrency & Safety](#concurrency--safety)
14. [Request Flow Examples](#request-flow-examples)
15. [Configuration](#configuration)
16. [Architectural Patterns](#architectural-patterns)

---

## Project Overview

This is a **production-grade Spring Boot digital wallet API** built with security, performance, and compliance in mind. It provides comprehensive financial transaction management with:

- ✅ User registration and JWT authentication
- ✅ Wallet creation and management
- ✅ Atomic fund transfers with deadlock prevention
- ✅ Immutable transaction ledger for auditing
- ✅ Pessimistic and optimistic locking for concurrency
- ✅ OpenAPI/Swagger documentation
- ✅ Comprehensive error handling (RFC 7807)
- ✅ Prometheus metrics for observability

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  REST API Layer                          │
│            (AuthController, WalletController)            │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Security Layer (JWT)                        │
│  (JwtAuthFilter, JwtService, UserDetailsServiceImpl)    │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│         Business Logic Layer (Services)                  │
│    (AuthService, WalletService, Transaction Logic)      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│          Data Access Layer (Repositories)               │
│  (WalletRepository, UserRepository, TransactionRepo)   │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│       Database Layer (PostgreSQL)                       │
│    (app_users, wallets, transactions tables)           │
└─────────────────────────────────────────────────────────┘
```

---

## Directory Structure

```
src/main/
├── java/com/muigo/wallet/
│   ├── WalletApplication.java             Main entry point
│   │
│   ├── config/
│   │   ├── SecurityConfig.java            Spring Security configuration
│   │   └── OpenApiConfig.java             Swagger/OpenAPI setup
│   │
│   ├── controllers/
│   │   ├── AuthController.java            Authentication endpoints
│   │   └── WalletController.java          Wallet operation endpoints
│   │
│   ├── services/
│   │   ├── AuthService.java               User auth business logic
│   │   └── WalletService.java             Wallet & transfer logic
│   │
│   ├── models/
│   │   ├── AppUser.java                   User entity
│   │   ├── Wallet.java                    Wallet entity
│   │   └── Transaction.java               Transaction ledger entity
│   │
│   ├── repositories/
│   │   ├── UserRepository.java            User data access
│   │   ├── WalletRepository.java          Wallet data access
│   │   └── TransactionRepository.java     Transaction data access
│   │
│   ├── dtos/
│   │   └── WalletDtos.java                All request/response DTOs
│   │
│   ├── exceptions/
│   │   ├── WalletExceptions.java          Custom exceptions
│   │   └── GlobalExceptionHandler.java    Centralized error handling
│   │
│   └── security/
│       ├── JwtService.java                JWT token management
│       ├── JwtAuthFilter.java             JWT request filter
│       └── UserDetailsServiceImpl.java     User loading for auth
│
└── resources/
    ├── application.properties              Configuration & secrets
    └── db/migration/
        └── V1__initial_schema.sql         Database schema (Flyway)
```

---

## Core Components

### 1. WalletApplication.java

The entry point for the entire Spring Boot application:

```java
@SpringBootApplication
public class WalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
```

**What it does:**
- Bootstraps the Spring context
- Enables component scanning
- Activates auto-configuration
- Loads application properties

---

## Models & Entities

### AppUser.java - User/Authentication Entity

**Purpose:** Represents an application user with authentication credentials

**Key Fields:**
| Field | Type | Constraints | Purpose |
|-------|------|-----------|---------|
| `id` | String (UUID) | AUTO-GENERATED | Unique user identifier |
| `email` | String | UNIQUE, NOT NULL | Login username & contact |
| `password` | String | NOT NULL | BCrypt-hashed password |
| `fullName` | String | NOT NULL | User's full name |
| `role` | Enum | DEFAULT: USER | User role (USER or ADMIN) |
| `createdAt` | LocalDateTime | AUTO-POPULATED | Registration timestamp |

**Special Features:**
- ✅ Implements Spring Security's `UserDetails` interface
- ✅ Can be used directly in authentication without conversion
- ✅ Provides authorities: `ROLE_USER` or `ROLE_ADMIN`
- ✅ Automatically populates `createdAt` via JPA auditing

**Implementation:**
```java
@Entity
@Table(name = "app_users")
@Data
public class AppUser implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String fullName;
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

---

### Wallet.java - Financial Account Entity

**Purpose:** Represents a digital wallet with a balance

**Key Fields:**
| Field | Type | Constraints | Purpose |
|-------|------|-----------|---------|
| `id` | String (UUID) | AUTO-GENERATED | Unique wallet ID |
| `ownerName` | String | NOT NULL | Wallet owner's name |
| `balance` | BigDecimal | NUMERIC(19,4), DEFAULT: 0 | Current wallet balance |
| `version` | Long | AUTO-MANAGED | Optimistic locking counter |
| `createdAt` | LocalDateTime | NOT NULL, IMMUTABLE | Creation timestamp |
| `updatedAt` | LocalDateTime | NOT NULL, UPDATED | Last modification time |

**Critical Design Decision - Optimistic Locking:**

The `@Version` field implements optimistic locking:
- Each wallet has a version number (starts at 0)
- When you modify a wallet, Hibernate checks if the version matches
- If another thread modified it in between, the update fails with `OptimisticLockException`
- This prevents "lost updates" in concurrent deposit/withdrawal scenarios

**Why BigDecimal?**
- ❌ **Never use `double` or `float` for money** (precision loss)
- ✅ `BigDecimal` provides exact decimal arithmetic
- ✅ NUMERIC(19,4) = 15 digits before decimal + 4 after decimal

**Implementation:**
```java
@Entity
@Table(name = "wallets")
@Data
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String ownerName;
    
    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Version
    private Long version;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

---

### Transaction.java - Immutable Ledger Record

**Purpose:** Append-only audit log of all financial events

**Key Fields:**
| Field | Type | Purpose |
|-------|------|---------|
| `id` | String (UUID) | Unique transaction ID |
| `walletId` | String | Which wallet was affected |
| `referenceId` | String | Links both legs of a transfer |
| `type` | Enum | DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT |
| `amount` | BigDecimal | Transaction amount |
| `balanceAfter` | BigDecimal | Wallet balance snapshot after txn |
| `description` | String | Optional memo |
| `createdAt` | LocalDateTime | Immutable creation timestamp |

**Design Pattern:**
- ✅ Transactions are **NEVER** updated or deleted
- ✅ Pure append-only pattern for compliance
- ✅ `balanceAfter` captures wallet state for auditing
- ✅ Transfers create **TWO** transactions with same `referenceId`

**Implementation:**
```java
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_wallet_id", columnList = "walletId"),
    @Index(name = "idx_transaction_created_at", columnList = "createdAt")
})
@Data
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String walletId;
    
    private String referenceId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;
    
    private String description;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

---

## DTOs

### Data Transfer Objects (WalletDtos.java)

DTOs are the contract between the API and clients. They are separate from entities for security and flexibility.

#### Request DTOs

**RegisterRequest**
```
Fields:
  • email: String (required, must be valid email)
  • password: String (required, minimum 8 characters)
  • fullName: String (required)
```

**LoginRequest**
```
Fields:
  • email: String (required, valid email)
  • password: String (required)
```

**CreateWalletRequest**
```
Fields:
  • ownerName: String (2-100 chars, required)
```

**DepositRequest**
```
Fields:
  • walletId: String (required)
  • amount: BigDecimal (≥0.01, required)
  • description: String (optional)
```

**WithdrawRequest**
```
Fields:
  • walletId: String (required)
  • amount: BigDecimal (≥0.01, required)
  • description: String (optional)
```

**TransferRequest**
```
Fields:
  • fromWalletId: String (required)
  • toWalletId: String (required)
  • amount: BigDecimal (≥0.01, required)
  • description: String (optional)
```

#### Response DTOs

**AuthResponse**
```
Fields:
  • token: String (JWT bearer token)
  • tokenType: String ("Bearer")
  • userId: String (UUID)
  • email: String
```

**WalletResponse**
```
Fields:
  • id: String (UUID)
  • ownerName: String
  • balance: BigDecimal
  • createdAt: LocalDateTime
  • updatedAt: LocalDateTime
```

**TransactionResponse**
```
Fields:
  • id: String (UUID)
  • walletId: String
  • referenceId: String
  • type: TransactionType
  • amount: BigDecimal
  • balanceAfter: BigDecimal
  • description: String
  • createdAt: LocalDateTime
```

**TransferResponse**
```
Fields:
  • referenceId: String (links both transaction records)
  • amount: BigDecimal
  • fromWallet: WalletResponse
  • toWallet: WalletResponse
  • timestamp: LocalDateTime
```

**Key Principle:** Request DTOs use Bean Validation annotations to validate input **before** business logic executes. The controller never receives invalid data.

---

## Repositories

Data access layer using Spring Data JPA.

### WalletRepository.java

```java
@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(String id);
}
```

**Methods:**
- `save(Wallet)` - Insert/update wallet (inherited from JpaRepository)
- `findById(String)` - Fetch wallet by ID (inherited)
- `findByIdWithLock(String)` - **Custom**: Fetch wallet with DB-level pessimistic lock

**Pessimistic Locking:**
- Acquires exclusive row lock at database level
- Other threads/transactions must wait
- Prevents concurrent modification during transfers
- Sorted lock acquisition prevents deadlocks

### UserRepository.java

```java
@Repository
public interface UserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

**Custom Methods:**
- `findByEmail(String)` - Find user by email for login
- `existsByEmail(String)` - Check if email already registered

### TransactionRepository.java

```java
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(
        String walletId, 
        Pageable pageable
    );
}
```

**Custom Method:**
- `findByWalletIdOrderByCreatedAtDesc()` - Get paginated transaction history, sorted newest first

---

## Services

Business logic layer - where all domain rules are enforced.

### AuthService.java - Authentication Logic

**Purpose:** Handle user registration and login

**Methods:**

#### register(RegisterRequest) → AuthResponse

```
Steps:
  1. Check if email already registered (prevent duplicates)
  2. Hash password using BCrypt
  3. Create new AppUser entity
  4. Save to database
  5. Generate JWT token
  6. Return AuthResponse with token

Throws:
  • IllegalArgumentException if email already exists
```

#### login(LoginRequest) → AuthResponse

```
Steps:
  1. Authenticate via Spring's AuthenticationManager
     (runs DaoAuthenticationProvider which checks password)
  2. Retrieve user from database by email
  3. Generate new JWT token
  4. Return AuthResponse with token

Throws:
  • BadCredentialsException if password wrong
  • UsernameNotFoundException if email not found
```

---

### WalletService.java - Core Financial Logic

**Purpose:** Implement all wallet operations with transactional safety

**Key Principle:** All write methods are `@Transactional`:
- If ANY step fails, the ENTIRE operation rolls back
- No half-updated state
- Prevents corruption of financial data

#### createWallet(CreateWalletRequest) → WalletResponse

```
Steps:
  1. Create new Wallet with balance = 0
  2. Save to database
  3. Return WalletResponse

Side Effects:
  • Wallet created with given owner name
```

#### deposit(DepositRequest) → WalletResponse

```
Steps:
  1. Validate amount > 0
  2. Fetch wallet by ID (throws WalletNotFoundException if not found)
  3. Add amount to balance
  4. Save updated wallet
  5. Record transaction with type=DEPOSIT
  6. Return updated wallet

Throws:
  • WalletNotFoundException
  • InvalidAmountException if amount ≤ 0
```

#### withdraw(WithdrawRequest) → WalletResponse

```
Steps:
  1. Validate amount > 0
  2. Fetch wallet by ID
  3. Check balance >= amount (throw InsufficientFundsException if not)
  4. Subtract amount from balance
  5. Save updated wallet
  6. Record transaction with type=WITHDRAWAL
  7. Return updated wallet

Throws:
  • WalletNotFoundException
  • InsufficientFundsException
  • InvalidAmountException
```

#### transfer(TransferRequest) → TransferResponse

**Atomic transfer between two wallets with deadlock prevention**

```
Steps:
  1. Validate amount > 0
  2. Check fromWalletId ≠ toWalletId
  3. Sort wallet IDs alphabetically (CRITICAL for deadlock prevention)
  4. Lock BOTH wallets in sorted order using pessimistic locking
  5. Verify sender has sufficient balance
  6. Debit sender, credit receiver (atomically)
  7. Save both wallets
  8. Generate UUID for reference ID
  9. Create TRANSFER_DEBIT transaction for sender (references debit txn)
 10. Create TRANSFER_CREDIT transaction for receiver (same reference ID)
  11. Return TransferResponse with both wallet states

Throws:
  • WalletNotFoundException
  • InsufficientFundsException
  • InvalidAmountException

Concurrency Protection:
  ✅ Pessimistic locking prevents concurrent modifications
  ✅ ID sorting prevents deadlock:
     - Thread A: Lock wallet-1, then wallet-2
     - Thread B: Also locks wallet-1 first (alphabetically sorted)
     - Never hold locks in opposite order → no deadlock
```

#### getWallet(String walletId) → WalletResponse

```
Read-only operation:
  1. Fetch wallet by ID
  2. Convert to WalletResponse
  3. Return current state

Throws:
  • WalletNotFoundException
```

#### getTransactionHistory(String walletId, Pageable) → Page<TransactionResponse>

```
Read-only paginated query:
  1. Verify wallet exists
  2. Query transactions for wallet
  3. Sort by createdAt descending (newest first)
  4. Apply pagination
  5. Convert to TransactionResponses
  6. Return page

Throws:
  • WalletNotFoundException

Pagination:
  • Default page size: 20 items
  • Can be customized via query params
```

---

## Controllers & REST API

### AuthController.java

```
Base Path: /api/auth
```

| HTTP Method | Endpoint | Authentication | Request | Response | Status |
|------------|----------|-----------------|---------|----------|--------|
| POST | `/register` | ❌ Public | RegisterRequest | ApiResponse<AuthResponse> | 201 Created |
| POST | `/login` | ❌ Public | LoginRequest | ApiResponse<AuthResponse> | 200 OK |

**Example: Register**
```
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123",
  "fullName": "John Doe"
}

Response (201):
{
  "status": "success",
  "message": "Registration successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com"
  }
}
```

**Example: Login**
```
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123"
}

Response (200):
{
  "status": "success",
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com"
  }
}
```

---

### WalletController.java

```
Base Path: /api/v1/wallets
Authentication: ✅ JWT Bearer Token Required (except where noted)
```

| HTTP Method | Endpoint | Purpose | Request | Response |
|------------|----------|---------|---------|----------|
| POST | `/` | Create wallet | CreateWalletRequest | ApiResponse<WalletResponse> |
| GET | `/{walletId}` | Get wallet details | - | ApiResponse<WalletResponse> |
| POST | `/deposit` | Add funds | DepositRequest | ApiResponse<WalletResponse> |
| POST | `/withdraw` | Remove funds | WithdrawRequest | ApiResponse<WalletResponse> |
| POST | `/transfer` | Transfer between wallets | TransferRequest | ApiResponse<TransferResponse> |
| GET | `/{walletId}/transactions` | Get history (paginated) | Query: page, size | ApiResponse<Page<TransactionResponse>> |

**Example: Create Wallet**
```
POST /api/v1/wallets
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "ownerName": "Alice Smith"
}

Response (201):
{
  "status": "success",
  "message": "Wallet created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "ownerName": "Alice Smith",
    "balance": 0.0000,
    "createdAt": "2026-07-03T10:00:00",
    "updatedAt": "2026-07-03T10:00:00"
  }
}
```

**Example: Deposit**
```
POST /api/v1/wallets/deposit
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "walletId": "550e8400-e29b-41d4-a716-446655440001",
  "amount": 100.50,
  "description": "Monthly salary"
}

Response (200):
{
  "status": "success",
  "message": "Deposit successful",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "ownerName": "Alice Smith",
    "balance": 100.5000,
    "createdAt": "2026-07-03T10:00:00",
    "updatedAt": "2026-07-03T10:05:00"
  }
}
```

**Example: Transfer**
```
POST /api/v1/wallets/transfer
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "fromWalletId": "550e8400-e29b-41d4-a716-446655440001",
  "toWalletId": "550e8400-e29b-41d4-a716-446655440002",
  "amount": 25.00,
  "description": "Payment for services"
}

Response (200):
{
  "status": "success",
  "message": "Transfer successful",
  "data": {
    "referenceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
    "amount": 25.00,
    "fromWallet": {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "ownerName": "Alice Smith",
      "balance": 75.5000,
      "createdAt": "2026-07-03T10:00:00",
      "updatedAt": "2026-07-03T10:10:00"
    },
    "toWallet": {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "ownerName": "Bob Johnson",
      "balance": 125.0000,
      "createdAt": "2026-07-03T10:00:00",
      "updatedAt": "2026-07-03T10:10:00"
    },
    "timestamp": "2026-07-03T10:10:00"
  }
}
```

**Example: Get Transaction History**
```
GET /api/v1/wallets/550e8400-e29b-41d4-a716-446655440001/transactions?page=0&size=10
Authorization: Bearer <JWT_TOKEN>

Response (200):
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": "tx-001",
        "walletId": "550e8400-e29b-41d4-a716-446655440001",
        "referenceId": null,
        "type": "DEPOSIT",
        "amount": 100.5000,
        "balanceAfter": 100.5000,
        "description": "Monthly salary",
        "createdAt": "2026-07-03T10:05:00"
      },
      {
        "id": "tx-002",
        "walletId": "550e8400-e29b-41d4-a716-446655440001",
        "referenceId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
        "type": "TRANSFER_DEBIT",
        "amount": 25.0000,
        "balanceAfter": 75.5000,
        "description": "Payment for services",
        "createdAt": "2026-07-03T10:10:00"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "currentPage": 0,
    "size": 10
  }
}
```

---

## Security Layer

### JwtService.java - Token Management

**Purpose:** Generate, validate, and extract information from JWT tokens

**Key Methods:**

#### generateToken(UserDetails) → String

```
Steps:
  1. Create JWT builder
  2. Set subject to username (email)
  3. Add issued-at timestamp
  4. Add expiration time (24 hours by default)
  5. Sign with HMAC-SHA using secret key from environment
  6. Return compact encoded token string

Token Structure:
  Header.Payload.Signature
  
Example Token:
  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
  eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNjg4MzA0MDAwLCJleHAiOjE2ODgzOTA0MDB9.
  3KM8N5pQ7vL2xY9zU1aB4cD6eF8gH0iJ2kL4mN6oP8
```

#### isTokenValid(String token, UserDetails userDetails) → boolean

```
Steps:
  1. Extract claims from token (verifies signature in process)
  2. Extract username/email from claims
  3. Verify username matches UserDetails
  4. Check token not expired
  5. Return true if all checks pass

Returns:
  • true: Token is valid and user is authentic
  • false: Token invalid, expired, or signature mismatch
```

#### extractUsername(String token) → String

```
Steps:
  1. Parse and verify token signature
  2. Extract JWT subject claim
  3. Return email/username

Used by:
  • JwtAuthFilter to identify which user authenticated
```

**Configuration:**
```properties
app.jwt.secret=${JWT_SECRET}                    # From environment
app.jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}  # 24 hours default
```

---

### JwtAuthFilter.java - Request Filter

**Purpose:** Intercept HTTP requests and validate JWT tokens

**Inheritance:** `OncePerRequestFilter` ensures it runs exactly once per request

**Processing Steps:**

```
For each incoming request:

1. Check Authorization header
   • Look for: Authorization: Bearer <token>
   • If missing or wrong format → skip JWT processing
   • Proceed to next filter

2. Extract JWT token
   • Remove "Bearer " prefix (7 characters)
   • Token string remains

3. Load user details
   • Extract email from token
   • Query UserRepository by email
   • Get AppUser (implements UserDetails)

4. Validate token
   • Check signature (HMAC-SHA verification)
   • Check expiration time
   • Verify email matches

5. Set authentication
   • Create UsernamePasswordAuthenticationToken
   • Add UserDetails and authorities
   • Store in SecurityContextHolder
   • Mark request as authenticated

6. Continue filter chain
   • Pass to next filter/controller
   • Controller receives authenticated user
```

**Code Flow:**
```java
@Override
protected void doFilterInternal(...) throws ServletException, IOException {
    // 1. Extract token from header
    final String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);  // Skip JWT processing
        return;
    }
    
    // 2. Parse token
    final String jwt = authHeader.substring(7);
    final String userEmail = jwtService.extractUsername(jwt);
    
    // 3. Load and validate user
    if (userEmail != null && SecurityContextHolder.getContext()
        .getAuthentication() == null) {
        
        UserDetails userDetails = userDetailsService
            .loadUserByUsername(userEmail);
        
        if (jwtService.isTokenValid(jwt, userDetails)) {
            // 4. Set authentication
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
                );
            authToken.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext()
                .setAuthentication(authToken);
        }
    }
    
    // 5. Continue
    filterChain.doFilter(request, response);
}
```

---

### UserDetailsServiceImpl.java - User Loading

**Purpose:** Load user details from database for authentication

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String email) 
        throws UsernameNotFoundException {
        
        return userRepository.findByEmail(email)
            .orElseThrow(() -> 
                new UsernameNotFoundException("User not found: " + email)
            );
    }
}
```

**Used by:**
- Spring's DaoAuthenticationProvider during login
- JwtAuthFilter when validating tokens

**Throws:**
- `UsernameNotFoundException` if email not found in database

---

### SecurityConfig.java - Spring Security Configuration

**Purpose:** Configure Spring Security for the application

**Key Configurations:**

#### 1. SecurityFilterChain - HTTP Security

```
Authorization rules:
  ✅ /api/auth/**           → Permit all (public endpoints)
  ✅ /actuator/health       → Permit all (health check)
  ✅ /actuator/prometheus   → Permit all (metrics)
  ✅ /swagger-ui/**         → Permit all (API docs)
  ❌ All other endpoints    → Require authentication
```

#### 2. Session Management

```
Policy: STATELESS
  • No server-side sessions
  • Each request must have JWT token
  • Suitable for REST APIs and mobile clients
  • No session cookies
```

#### 3. CSRF Protection

```
csrf().disable()
  • CSRF disabled (OK for stateless REST API)
  • Would only apply to session-based apps anyway
```

#### 4. Authentication Provider

```
DaoAuthenticationProvider:
  • Uses UserDetailsServiceImpl to load users
  • Uses BCryptPasswordEncoder to verify passwords
  • Called during login process
```

#### 5. JWT Filter Integration

```
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
  • Runs JWT filter BEFORE standard username/password filter
  • JWT filter runs on every request (validates token)
  • Sets SecurityContext before controller is called
```

**Configuration Code:**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) 
        throws Exception {
        
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", 
                                "/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**", 
                                "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(s -> 
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, 
                UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = 
            new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
        
        return config.getAuthenticationManager();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## Exception Handling

### WalletExceptions.java - Custom Exceptions

**Purpose:** Domain-specific exceptions for wallet operations

```java
public class WalletExceptions {
    
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException(String walletId) {
            super("Wallet not found: " + walletId);
        }
    }
    
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String walletId) {
            super("Insufficient funds in wallet: " + walletId);
        }
    }
    
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidAmountException extends RuntimeException {
        public InvalidAmountException(String message) {
            super(message);
        }
    }
    
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ConcurrentModificationException 
        extends RuntimeException {
        
        public ConcurrentModificationException() {
            super("Wallet modified concurrently. Please retry.");
        }
    }
}
```

**Exception Hierarchy:**
- All extend `RuntimeException` (unchecked)
- `@ResponseStatus` annotation maps HTTP status code
- Caught by GlobalExceptionHandler

---

### GlobalExceptionHandler.java - Centralized Error Handling

**Purpose:** Convert all exceptions to RFC 7807 Problem Detail responses

**RFC 7807 Problem Details Format:**
```json
{
  "type": "https://api.muigo.com/errors/insufficient-funds",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Insufficient funds in wallet: wallet-123",
  "instance": "/api/v1/wallets/withdraw",
  "timestamp": "2026-07-03T10:30:00Z"
}
```

**Handled Exceptions:**

| Exception | Status | Error Type | Description |
|-----------|--------|-----------|-------------|
| WalletNotFoundException | 404 | wallet-not-found | Wallet doesn't exist |
| InsufficientFundsException | 422 | insufficient-funds | Not enough balance |
| InvalidAmountException | 400 | invalid-amount | Amount ≤ 0 or invalid |
| ObjectOptimisticLockingFailureException | 409 | concurrent-modification | Concurrent update conflict |
| MethodArgumentNotValidException | 400 | validation-error | Bean validation failed |
| ConstraintViolationException | 400 | validation-error | Constraint violated |
| Generic Exception | 500 | internal-error | Unexpected error |

**Implementation Example:**
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleNotFound(
        WalletNotFoundException ex) {
        
        return problem(
            HttpStatus.NOT_FOUND, 
            "wallet-not-found", 
            ex.getMessage()
        );
    }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(
        InsufficientFundsException ex) {
        
        return problem(
            HttpStatus.UNPROCESSABLE_ENTITY, 
            "insufficient-funds", 
            ex.getMessage()
        );
    }
    
    private ProblemDetail problem(
        HttpStatus status, String type, String detail) {
        
        ProblemDetail pd = ProblemDetail
            .forStatusAndDetail(status, detail);
        pd.setType(URI.create(
            "https://api.muigo.com/errors/" + type
        ));
        pd.setProperty("timestamp", 
            Instant.now().toString());
        return pd;
    }
}
```

**Advantages:**
✅ Consistent error format for all responses
✅ Clients know what to expect
✅ Easy to write error handling tests
✅ Compliant with RFC 7807 standard
✅ Self-describing error types

---

## Database Schema

### V1__initial_schema.sql - Flyway Migration

**Database:** PostgreSQL  
**Migration Tool:** Flyway (version-based, never modified)  

**Tables:**

#### app_users

```sql
CREATE TABLE app_users (
    id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

Indexes:
  • Primary Key: id
  • Unique: email
```

**Design Notes:**
- UUIDs as primary keys (globally unique)
- `email` unique and not null (username)
- `password` stored hashed (never plain text)
- `role` enum: USER or ADMIN
- `created_at` immutable (no updates)

#### wallets

```sql
CREATE TABLE wallets (
    id           VARCHAR(36)      PRIMARY KEY DEFAULT gen_random_uuid()::text,
    owner_name   VARCHAR(100)     NOT NULL,
    balance      NUMERIC(19, 4)   NOT NULL DEFAULT 0.0000,
    version      BIGINT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP        NOT NULL DEFAULT now(),
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0)
);

Indexes:
  • Primary Key: id

Constraints:
  • balance >= 0 (database-level enforcement)
```

**Design Notes:**
- `NUMERIC(19,4)` for precise decimal (15 integer + 4 decimal)
- Never `FLOAT` or `DOUBLE` for money!
- `version` field for optimistic locking
- `CHECK` constraint prevents negative balance at DB level
- Auto-timestamps for audit trail

#### transactions

```sql
CREATE TABLE transactions (
    id             VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid()::text,
    wallet_id      VARCHAR(36)    NOT NULL REFERENCES wallets(id),
    reference_id   VARCHAR(36),   -- Links two legs of transfer
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL,
    description    VARCHAR(255),
    created_at     TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT transactions_amount_positive CHECK (amount > 0)
);

Indexes:
  • Primary Key: id
  • idx_transactions_wallet_id (for wallet lookup)
  • idx_transactions_created_at DESC (for time-based queries)
  • idx_transactions_reference (for transfer linking)
```

**Design Notes:**
- `wallet_id` foreign key (referential integrity)
- `reference_id` NULL-able (only for transfers)
- Transfer = 2 transactions with same reference_id
- `amount > 0` enforced at DB level
- `balance_after` is snapshot of wallet balance after txn
- Append-only (no UPDATE/DELETE)
- Immutable `created_at`

---

## Concurrency & Safety

### Race Conditions & Solutions

| Scenario | Problem | Solution | Mechanism |
|----------|---------|----------|-----------|
| Two concurrent deposits | Lost update (both read balance, overwrite each other) | Optimistic locking | `@Version` field |
| Two concurrent transfers on same wallet | Double-spending or lost funds | Pessimistic locking | DB row-level lock |
| Transfer deadlock (A→B and B→A) | Threads deadlock waiting for locks | Ordered locking | Sort IDs before locking |
| Negative balance | User withdraws more than available | Atomicity + validation | `@Transactional` + check before update |
| Transaction loss | Partial operation (transfer debits but not credits) | Database transaction | `@Transactional` rollback |
| Corrupt audit trail | Missing or inconsistent transaction records | Immutable ledger | Append-only transactions table |

### Optimistic Locking (Deposit/Withdrawal)

**Scenario:** Two deposit requests on same wallet simultaneously

```
Thread A:
  1. Read wallet, version=5, balance=100
  2. Add 50: balance=150
  3. Save (UPDATE wallets SET balance=150, version=6 WHERE id=X AND version=5)

Thread B (concurrent):
  1. Read wallet, version=5, balance=100
  2. Add 30: balance=130
  3. Try save (UPDATE ... WHERE version=5)
     → Fails! Version changed to 6 by Thread A
     → OptimisticLockException thrown
     → Transaction rolled back
     → Client retries

Final state:
  • Balance=150 (correct: 100+50)
  • Thread B retries and reads 150
  • Thread B adds 30: 180 (correct: 150+30)
```

**Implementation:**
```java
@Entity
public class Wallet {
    @Version
    private Long version;  // Managed by Hibernate
}
```

---

### Pessimistic Locking (Transfers)

**Scenario:** Two transfers on same wallet simultaneously

```
Thread A (lock wallet-1, then wallet-2):
  1. Lock wallet-1 (wait if locked)
  2. Lock wallet-2 (wait if locked)
  3. Debit wallet-1, credit wallet-2
  4. Commit (releases both locks)

Thread B (lock wallet-1 first - alphabetical):
  1. Try lock wallet-1 (blocks, Thread A has it)
  2. Waits...
  3. Thread A completes, releases lock
  4. Thread B acquires lock, proceeds
```

**Implementation:**
```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdWithLock(String id);

// Service
Wallet from = walletRepository.findByIdWithLock(fromId)
    .orElseThrow(...);  // DB lock acquired here
```

---

### Deadlock Prevention (Transfer)

**Problem:** Naive implementation deadlocks

```
Thread A (transfer wallet-1 → wallet-2):
  1. Lock wallet-1
  2. Wait for wallet-2 (locked by Thread B)

Thread B (transfer wallet-2 → wallet-1):
  1. Lock wallet-2
  2. Wait for wallet-1 (locked by Thread A)

Result: Deadlock! Both threads waiting forever
```

**Solution: Ordered Locking**

```
Always lock in the same order (e.g., alphabetically):

Thread A (transfer wallet-1 → wallet-2):
  1. IDs sorted: [wallet-1, wallet-2]
  2. Lock wallet-1, then wallet-2
  3. Proceed

Thread B (transfer wallet-2 → wallet-1):
  1. IDs sorted: [wallet-1, wallet-2]  ← Same order!
  2. Lock wallet-1 (waits for Thread A)
  3. Lock wallet-2
  4. Proceed

Result: Linear ordering prevents deadlock
```

**Implementation:**
```java
// Sort IDs to ensure consistent locking order
String firstId = request.getFromWalletId()
    .compareTo(request.getToWalletId()) < 0
    ? request.getFromWalletId() 
    : request.getToWalletId();

String secondId = firstId.equals(request.getFromWalletId())
    ? request.getToWalletId() 
    : request.getFromWalletId();

// Always lock in sorted order
Wallet first = walletRepository.findByIdWithLock(firstId);
Wallet second = walletRepository.findByIdWithLock(secondId);

// Then identify sender and receiver
Wallet from = first.getId().equals(request.getFromWalletId()) 
    ? first : second;
Wallet to = first.getId().equals(request.getToWalletId()) 
    ? first : second;
```

---

## Request Flow Examples

### Example 1: User Registration

```
User Registration Flow
=======================

1. Client: POST /api/auth/register
   {
     "email": "alice@example.com",
     "password": "SecurePass123",
     "fullName": "Alice Smith"
   }

2. JwtAuthFilter
   • Checks Authorization header
   • None present (unauthenticated request allowed for /api/auth/**)
   • Passes through

3. AuthController.register()
   • Validates request with Bean Validation
     ✅ email is valid email
     ✅ password >= 8 chars
     ✅ fullName not blank

4. AuthService.register()
   @Transactional:
     a. Check userRepository.existsByEmail("alice@example.com")
        → false (doesn't exist)
     
     b. Hash password: "SecurePass123"
        → "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
        
     c. Create AppUser entity
        new AppUser(
          email="alice@example.com",
          password="$2a$10$N9qo8uLOickgx2ZMRZoMye...",
          fullName="Alice Smith",
          role=Role.USER
        )
        
     d. userRepository.save(user)
        → Inserts into app_users table
        → Returns saved user with auto-generated id
        
     e. jwtService.generateToken(user)
        → Creates JWT with subject="alice@example.com"
        → Expiration in 24 hours
        → Signs with HMAC-SHA
        → Returns token string
        
     f. Build AuthResponse
        token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        tokenType="Bearer",
        userId="550e8400-e29b-41d4-a716-446655440000",
        email="alice@example.com"

5. Wrapping & Response
   ApiResponse<AuthResponse>:
     status="success",
     message="Registration successful",
     data=AuthResponse(...)

6. HTTP Response: 201 Created
   {
     "status": "success",
     "message": "Registration successful",
     "data": {
       "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
       "tokenType": "Bearer",
       "userId": "550e8400-e29b-41d4-a716-446655440000",
       "email": "alice@example.com"
     }
   }

7. Client stores JWT token
   • Save to localStorage/sessionStorage
   • Use in Authorization header for future requests

=== Side Effects ===
✅ New row in app_users table
✅ User can now login with email+password
✅ User has ROLE_USER authority
✅ Account creation timestamp recorded
```

---

### Example 2: Authenticated Transfer

```
Transfer Flow (WITH CONCURRENCY PROTECTION)
==============================================

Preconditions:
  • User Alice has JWT token
  • Alice's wallet (wallet-1) has balance=1000
  • Bob's wallet (wallet-2) has balance=500
  • Alice requests: transfer 100 from wallet-1 to wallet-2

1. Client: POST /api/v1/wallets/transfer
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   {
     "fromWalletId": "wallet-1",
     "toWalletId": "wallet-2",
     "amount": 100.00,
     "description": "Payment"
   }

2. JwtAuthFilter
   a. Extract Authorization header
      → "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   
   b. Extract token (remove "Bearer " prefix)
      → "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   
   c. jwtService.extractUsername(token)
      → Parses JWT, verifies HMAC-SHA signature
      → Returns "alice@example.com"
   
   d. userDetailsService.loadUserByUsername("alice@example.com")
      → Queries userRepository
      → Returns AppUser(id=user-123, email=alice@..., role=USER)
   
   e. jwtService.isTokenValid(token, userDetails)
      → Verify signature ✅
      → Check expiration ✅
      → Check username match ✅
      → Returns true
   
   f. Set authentication
      → Create UsernamePasswordAuthenticationToken
      → Add AppUser and authorities [ROLE_USER]
      → Store in SecurityContextHolder
      → Mark request as authenticated

3. WalletController.transfer()
   a. Validates request with Bean Validation
      ✅ fromWalletId not blank
      ✅ toWalletId not blank
      ✅ amount >= 0.01
      ✅ amount has valid decimal format
   
   b. Calls walletService.transfer(request)

4. WalletService.transfer()
   @Transactional (CRITICAL for atomicity):
   
   a. validateAmount(100.00)
      ✅ 100.00 > 0
   
   b. Check wallets are different
      ✅ "wallet-1" ≠ "wallet-2"
   
   c. DEADLOCK PREVENTION: Sort IDs
      String firstId = "wallet-1".compareTo("wallet-2") < 0
                       ? "wallet-1" : "wallet-2"
                     = "wallet-1"
      String secondId = "wallet-2"
   
   d. Lock wallet-1 (pessimistic)
      walletRepository.findByIdWithLock("wallet-1")
      → Database acquires exclusive row lock
      → Other threads wait if they try to access wallet-1
      → Wallet returned: {id: wallet-1, balance: 1000, version: 0}
   
   e. Lock wallet-2 (pessimistic)
      walletRepository.findByIdWithLock("wallet-2")
      → Database acquires exclusive row lock
      → Wallet returned: {id: wallet-2, balance: 500, version: 0}
   
   f. Identify sender/receiver
      from = {id: wallet-1, balance: 1000}
      to = {id: wallet-2, balance: 500}
   
   g. Check sufficient funds
      1000 >= 100 ✅
   
   h. Update balances (in memory)
      from.balance = 1000 - 100 = 900
      to.balance = 500 + 100 = 600
   
   i. Save wallet-1 (pessimistic lock still held)
      walletRepository.save(from)
      → UPDATE wallets SET balance=900, updated_at=now() 
        WHERE id='wallet-1'
      → Updated rows: 1
      → Changes persisted to database
   
   j. Save wallet-2 (pessimistic lock still held)
      walletRepository.save(to)
      → UPDATE wallets SET balance=600, updated_at=now() 
        WHERE id='wallet-2'
      → Updated rows: 1
      → Changes persisted to database
   
   k. Generate reference ID (links both txns)
      referenceId = UUID.randomUUID()
                  = "ref-abc123-def456"
   
   l. Record DEBIT transaction for wallet-1
      transactionRepository.save(
        Transaction(
          walletId: "wallet-1",
          referenceId: "ref-abc123-def456",
          type: TRANSFER_DEBIT,
          amount: 100.00,
          balanceAfter: 900.00,
          description: "Payment",
          createdAt: now()
        )
      )
      → INSERT into transactions table
   
   m. Record CREDIT transaction for wallet-2
      transactionRepository.save(
        Transaction(
          walletId: "wallet-2",
          referenceId: "ref-abc123-def456",
          type: TRANSFER_CREDIT,
          amount: 100.00,
          balanceAfter: 600.00,
          description: "Payment",
          createdAt: now()
        )
      )
      → INSERT into transactions table
   
   n. Build response
      TransferResponse(
        referenceId: "ref-abc123-def456",
        amount: 100.00,
        fromWallet: WalletResponse(
          id: "wallet-1",
          ownerName: "Alice Smith",
          balance: 900.00,
          createdAt: ...,
          updatedAt: now()
        ),
        toWallet: WalletResponse(
          id: "wallet-2",
          ownerName: "Bob Johnson",
          balance: 600.00,
          createdAt: ...,
          updatedAt: now()
        ),
        timestamp: now()
      )
   
   o. @Transactional COMMIT
      → All changes committed to database atomically
      → Pessimistic locks released
      → Other threads can now access wallets

5. GlobalExceptionHandler (not triggered)
   • No exceptions occurred
   • Response wrapping only

6. HTTP Response: 200 OK
   {
     "status": "success",
     "message": "Transfer successful",
     "data": {
       "referenceId": "ref-abc123-def456",
       "amount": 100.00,
       "fromWallet": {...},
       "toWallet": {...},
       "timestamp": "2026-07-03T10:30:00"
     }
   }

=== Side Effects ===
✅ wallet-1.balance = 900 (was 1000)
✅ wallet-2.balance = 600 (was 500)
✅ Both wallet updated_at timestamps changed
✅ Transaction record created for wallet-1 (DEBIT)
✅ Transaction record created for wallet-2 (CREDIT)
✅ Both transactions linked by same referenceId
✅ Audit trail complete and immutable

=== If Error Occurred ===
Example: wallet-2 save fails (DB connection error)
  • @Transactional rollback triggered
  • All changes reverted to pre-transaction state
  • wallet-1.balance reverts to 1000
  • wallet-2.balance reverts to 500
  • NO transaction records created
  • Client receives error response
  • User can retry safely (idempotent)
```

---

## Configuration

### application.properties

```properties
# ── Server ──────────────────────────────────────────────────────
server.port=${PORT:8080}
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

Explanation:
  PORT: Port to listen on (default 8080, override via env var)
  shutdown: Graceful shutdown waits for requests to complete
  timeout-per-shutdown-phase: Give requests 30s to finish


# ── Application ─────────────────────────────────────────────────
spring.application.name=muigo-wallet

Explanation:
  Used for logging and monitoring


# ── Database ────────────────────────────────────────────────────
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/wallet}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

Explanation:
  SPRING_DATASOURCE_URL: PostgreSQL connection (default local)
  SPRING_DATASOURCE_USERNAME: DB user (default postgres)
  DB_PASSWORD: DB password (required env var)
  driver-class-name: PostgreSQL JDBC driver


# ── Connection Pool (HikariCP) ──────────────────────────────────
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000

Explanation:
  maximum-pool-size: Max 10 DB connections
  minimum-idle: Keep at least 2 idle connections ready
  connection-timeout: Wait max 30s to get connection
  idle-timeout: Close connections unused for 10 min


# ── JPA & Hibernate ─────────────────────────────────────────────
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true

Explanation:
  ddl-auto=update: Auto-create/update schema on startup
  dialect: PostgreSQL-specific Hibernate dialect
  show-sql: Don't print SQL to logs (use Flyway instead)
  format_sql: Format SQL if it were printed (readable)


# ── Flyway (DB Migrations) ──────────────────────────────────────
spring.flyway.enabled=false
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

Explanation:
  enabled=false: Disabled (manual control preferred)
  locations: Read migrations from db/migration folder
  baseline-on-migrate: Set baseline if tables already exist


# ── JWT ─────────────────────────────────────────────────────────
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}

Explanation:
  JWT_SECRET: Secret key for signing tokens (must be set)
  JWT_EXPIRATION_MS: Token validity in milliseconds
                     Default: 86400000 = 24 hours


# ── Actuator / Observability ────────────────────────────────────
management.endpoints.web.exposure.include=health,prometheus,info,metrics
management.endpoint.health.show-details=when-authorized
management.metrics.export.prometheus.enabled=true
management.tracing.sampling.probability=1.0

Explanation:
  exposure.include: Expose these monitoring endpoints
  show-details: Only show health details if authenticated
  prometheus.enabled: Export Prometheus metrics
  tracing.sampling: 100% of traces (1.0)


# ── Logging ─────────────────────────────────────────────────────
logging.level.com.muigo.wallet=INFO
logging.level.org.springframework.security=WARN

Explanation:
  com.muigo.wallet: Application logs at INFO level
  Spring Security: WARN level (reduce noise)
```

### OpenApiConfig.java - Swagger/OpenAPI

**Purpose:** Generate interactive API documentation

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Muigo Wallet API",
        version = "1.0.0",
        description = "Production-grade digital wallet API",
        contact = @Contact(name = "Muigo", email = "api@muigo.com")
    ),
    servers = {
        @Server(url = "http://localhost:8080", 
                description = "Local development"),
        @Server(url = "https://api.muigo.com", 
                description = "Production")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {}
```

**Features:**
- ✅ Accessible at http://localhost:8080/swagger-ui/
- ✅ All endpoints documented automatically from controller annotations
- ✅ Request/response examples from DTOs
- ✅ JWT authentication scheme shown
- ✅ Try-it-out functionality (send requests from docs)

---

## Architectural Patterns

### Pattern 1: Layered Architecture

```
Presentation Layer (Controllers)
    ↓
Business Logic Layer (Services)
    ↓
Data Access Layer (Repositories)
    ↓
Database Layer (PostgreSQL)
```

**Benefits:**
- Clear separation of concerns
- Easy to test each layer independently
- Changes in one layer don't affect others
- Single Responsibility Principle

---

### Pattern 2: Data Transfer Objects (DTO)

```
External API ← DTO Layer ← Service Layer ← Entity Layer ← Database
```

**Benefits:**
- Entities (database models) never exposed to clients
- Can evolve API independently of database schema
- Validate input at API boundary before business logic
- Hide sensitive fields (e.g., password hash)

---

### Pattern 3: Transactional Integrity

```
@Transactional
public void transfer(...) {
  // All or nothing
  operation1();
  operation2();
  operation3();
  // If any fails, all roll back
}
```

**Benefits:**
- Atomic operations (all-or-nothing)
- No partial updates
- Data consistency guaranteed
- Completes all operations or completes none

---

### Pattern 4: Optimistic Locking

```
Read: version=5
    ↓
Modify
    ↓
Write: UPDATE ... WHERE version=5
       (Version changed? Conflict → retry)
```

**Benefits:**
- Better concurrency (readers don't block readers)
- Suitable for read-heavy workloads
- Avoids deadlocks in many scenarios

---

### Pattern 5: Pessimistic Locking

```
Lock: SELECT ... FOR UPDATE
  ↓
Modify
  ↓
Commit (release lock)
```

**Benefits:**
- Prevents concurrent modifications at DB level
- Suitable for write-heavy workloads (transfers)
- Serialized access when needed

---

### Pattern 6: Immutable Audit Trail

```
Transactions Table:
  • Append-only (only INSERT)
  • Never UPDATE or DELETE
  • Every operation recorded
  • Immutable timestamp
  • balanceAfter snapshot for debugging
```

**Benefits:**
- Complete history for compliance
- Can't hide or modify transactions
- Debugging easier (state history)
- Regulatory auditing possible

---

### Pattern 7: Global Exception Handler

```
Any Exception Thrown
    ↓
GlobalExceptionHandler
    ↓
RFC 7807 ProblemDetail
    ↓
HTTP Response
```

**Benefits:**
- Consistent error format
- Centralized error handling logic
- No duplicated error handling in controllers
- Easy to add new error types

---

### Pattern 8: Bean Validation

```
Client Request
    ↓
DTO with @NotNull, @DecimalMin, etc.
    ↓
Spring validates
    ↓
MethodArgumentNotValidException if invalid
    ↓
GlobalExceptionHandler → HTTP 400
```

**Benefits:**
- Validation at API boundary
- Business logic works only with valid data
- Clear validation rules as code
- Automatic error messages

---

### Pattern 9: Dependency Injection

```java
@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    // Constructor auto-generated by Lombok
}
```

**Benefits:**
- Loose coupling between components
- Easy to mock for testing
- Spring manages lifecycle
- Flexible configurations

---

### Pattern 10: Configuration Externalization

```java
@Value("${app.jwt.secret}")
private String secret;

// Runtime: Spring reads from application.properties or env vars
```

**Benefits:**
- Secrets not hardcoded
- Different configs for dev/prod
- Easy environment-specific setup
- Follows 12-factor app principles

---

## Summary Table: Complete Feature Overview

| Feature | Technology | Implementation | Purpose |
|---------|-----------|-----------------|---------|
| **Entry Point** | Spring Boot | WalletApplication.java | Bootstrap app |
| **REST API** | Spring Web | Controllers | HTTP endpoints |
| **Security** | Spring Security + JWT | JwtAuthFilter, JwtService | Stateless auth |
| **Password Hashing** | BCrypt | PasswordEncoder | Secure storage |
| **Database** | PostgreSQL | Spring Data JPA | Persistent storage |
| **ORM** | Hibernate | JPA annotations | Object mapping |
| **Transactions** | @Transactional | All write methods | ACID compliance |
| **Optimistic Locking** | @Version | Wallet entity | Read-heavy concurrency |
| **Pessimistic Locking** | @Lock | WalletRepository | Write-heavy concurrency |
| **Validation** | Bean Validation | DTO annotations | Input safety |
| **Exception Handling** | @RestControllerAdvice | GlobalExceptionHandler | Consistent errors |
| **API Docs** | OpenAPI/Swagger | @OpenAPIDefinition | Documentation |
| **Monitoring** | Actuator + Prometheus | /actuator/prometheus | Observability |
| **Logging** | SLF4J | @Slf4j annotations | Debugging |
| **Dependency Injection** | Spring IoC | @RequiredArgsConstructor | Component wiring |
| **Configuration** | Externalized | application.properties | Environment setup |
| **Data Migrations** | Flyway | V1__initial_schema.sql | Schema versioning |

---

## Deployment & Environment Variables

Required environment variables:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/wallet
SPRING_DATASOURCE_USERNAME=app_user
DB_PASSWORD=secure_password_here

# JWT
JWT_SECRET=your-256-bit-secret-key-minimum-recommended
JWT_EXPIRATION_MS=86400000

# Server
PORT=8080
```

---

## Performance Characteristics

| Operation | Time Complexity | Locking | Transactions |
|-----------|-----------------|---------|---------------|
| Register | O(1) | None | @Transactional |
| Login | O(log n) | Index lookup | None |
| Create Wallet | O(1) | None | @Transactional |
| Deposit | O(1) | Optimistic | @Transactional |
| Withdraw | O(1) | Optimistic | @Transactional |
| Transfer | O(1) | Pessimistic (both wallets) | @Transactional |
| Get Transaction History | O(log n) | Index scan | Read-only |

---

## Final Thoughts

This is a **production-ready digital wallet system** with:

✅ Security (JWT, BCrypt, SQLi prevention)
✅ Concurrency (optimistic + pessimistic locking)
✅ Auditability (immutable transaction ledger)
✅ Error Handling (RFC 7807 Problem Details)
✅ Scalability (stateless REST API)
✅ Maintainability (layered architecture, clear separation)
✅ Observability (Prometheus metrics, structured logging)
✅ Documentation (Swagger/OpenAPI)

Perfect for production financial systems!

---

**End of Documentation**

Document Version: 1.0  
Last Updated: 2026-07-03  
Author: Technical Documentation
