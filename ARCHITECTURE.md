# MarketX Authentication — Complete Technical Reference

---

## Table of Contents
1. [Project Architecture Overview](#1-project-architecture-overview)
2. [File Map — Every File and Its Role](#2-file-map--every-file-and-its-role)
3. [Database Design & Relations](#3-database-design--relations)
4. [Complete Workflow Walkthroughs](#4-complete-workflow-walkthroughs)
   - [App Startup & Admin Seeding](#41-app-startup--admin-seeding)
   - [Buyer Registration & Login](#42-buyer-registration--login)
   - [Seller Registration → Approval → Login](#43-seller-registration--approval--login)
   - [Admin Login & Approval Workflow](#44-admin-login--approval-workflow)
   - [Logout](#45-logout)
5. [Spring Security — How It Protects Everything](#5-spring-security--how-it-protects-everything)
6. [REST Principles — Applied or Not?](#6-rest-principles--applied-or-not)
7. [Design Patterns Applied](#7-design-patterns-applied)
8. [Layer-by-Layer Code Explanation](#8-layer-by-layer-code-explanation)

---

## 1. Project Architecture Overview

```
Browser (Thymeleaf HTML)
        │  HTTP Request
        ▼
┌─────────────────────────────┐
│   Spring Security Filter     │  ← Intercepts EVERY request before it hits a controller
│   Chain (SecurityConfig)     │    Checks: is this URL allowed? Is the user logged in?
└────────────┬────────────────┘
             │ Passes through if allowed
             ▼
┌─────────────────────────────┐
│      Controllers             │  ← Entry point for all HTTP endpoints
│  (HomeController,            │    Receives request, calls service, returns view name
│   AuthController,            │
│   AdminController,           │
│   SellerController,          │
│   BuyerController)           │
└────────────┬────────────────┘
             │ Calls
             ▼
┌─────────────────────────────┐
│        Services              │  ← Business logic lives here
│  (UserService, AdminService) │    Validates, transforms, orchestrates
│  (UserServiceImpl,           │
│   AdminServiceImpl)          │
└────────────┬────────────────┘
             │ Calls
             ▼
┌─────────────────────────────┐
│       Repository             │  ← Database access via Spring Data JPA
│    (UserRepository)          │    SQL is auto-generated from method names
└────────────┬────────────────┘
             │ Reads/Writes
             ▼
┌─────────────────────────────┐
│       PostgreSQL Database    │  ← Single table: "users"
│       (users table)          │
└─────────────────────────────┘
```

**Request–Response flow is always:**
`Browser → Security Filter → Controller → Service → Repository → DB → back up the chain`

---

## 2. File Map — Every File and Its Role

### Entry Point
| File | What it does |
|------|-------------|
| `MarketXApplication.java` | Boots the Spring application. `@SpringBootApplication` triggers component scan of the whole `com.marketx.marketplace` package and all sub-packages. |

### Entity Layer (`entity/`)
| File | What it does |
|------|-------------|
| `Role.java` | Enum with 3 values: `ADMIN`, `SELLER`, `BUYER`. Stored as string in DB column `role`. |
| `ApprovalStatus.java` | Enum with 3 values: `PENDING`, `APPROVED`, `REJECTED`. Stored as string in DB column `approval_status`. |
| `User.java` | The only JPA entity. Maps to the `users` table. Holds all user data including role and approval status. |

### DTO Layer (`dto/`)
| File | What it does |
|------|-------------|
| `BuyerRegistrationDto.java` | Carries form data from the buyer registration form to the controller. Has Bean Validation annotations to reject empty/invalid inputs before they reach service code. |
| `SellerRegistrationDto.java` | Same structure as BuyerRegistrationDto — intentionally separate so seller-specific fields can be added later without touching buyer code. |

### Repository Layer (`repository/`)
| File | What it does |
|------|-------------|
| `UserRepository.java` | Extends `JpaRepository<User, Long>`. Spring generates all SQL automatically. Provides: find by email, exists by email, find by role+status, find by role. |

### Security Layer (`security/`)
| File | What it does |
|------|-------------|
| `CustomUserDetails.java` | Adapter that wraps a `User` entity and implements Spring Security's `UserDetails` interface. This is how Spring Security reads your domain user object. Contains the blocking logic for PENDING/REJECTED sellers. |
| `CustomUserDetailsService.java` | Called by Spring Security during every login attempt. Loads the `User` from DB by email, wraps it in `CustomUserDetails`. |
| `CustomAuthSuccessHandler.java` | Called when login succeeds. Reads the role from the authentication object and redirects to the correct dashboard. |
| `CustomAuthFailureHandler.java` | Called when login fails. Translates Spring Security exceptions into URL params (`?error=pending`, `?error=rejected`, `?error=invalid`). |

### Configuration Layer (`config/`)
| File | What it does |
|------|-------------|
| `SecurityConfig.java` | The main security configuration. Defines: which URLs are public, which require which role, how form login works, how logout works. Also defines the `BCryptPasswordEncoder` and `AuthenticationManager` beans. |
| `DataSeeder.java` | Runs once after the application starts. Checks if the admin email exists in DB. If not, creates it with BCrypt-encoded password. Idempotent — safe to restart. |

### Exception Layer (`exception/`)
| File | What it does |
|------|-------------|
| `UserAlreadyExistsException.java` | Thrown by `UserServiceImpl` when someone tries to register with an email that already exists. |
| `PasswordMismatchException.java` | Thrown by `UserServiceImpl` when password and confirmPassword don't match. |
| `GlobalExceptionHandler.java` | `@ControllerAdvice` — catches unhandled exceptions across all controllers. Redirects `EntityNotFoundException` to admin dashboard; sends everything else to the 500 error page. |

### Service Layer (`service/`)
| File | What it does |
|------|-------------|
| `UserService.java` | Interface declaring what user operations are possible: `registerBuyer`, `registerSeller`, `findByEmail`, `existsByEmail`. |
| `AdminService.java` | Interface declaring what admin operations are possible: `getPendingSellerRegistrations`, `approveSeller`, `rejectSeller`, `getAllUsers`. |
| `impl/UserServiceImpl.java` | Concrete implementation of `UserService`. Validates inputs, encodes passwords, sets the right role and approval status, saves to DB. |
| `impl/AdminServiceImpl.java` | Concrete implementation of `AdminService`. Queries pending sellers, updates approval status. |

### Controller Layer (`controller/`)
| File | Endpoints | What it does |
|------|-----------|-------------|
| `HomeController.java` | `GET /` | If logged in, redirects to the right dashboard. Otherwise shows the landing page. |
| `AuthController.java` | `GET /auth/login` `GET /auth/register` `GET/POST /auth/register/buyer` `GET/POST /auth/register/seller` `GET /auth/access-denied` | Handles all authentication UI — login page, register page, form submissions. |
| `AdminController.java` | `GET /admin/dashboard` `POST /admin/sellers/{id}/approve` `POST /admin/sellers/{id}/reject` `GET /admin/users` | Admin's interface to see pending sellers and approve/reject them. |
| `SellerController.java` | `GET /seller/dashboard` | Shows the seller's dashboard after login. |
| `BuyerController.java` | `GET /buyer/dashboard` | Shows the buyer's dashboard after login. |

### Templates (`resources/templates/`)
| File | Purpose |
|------|---------|
| `fragments/navbar.html` | Reusable navigation bar included in every page. Shows Login/Register when not logged in, shows user name + Logout when logged in. Role-specific nav links. |
| `index.html` | Public landing page. |
| `auth/login.html` | Login form. Shows error messages from `?error=` query params. |
| `auth/register.html` | Role selection page — click Buyer or Seller card. |
| `auth/register-buyer.html` | Buyer registration form with `th:field` binding to `BuyerRegistrationDto`. |
| `auth/register-seller.html` | Seller registration form with info box about admin approval. |
| `auth/access-denied.html` | Shown when a user tries to access a URL they don't have permission for. |
| `admin/dashboard.html` | Lists all PENDING sellers in a table. Each row has Approve and Reject buttons. |
| `admin/users.html` | Lists all users with their role and status badges. |
| `seller/dashboard.html` | Seller's home page post-login. |
| `buyer/dashboard.html` | Buyer's home page post-login. |
| `error/500.html` | Generic error page. |

---

## 3. Database Design & Relations

### The `users` Table (auto-created by Hibernate from `User.java`)

```
┌──────────────────────────────────────────────────────────────────┐
│                            users                                  │
├─────────────┬──────────────────┬──────────────────────────────────┤
│ Column       │ Type             │ Constraint                        │
├─────────────┼──────────────────┼──────────────────────────────────┤
│ id           │ BIGINT           │ PRIMARY KEY, AUTO INCREMENT       │
│ name         │ VARCHAR(255)     │ NOT NULL                          │
│ email        │ VARCHAR(255)     │ NOT NULL, UNIQUE                  │
│ password     │ VARCHAR(255)     │ NOT NULL (BCrypt hash, 60 chars)  │
│ role         │ VARCHAR(255)     │ NOT NULL ('ADMIN','SELLER','BUYER')│
│ approval_status│ VARCHAR(255)  │ NOT NULL ('PENDING','APPROVED',   │
│              │                  │           'REJECTED')             │
│ created_at   │ TIMESTAMP        │ NOT NULL, set once on insert      │
└─────────────┴──────────────────┴──────────────────────────────────┘
```

### How this maps to Java code

```java
// User.java — every field annotated for Hibernate
@Entity
@Table(name = "users")                         // → table name "users"
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // → AUTO INCREMENT
    private Long id;

    @Column(nullable = false)                  // → NOT NULL constraint
    private String name;

    @Column(nullable = false, unique = true)   // → NOT NULL + UNIQUE INDEX
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)               // → stored as 'ADMIN'/'SELLER'/'BUYER'
    @Column(nullable = false)                  //   not as integer (EnumType.ORDINAL)
    private Role role;

    @Enumerated(EnumType.STRING)               // → stored as 'PENDING'/'APPROVED'/'REJECTED'
    @Column(nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(nullable = false, updatable = false) // → set ONCE on INSERT, never updated
    private LocalDateTime createdAt;

    @PrePersist                                // → Hibernate hook: runs before first save
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

### Current vs Future Database Relations

**Currently (Auth phase):** Only 1 table. No foreign keys yet.

```
[users]
```

**After Product/Order phase:** 3 tables with relations

```
[users] 1──────────M [products]
   id ◄────────────── seller_id (FK)  ← One seller has many products

[users] 1──────────M [orders]
   id ◄────────────── buyer_id (FK)   ← One buyer has many orders

[products] 1───────M [orders]
   id ◄─────────────── product_id (FK) ← One product can appear in many orders
```

This is the **1:M (One-to-Many)** pattern the lab project requires.

### Why `@Enumerated(EnumType.STRING)` instead of `EnumType.ORDINAL`?

`EnumType.ORDINAL` stores the enum position as an integer (0, 1, 2). If you ever reorder the enum values, all existing data in the database becomes wrong silently. `EnumType.STRING` stores the name `"ADMIN"` — safe, readable, refactor-proof.

---

## 4. Complete Workflow Walkthroughs

### 4.1 App Startup & Admin Seeding

```
JVM starts
    │
    ▼
MarketXApplication.main()
    │   SpringApplication.run() scans all @Component/@Service/@Controller beans
    ▼
Spring creates all beans (SecurityConfig, DataSeeder, UserRepository, Services, Controllers...)
    │
    ▼
Hibernate reads User.java entity → creates/updates "users" table in PostgreSQL
    │
    ▼
DataSeeder.run() is called (implements ApplicationRunner = runs after context is ready)
    │
    ├─ userRepository.existsByEmail("albitahmid@gmail.com")
    │      └─ SQL: SELECT COUNT(*) FROM users WHERE email = 'albitahmid@gmail.com'
    │
    ├─ If false (first startup):
    │      User.builder()
    │          .name("Tahmid Albi")
    │          .email("albitahmid@gmail.com")
    │          .password(passwordEncoder.encode("rafiqul25"))  ← BCrypt hash
    │          .role(Role.ADMIN)
    │          .approvalStatus(ApprovalStatus.APPROVED)
    │          .build()
    │      userRepository.save(admin)
    │      SQL: INSERT INTO users (name, email, password, role, approval_status, created_at)
    │                      VALUES ('Tahmid Albi', 'albitahmid@gmail.com', '$2a$10$...', 'ADMIN', 'APPROVED', now())
    │
    └─ If true (subsequent startups): logs "Admin user already exists, skipping seed."

App is now ready. Port 8081.
```

**Key files involved:**
- `DataSeeder.java` line 27: `if (!userRepository.existsByEmail(ADMIN_EMAIL))`
- `DataSeeder.java` lines 28–36: `User.builder()...build()`
- `DataSeeder.java` line 37: `userRepository.save(admin)`
- `UserRepository.java` line 14: `boolean existsByEmail(String email)` — Spring generates the SQL automatically from the method name

---

### 4.2 Buyer Registration & Login

#### Step 1 — User clicks Register
```
Browser: GET /
SecurityConfig: "/" is in permitAll() → no login required → passes through
HomeController.home() → authentication is null (anonymous) → returns "index"
Thymeleaf renders index.html
navbar.html: sec:authorize="!isAuthenticated()" is TRUE → shows Login + Register buttons
```

#### Step 2 — User clicks Register button
```
Browser: GET /auth/register
SecurityConfig: "/auth/**" is in permitAll() → passes through
AuthController.showRegisterPage() → returns "auth/register"
Thymeleaf renders register.html → shows Buyer card + Seller card
```

#### Step 3 — User clicks Buyer card
```
Browser: GET /auth/register/buyer
AuthController.showBuyerRegistrationForm(model)
    → model.addAttribute("buyerDto", new BuyerRegistrationDto())   ← empty form object
    → returns "auth/register-buyer"

Thymeleaf renders register-buyer.html
th:object="${buyerDto}" → binds the form to the DTO object
th:field="*{name}"      → renders <input name="name" ...>
th:field="*{email}"     → renders <input name="email" ...>
th:field="*{password}"  → renders <input name="password" ...>
```

#### Step 4 — User submits the form
```
Browser: POST /auth/register/buyer
         Body: name=Jane&email=jane@x.com&password=abc123&confirmPassword=abc123

AuthController.registerBuyer(@Valid @ModelAttribute BuyerRegistrationDto dto, BindingResult result, ...)
    │
    ├─ Spring binds POST body fields → dto.name="Jane", dto.email="jane@x.com", etc.
    │
    ├─ @Valid triggers Bean Validation on the DTO:
    │      @NotBlank on name     → passes (not blank)
    │      @Email on email       → passes (valid format)
    │      @Size(min=6) on pass  → passes (length >= 6)
    │      @NotBlank on confirm  → passes
    │
    ├─ result.hasErrors() → false → continues
    │
    ├─ userService.registerBuyer(dto)  → calls UserServiceImpl
    │       │
    │       ├─ userRepository.existsByEmail("jane@x.com") → false (new email)
    │       ├─ dto.getPassword().equals(dto.getConfirmPassword()) → true
    │       ├─ User.builder()
    │       │       .name("Jane")
    │       │       .email("jane@x.com")
    │       │       .password(passwordEncoder.encode("abc123"))  ← BCrypt hash
    │       │       .role(Role.BUYER)
    │       │       .approvalStatus(ApprovalStatus.APPROVED)     ← IMMEDIATE access
    │       │       .build()
    │       └─ userRepository.save(buyer)
    │              SQL: INSERT INTO users (...) VALUES ('Jane', 'jane@x.com', '$2a$10$...', 'BUYER', 'APPROVED', now())
    │
    ├─ redirectAttributes.addFlashAttribute("successMessage", "Registration successful!...")
    └─ return "redirect:/auth/login"   ← PRG Pattern (Post-Redirect-Get)
```

#### Step 5 — Browser follows redirect to login page
```
Browser: GET /auth/login
AuthController.showLoginPage(error=null, logout=null, model)
    → successMessage is in flash scope (from RedirectAttributes) → model has it
    → returns "auth/login"
Thymeleaf shows the form + green success alert
```

#### Step 6 — User submits login form
```
Browser: POST /auth/login
         Body: email=jane@x.com&password=abc123

THIS IS NOT HANDLED BY AuthController.
Spring Security intercepts POST /auth/login because of:
    SecurityConfig line: .loginProcessingUrl("/auth/login")

Spring Security's UsernamePasswordAuthenticationFilter runs:
    1. Reads "email" field (not "username") because: .usernameParameter("email")
    2. Reads "password" field
    3. Calls: customUserDetailsService.loadUserByUsername("jane@x.com")
                │
                └─ userRepository.findByEmail("jane@x.com")
                   SQL: SELECT * FROM users WHERE email = 'jane@x.com'
                   Returns: User entity (found)
                   Returns: new CustomUserDetails(user)

    4. Checks CustomUserDetails booleans:
          isEnabled()         → true  (BUYER with APPROVED status)
          isAccountNonLocked()→ true
          isAccountNonExpired()→ true
          isCredentialsNonExpired()→ true

    5. Compares raw password against BCrypt hash:
          passwordEncoder.matches("abc123", "$2a$10$...") → true

    6. Authentication SUCCEEDS
    7. Calls: CustomAuthSuccessHandler.onAuthenticationSuccess()
                authority = "ROLE_BUYER"
                switch → "/buyer/dashboard"
                response.sendRedirect("/buyer/dashboard")
```

#### Step 7 — Dashboard
```
Browser: GET /buyer/dashboard
SecurityConfig: "/buyer/**" requires hasRole("BUYER") → user has ROLE_BUYER → passes through
BuyerController.showDashboard(authentication, model)
    → (CustomUserDetails) authentication.getPrincipal()  ← get the logged-in user
    → model.addAttribute("user", userDetails.getUser())
    → returns "buyer/dashboard"
Thymeleaf renders buyer/dashboard.html
    th:text="${user.name}" → "Jane"
```

---

### 4.3 Seller Registration → Approval → Login

#### Registration (Steps 1–4 same as buyer but different URL):
```
POST /auth/register/seller
AuthController.registerSeller(dto, ...)
    → userService.registerSeller(dto)
           → UserServiceImpl.registerSeller()
                  User.builder()
                      .role(Role.SELLER)
                      .approvalStatus(ApprovalStatus.PENDING)   ← KEY DIFFERENCE
                      .build()
                  userRepository.save(seller)
    → redirectAttributes.addFlashAttribute("successMessage",
          "Seller registration submitted! Please wait for admin approval...")
    → return "redirect:/auth/login"
```

#### Seller tries to login BEFORE approval:
```
POST /auth/login (email=seller@x.com, password=pass123)

Spring Security → CustomUserDetailsService.loadUserByUsername("seller@x.com")
    → Returns CustomUserDetails wrapping the PENDING seller

Spring Security checks CustomUserDetails booleans:
    isEnabled() line in CustomUserDetails.java:
        if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING)
            return false;   ← !!!!!

Spring Security sees isEnabled() = false
→ throws DisabledException("User is disabled")

CustomAuthFailureHandler.onAuthenticationFailure() catches it:
    exception instanceof DisabledException → errorParam = "pending"
    response.sendRedirect("/auth/login?error=pending")

AuthController.showLoginPage(error="pending", ...)
    switch "pending" → "Your seller account is pending admin approval. Please wait."
    model.addAttribute("errorMessage", message)
    returns "auth/login"

Login page shows red error box.
```

#### Admin approves the seller (see section 4.4):
```
SQL: UPDATE users SET approval_status = 'APPROVED' WHERE id = {seller_id}
```

#### Seller retries login AFTER approval:
```
POST /auth/login

Spring Security → CustomUserDetailsService → CustomUserDetails

isEnabled():
    user.getApprovalStatus() is now APPROVED (not PENDING anymore)
    → condition is false → returns true  ← !!!!!

isAccountNonLocked(): returns true (not REJECTED)

Authentication SUCCEEDS
CustomAuthSuccessHandler → "/seller/dashboard"
```

---

### 4.4 Admin Login & Approval Workflow

#### Admin logs in:
```
POST /auth/login (email=albitahmid@gmail.com, password=rafiqul25)

Spring Security → loadUserByUsername → finds Admin user
CustomUserDetails:
    isEnabled() → true (ADMIN, APPROVED)
    isAccountNonLocked() → true

Authentication SUCCEEDS
CustomAuthSuccessHandler:
    authority = "ROLE_ADMIN"
    switch → "/admin/dashboard"
    redirect to /admin/dashboard
```

#### Admin views pending sellers:
```
GET /admin/dashboard
SecurityConfig: "/admin/**" → hasRole("ADMIN") → admin has ROLE_ADMIN → passes
AdminController.showDashboard(model)
    → adminService.getPendingSellerRegistrations()
           → AdminServiceImpl.getPendingSellerRegistrations()
                  → userRepository.findByRoleAndApprovalStatus(Role.SELLER, ApprovalStatus.PENDING)
                  SQL: SELECT * FROM users WHERE role = 'SELLER' AND approval_status = 'PENDING'
                  Returns: List<User>
    → model.addAttribute("pendingSellers", list)
    → returns "admin/dashboard"

Thymeleaf renders admin/dashboard.html:
    th:each="seller, stat : ${pendingSellers}"  ← loops over the list
    For each seller row:
        th:text="${seller.name}"
        th:text="${seller.email}"
        th:text="${#temporals.format(seller.createdAt, 'dd MMM yyyy, HH:mm')}"
        Two forms: Approve button (POST .../approve) and Reject button (POST .../reject)
        th:action="@{/admin/sellers/{id}/approve(id=${seller.id})}"  ← builds the URL with seller's ID
        Thymeleaf auto-injects CSRF hidden input into both forms
```

#### Admin clicks Approve:
```
Browser: POST /admin/sellers/5/approve    (assuming seller's id is 5)

Spring Security: Checks /admin/** → hasRole("ADMIN") → passes

AdminController.approveSeller(id=5, redirectAttributes)
    → adminService.approveSeller(5)
           → AdminServiceImpl.approveSeller(5)
                  → userRepository.findById(5)
                  SQL: SELECT * FROM users WHERE id = 5
                  Returns: User (the seller)
                  user.setApprovalStatus(ApprovalStatus.APPROVED)
                  userRepository.save(user)
                  SQL: UPDATE users SET approval_status = 'APPROVED' WHERE id = 5
    → redirectAttributes.addFlashAttribute("successMessage", "Seller account approved successfully.")
    → return "redirect:/admin/dashboard"   ← PRG pattern

Browser: GET /admin/dashboard (re-loads, the seller is no longer in the pending list)
Green success alert shown.
```

---

### 4.5 Logout

```
Browser: POST /auth/logout   (form in navbar uses th:action="@{/auth/logout}", method="post")

Spring Security intercepts (logoutUrl configured in SecurityConfig)
    → Invalidates the HTTP session (all session data cleared)
    → Deletes JSESSIONID cookie from browser
    → Redirects to: /auth/login?logout

AuthController.showLoginPage(error=null, logout="logout", model)
    → logout != null → model.addAttribute("successMessage", "You have been logged out successfully.")
    → returns "auth/login"

Login page shows green "Logged out" message.
```

Why POST for logout? Because GET-based logout is a security vulnerability — an attacker could embed a `<img src="/auth/logout">` on any page and silently log out the user. POST + CSRF token prevents this.

---

## 5. Spring Security — How It Protects Everything

### The Security Filter Chain

Every single HTTP request passes through Spring Security's filter chain BEFORE reaching any controller. This is not optional — it's wired into the Servlet container.

```
HTTP Request arrives at port 8081
    │
    ▼
SecurityFilterChain (defined in SecurityConfig.filterChain())
    │
    ├─ Step 1: Is the URL public?
    │    "/", "/auth/**", "/css/**", "/js/**", "/images/**"  → permitAll()
    │    If yes → skip auth checks, go to controller
    │
    ├─ Step 2: Is the URL role-restricted?
    │    "/admin/**" → requires ROLE_ADMIN
    │    "/seller/**" → requires ROLE_SELLER
    │    "/buyer/**" → requires ROLE_BUYER
    │    If user doesn't have the right role → redirect to /auth/access-denied
    │
    ├─ Step 3: Is the URL anything else?
    │    anyRequest().authenticated() → must be logged in (any role)
    │    If not logged in → redirect to /auth/login
    │
    └─ Passes to Controller
```

### The UserDetails Contract — How Sellers Get Blocked

`CustomUserDetails` implements `UserDetails`, which has 4 boolean methods Spring Security checks on every login:

```java
// CustomUserDetails.java

// Spring Security checks this. If false → throws DisabledException
@Override
public boolean isEnabled() {
    // PENDING sellers are blocked here
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING) {
        return false;   // Login blocked
    }
    return true;        // Everyone else: allowed
}

// Spring Security checks this. If false → throws LockedException
@Override
public boolean isAccountNonLocked() {
    // REJECTED sellers are blocked here
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.REJECTED) {
        return false;   // Login blocked
    }
    return true;        // Everyone else: allowed
}
```

This is elegant because **no controller code is needed** to block sellers. It's handled entirely inside the security layer.

### Exception-to-URL Mapping

```
CustomAuthFailureHandler.onAuthenticationFailure():
    DisabledException (PENDING seller)  → /auth/login?error=pending
    LockedException   (REJECTED seller) → /auth/login?error=rejected
    BadCredentialsException (wrong pass)→ /auth/login?error=invalid

AuthController.showLoginPage(error, ...):
    "pending"  → "Your seller account is pending admin approval."
    "rejected" → "Your seller account has been rejected by the admin."
    anything else → "Invalid email or password."
```

### Password Encryption

```java
// SecurityConfig.java — declares the bean
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();   // BCrypt with default strength 10 (2^10 = 1024 rounds)
}

// UserServiceImpl.java — used during registration
.password(passwordEncoder.encode(dto.getPassword()))
// "abc123" → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

// Spring Security — used during login (automatic)
passwordEncoder.matches(rawPassword, storedHash)
// Compares without decrypting — BCrypt is one-way
```

BCrypt is specifically designed to be slow (computationally expensive) to make brute-force attacks impractical. Even if the database is compromised, raw passwords cannot be recovered.

### CSRF Protection

All HTML forms use `th:action` instead of plain `action`:
```html
<!-- login.html -->
<form th:action="@{/auth/login}" method="post">

<!-- admin/dashboard.html -->
<form th:action="@{/admin/sellers/{id}/approve(id=${seller.id})}" method="post">
```

Thymeleaf automatically adds:
```html
<input type="hidden" name="_csrf" value="abc123xyz...">
```

Spring Security validates this token on every POST. If the token is missing or wrong → 403 Forbidden. This prevents Cross-Site Request Forgery attacks.

---

## 6. REST Principles — Applied or Not?

This is a **server-side rendered (SSR) MVC application** using Thymeleaf, not a REST API. However, REST principles were applied wherever applicable.

### What Was Applied

#### ✅ Proper HTTP Methods
Every URL uses the semantically correct HTTP verb:

| Action | HTTP Method | URL | Controller Method |
|--------|-------------|-----|-------------------|
| View login page | GET | `/auth/login` | `showLoginPage()` |
| View register page | GET | `/auth/register` | `showRegisterPage()` |
| Submit registration | POST | `/auth/register/buyer` | `registerBuyer()` |
| Submit registration | POST | `/auth/register/seller` | `registerSeller()` |
| View admin dashboard | GET | `/admin/dashboard` | `showDashboard()` |
| View all users | GET | `/admin/users` | `showAllUsers()` |
| Approve a seller | POST | `/admin/sellers/{id}/approve` | `approveSeller()` |
| Reject a seller | POST | `/admin/sellers/{id}/reject` | `rejectSeller()` |
| View seller dashboard | GET | `/seller/dashboard` | `showDashboard()` |
| View buyer dashboard | GET | `/buyer/dashboard` | `showDashboard()` |
| Submit logout | POST | `/auth/logout` | (Spring Security) |

GET = read-only, no side effects. POST = creates/modifies something. This is correct REST usage.

#### ✅ Resource-Oriented URLs
The approve/reject URLs follow REST resource conventions:
```
POST /admin/sellers/{id}/approve
POST /admin/sellers/{id}/reject
```
`{id}` is a path variable — it identifies the specific seller resource. This is RESTful URI design.

#### ✅ URL Hierarchy Reflects Resource Hierarchy
```
/admin/sellers/{id}/approve
  │       │      │      └── action on the resource
  │       │      └── specific seller (by ID)
  │       └── sellers sub-resource
  └── admin namespace
```

#### ✅ Global Exception Handling
REST principle: errors should be handled consistently across the application, not scattered in each controller.
```java
// GlobalExceptionHandler.java
@ControllerAdvice           ← applies to ALL controllers
@ExceptionHandler(EntityNotFoundException.class)  ← catches this exception anywhere
@ExceptionHandler(Exception.class)                ← fallback for everything else
```

#### ✅ Separation of Concerns (Controllers are thin)
Controllers don't contain business logic — they only:
1. Receive the request
2. Call a service method
3. Pass data to the view
4. Return the view name or redirect

All logic is in the service layer.

#### ✅ Stateless-Friendly Security
Spring Security session management is used (standard for web MVC), but the security rules themselves (URL authorization) are stateless — they don't depend on what happened before in the session.

### What Was NOT Applied (by design for MVC)

#### ❌ JSON Responses
REST APIs return JSON. This app returns HTML. That's correct for a Thymeleaf MVC app — it would be wrong to force JSON here.

#### ❌ PATCH/PUT/DELETE HTTP Methods
HTML forms only support GET and POST. To delete or update via a form, you must use POST. This is an HTML limitation, not a design flaw. True REST APIs use `DELETE /admin/sellers/{id}` but HTML can't do that natively.

#### ❌ HTTP Status Codes in Response Body
REST APIs return 201 Created, 404 Not Found, etc. MVC apps return redirects and HTML pages instead.

**Conclusion:** This app correctly applies REST principles in URI design and HTTP method usage. The SSR paradigm means full REST compliance is intentionally not the goal — both approaches are valid for their respective use cases.

---

## 7. Design Patterns Applied

### 1. Repository Pattern
**Where:** `UserRepository.java`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleAndApprovalStatus(Role role, ApprovalStatus approvalStatus);
}
```

**What it does:** Abstracts all database access behind an interface. Controllers and services NEVER write SQL or touch `EntityManager` directly. Everything goes through the repository. Spring Data JPA generates the SQL from the method name — `findByRoleAndApprovalStatus` becomes `SELECT * FROM users WHERE role = ? AND approval_status = ?`.

**Why it matters:** If you switch from PostgreSQL to MySQL or MongoDB, you only change the repository, not any service or controller code.

---

### 2. Service Layer Pattern (also called Facade Pattern)
**Where:** `UserService.java` + `UserServiceImpl.java`, `AdminService.java` + `AdminServiceImpl.java`

```java
// Interface — what operations exist
public interface UserService {
    void registerBuyer(BuyerRegistrationDto dto);
    void registerSeller(SellerRegistrationDto dto);
}

// Implementation — how they work
@Service
public class UserServiceImpl implements UserService {
    @Override
    public void registerBuyer(BuyerRegistrationDto dto) {
        // validation, encoding, saving
    }
}
```

**What it does:** Separates business logic from HTTP handling. Controllers call `userService.registerBuyer(dto)` without knowing anything about the implementation.

**Why it matters:** You can swap `UserServiceImpl` for a mock in tests. You can add caching or logging to the service without touching controllers. Unit testing becomes straightforward.

---

### 3. DTO Pattern (Data Transfer Object)
**Where:** `BuyerRegistrationDto.java`, `SellerRegistrationDto.java`

```java
@Data
public class BuyerRegistrationDto {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;

    @NotBlank
    private String confirmPassword;  // ← field that doesn't exist on User entity
}
```

**What it does:** Creates a separate object for carrying data between the web layer and the service layer. The `User` entity is never directly exposed to the browser.

**Why it matters:** `confirmPassword` doesn't belong on the `User` entity — it's only needed during registration. DTOs let you add/remove form fields without modifying your database schema. It also prevents mass assignment vulnerabilities (you can't accidentally set `role=ADMIN` from a form if the DTO doesn't have a `role` field).

---

### 4. Decorator Pattern
**Where:** `CustomUserDetails.java`

```java
public class CustomUserDetails implements UserDetails {

    private final User user;  // wraps the domain object

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {    // exposes the original object
        return user;
    }

    // Adds UserDetails behavior ON TOP of User
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
}
```

**What it does:** Adds Spring Security's `UserDetails` behaviour to your `User` domain entity without modifying `User.java`. The `User` class stays clean — it doesn't extend or implement anything Spring-specific.

**Why it matters:** Keeps domain objects independent of framework concerns. `User.java` can be used in contexts that have nothing to do with Spring Security.

---

### 5. Strategy Pattern
**Where:** `CustomAuthSuccessHandler.java`, `CustomAuthFailureHandler.java`

```java
// Two separate "strategies" for what happens on login success/failure
@Component
public class CustomAuthSuccessHandler implements AuthenticationSuccessHandler {
    // Strategy 1: redirect based on role
}

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {
    // Strategy 2: redirect with error param based on exception type
}

// Injected into SecurityConfig — swappable without changing SecurityConfig
.successHandler(successHandler)
.failureHandler(failureHandler)
```

**What it does:** Encapsulates the "what to do on success" and "what to do on failure" as separate pluggable objects. Spring Security doesn't care about the implementation — it just calls the interface method.

**Why it matters:** You can replace the redirect-based strategy with one that returns JSON (for an API), logs events, or sends emails — without touching `SecurityConfig`.

---

### 6. Builder Pattern
**Where:** `User.java` (`@Builder`), used in `UserServiceImpl.java` and `DataSeeder.java`

```java
// Without builder (telescoping constructor hell):
new User(null, "Jane", "jane@x.com", encoded, Role.BUYER, ApprovalStatus.APPROVED, null)
//           ↑ which arg is which? easy to mix up

// With builder (readable, safe):
User.builder()
    .name("Jane")
    .email("jane@x.com")
    .password(encodedPassword)
    .role(Role.BUYER)
    .approvalStatus(ApprovalStatus.APPROVED)
    .build()
```

**What it does:** Provides a readable, order-independent way to construct objects. Lombok's `@Builder` generates all the builder code automatically.

**Why it matters:** When a class has many fields, constructors become confusing and error-prone. Builder makes the construction self-documenting.

---

### 7. PRG Pattern (Post-Redirect-Get)
**Where:** Every POST handler in `AuthController.java` and `AdminController.java`

```java
// AuthController.java — after successful registration
redirectAttributes.addFlashAttribute("successMessage", "Registration successful!");
return "redirect:/auth/login";   ← sends HTTP 302 to browser

// AdminController.java — after approving a seller
redirectAttributes.addFlashAttribute("successMessage", "Seller account approved.");
return "redirect:/admin/dashboard";
```

**What it does:** After every successful POST, redirect to a GET instead of directly returning a page. If the user presses F5/refresh after a POST, the browser would re-submit the form. With PRG, refresh just re-fetches the GET page safely.

**Why it matters:** Prevents duplicate form submissions (double registration, double approval).

`RedirectAttributes.addFlashAttribute()` stores the message in the session temporarily, survives the redirect, then is removed after the GET is served. This is how success messages appear on the page after a redirect.

---

### 8. Template Method Pattern
**Where:** `fragments/navbar.html` included via Thymeleaf fragments

```html
<!-- Every page includes this: -->
<div th:replace="~{fragments/navbar :: navbar}"></div>

<!-- navbar.html defines the reusable structure -->
<nav class="navbar" th:fragment="navbar">
    <!-- LEFT: auth controls -->
    <!-- RIGHT: brand -->
</nav>
```

**What it does:** Defines a reusable UI structure (the navbar) that is "included" by all pages. The page provides its own content; the fragment provides the common chrome.

**Why it matters:** Change the navbar in one place (`navbar.html`) and it updates everywhere immediately. No copy-paste duplication.

---

## 8. Layer-by-Layer Code Explanation

### How Spring Boot Wires Everything Together

`@SpringBootApplication` on `MarketXApplication` triggers:
1. **Component Scan** — finds every `@Component`, `@Service`, `@Controller`, `@Repository`, `@Configuration` in `com.marketx.marketplace.*`
2. **Dependency Injection** — creates beans and injects them into each other automatically
3. **Spring Data JPA** — sees `UserRepository extends JpaRepository` and creates a proxy implementation with SQL generation
4. **Spring Security** — sees `SecurityConfig` and wires the filter chain into Tomcat

`@RequiredArgsConstructor` (Lombok) on services and controllers generates a constructor with all `final` fields — Spring uses this constructor for dependency injection. Example:

```java
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    // Lombok generates:
    // public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    //     this.userRepository = userRepository;
    //     this.passwordEncoder = passwordEncoder;
    // }
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}
```

### How `@Valid` + DTO Validation Works

```java
// AuthController.java
public String registerBuyer(
    @Valid @ModelAttribute("buyerDto") BuyerRegistrationDto dto,  // ← @Valid triggers validation
    BindingResult result,      // ← Spring puts validation errors here
    ...) {

    if (result.hasErrors()) {
        return "auth/register-buyer";  // re-show form with errors highlighted
    }
```

```java
// BuyerRegistrationDto.java — annotations control what's valid
@NotBlank(message = "Name is required")    ← null or "" or "   " all fail
private String name;

@Email(message = "Please enter a valid email address")   ← "notanemail" fails
private String email;

@Size(min = 6, message = "Password must be at least 6 characters")  ← "abc" fails
private String password;
```

```html
<!-- register-buyer.html — shows the error message below the field -->
<span class="field-error"
      th:if="${#fields.hasErrors('name')}"
      th:errors="*{name}">
</span>
```

### How Thymeleaf Security Tags Work

`thymeleaf-extras-springsecurity6` provides `sec:` namespace tags:

```html
<!-- navbar.html -->
<div sec:authorize="!isAuthenticated()">
    <!-- Only shown to guests (not logged in) -->
    Login | Register
</div>

<div sec:authorize="isAuthenticated()">
    <!-- Only shown to logged-in users -->
    Welcome, <strong sec:authentication="principal.user.name"></strong>
</div>

<span sec:authorize="hasRole('ADMIN')">
    <!-- Only shown to admins -->
    Admin-only nav links
</span>
```

`sec:authentication="principal.user.name"`:
- `principal` = the `CustomUserDetails` object stored in the security context
- `.user` = calls `CustomUserDetails.getUser()` → returns the `User` entity
- `.name` = calls `User.getName()` (generated by Lombok `@Getter`) → returns `"Tahmid Albi"`

These tags are evaluated server-side by Thymeleaf before the HTML is sent to the browser. The browser never sees anything about the security context.

---

*Generated for MarketX — Software Engineering Lab Project*
