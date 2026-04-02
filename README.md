<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Render-Deployed-46E3B7?style=for-the-badge&logo=render&logoColor=white" />
  <img src="https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white" />
</p>

# MarketX — Mini Online Marketplace

> A production-ready, full-stack web application demonstrating a complete professional software development workflow — from layered architecture and role-based security to CI/CD pipelines and cloud deployment.

**Live Demo:** [https://marketx-a-mini-online-marketplace-2.onrender.com](https://marketx-a-mini-online-marketplace-2.onrender.com)

---

## Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
  - [High-Level Architecture Diagram](#high-level-architecture-diagram)
  - [Layered Architecture](#layered-architecture)
- [ER Diagram](#er-diagram)
- [API Endpoints](#api-endpoints)
  - [Public Endpoints](#public-endpoints)
  - [Buyer Endpoints](#buyer-endpoints-role-buyer)
  - [Seller Endpoints](#seller-endpoints-role-seller)
  - [Admin Endpoints](#admin-endpoints-role-admin)
- [Security](#security)
- [Run Instructions](#run-instructions)
  - [Prerequisites](#prerequisites)
  - [Run with Docker (Recommended)](#run-with-docker-recommended)
  - [Run Locally (Development)](#run-locally-development)
  - [Run Tests](#run-tests)
- [CI/CD Pipeline](#cicd-pipeline)
  - [Pipeline Diagram](#pipeline-diagram)
  - [Pipeline Stages](#pipeline-stages)
- [Git Workflow](#git-workflow)
- [Project Structure](#project-structure)
- [Testing Strategy](#testing-strategy)

---

## Project Overview

**MarketX** is a mini online marketplace where **Buyers** browse and purchase products, **Sellers** list and manage inventory, and an **Admin** oversees the entire platform. The project emphasizes clean architecture, security best practices, comprehensive testing, and a fully automated deployment pipeline — rather than feature richness.

### Key Features

| Role | Capabilities |
|------|-------------|
| **Buyer** | Browse & search products, add to cart, checkout with SSLCommerz payment, track orders, leave reviews, manage profile |
| **Seller** | List products (name, price, category, image), manage inventory, view & update order statuses, access sales analytics dashboard |
| **Admin** | Approve/reject seller registrations, manage all users (enable/disable), oversee all products & orders, view platform-wide statistics & revenue charts |

**Additional Highlights:**
- BCrypt password encryption
- CSRF protection with SSLCommerz payment gateway integration
- Role-based access control at URL and method level
- Responsive Thymeleaf UI with dynamic navbar
- Automated admin seeding on first startup

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend Framework | Spring Boot 4.0.3 (Java 17) |
| Template Engine | Thymeleaf + Thymeleaf Spring Security extras |
| Security | Spring Security (BCrypt, role-based auth, CSRF) |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| Payment Gateway | SSLCommerz (Sandbox) |
| Build Tool | Maven |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Deployment | Render |
| Testing | JUnit 5, Mockito, MockMvc, H2 (in-memory for tests) |
| Utilities | Lombok, Jackson |

---

## Architecture

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT (Browser)                               │
│                         Thymeleaf-rendered HTML pages                       │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ HTTP Requests
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SPRING SECURITY FILTER CHAIN                        │
│                                                                             │
│  • URL-based authorization (/admin/** → ADMIN, /seller/** → SELLER, etc.)  │
│  • Form-based login with custom success/failure handlers                    │
│  • BCrypt password encoding                                                 │
│  • CSRF protection (with SSLCommerz callback exemptions)                    │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ Authenticated Request
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CONTROLLER LAYER                                 │
│                                                                             │
│  HomeController  │ AuthController │ BuyerController │ SellerController      │
│  ProductController │ AdminController                                        │
│                                                                             │
│  Receives HTTP requests → delegates to services → returns Thymeleaf views   │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ Method Calls
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SERVICE LAYER                                   │
│                                                                             │
│  UserService │ ProductService │ OrderService │ CartService │ ReviewService  │
│  AdminService                                                               │
│                                                                             │
│  Business logic, validation, DTO ↔ Entity mapping, transaction management   │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ JPA Calls
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           REPOSITORY LAYER                                  │
│                                                                             │
│  UserRepository │ ProductRepository │ OrderRepository │ OrderItemRepository │
│  CartItemRepository │ ReviewRepository                                      │
│                                                                             │
│  Spring Data JPA interfaces — SQL auto-generated from method signatures     │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │ JDBC
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          POSTGRESQL DATABASE                                │
│                                                                             │
│    users │ products │ orders │ order_items │ cart_items │ reviews            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layered Architecture

The application follows a strict **4-layer architecture** with clear separation of concerns:

```
┌─────────────────────────────┐
│     Presentation Layer      │  Controllers + Thymeleaf templates
│  (Request/Response handling) │  DTOs for form binding & validation
├─────────────────────────────┤
│       Security Layer        │  SecurityConfig, CustomUserDetails,
│   (Cross-cutting concern)   │  Success/Failure handlers, CSRF
├─────────────────────────────┤
│      Business Layer         │  Service interfaces + implementations
│  (Domain logic & rules)     │  Exception handling, validation
├─────────────────────────────┤
│     Persistence Layer       │  JPA Repositories, Entity classes
│   (Data access & storage)   │  Hibernate auto-DDL, relationships
└─────────────────────────────┘
```

**Design patterns applied:** Repository Pattern, DTO Pattern, Service Layer Pattern, Dependency Injection, Template Method (Spring Security filters), Strategy (custom auth handlers).

---

## ER Diagram

```
┌──────────────────────┐       ┌────────────────────────┐       ┌──────────────────────┐
│        users         │       │       products          │       │       reviews         │
├──────────────────────┤       ├────────────────────────┤       ├──────────────────────┤
│ id          BIGINT PK│◄──┐   │ id          BIGINT PK  │◄──┐   │ id         BIGINT PK │
│ name        VARCHAR  │   │   │ name        VARCHAR     │   │   │ stars      INT       │
│ email       VARCHAR  │   │   │ description TEXT        │   │   │ comment    TEXT      │
│  (UNIQUE)            │   │   │ price       DECIMAL     │   │   │ created_at TIMESTAMP │
│ password    VARCHAR  │   │   │  (10,2)                 │   │   │                      │
│ role        VARCHAR  │   │   │ quantity    INT          │   ├───│ buyer_id   BIGINT FK │
│  (ADMIN/SELLER/     │   │   │ image_url   VARCHAR     │   │   │ product_id BIGINT FK │──┐
│   BUYER)             │   │   │ category    VARCHAR     │   │   │                      │  │
│ approval_status      │   ├───│ seller_id   BIGINT FK   │   │   │ UNIQUE(buyer_id,     │  │
│  VARCHAR             │   │   │ created_at  TIMESTAMP   │   │   │   product_id)         │  │
│  (PENDING/APPROVED/  │   │   └────────────────────────┘   │   └──────────────────────┘  │
│   REJECTED)          │   │              ▲                  │              │               │
│ created_at TIMESTAMP │   │              │                  │              │               │
└──────────────────────┘   │              │                  │              ▼               │
         ▲  ▲              │   ┌──────────┴─────────────┐   │   ┌──────────────────────┐  │
         │  │              │   │      order_items        │   │   │      cart_items       │  │
         │  │              │   ├────────────────────────┤   │   ├──────────────────────┤  │
         │  │              │   │ id           BIGINT PK  │   │   │ id         BIGINT PK │  │
         │  │              │   │ quantity     INT         │   │   │ quantity   INT       │  │
         │  │              │   │ unit_price   DECIMAL     │   │   │ added_at   TIMESTAMP │  │
         │  │              │   │  (10,2)                  │   │   │                      │  │
         │  │              │   │ subtotal     DECIMAL     │   ├───│ buyer_id   BIGINT FK │  │
         │  │              │   │  (12,2)                  │   │   │ product_id BIGINT FK │──┘
         │  │              │   │ product_name  VARCHAR    │   │   │                      │
         │  │              │   │ product_category VARCHAR │   │   │ UNIQUE(buyer_id,     │
         │  │              │   │                          │   │   │   product_id)         │
         │  │              ├───│ seller_id    BIGINT FK   │   │   └──────────────────────┘
         │  │              │   │ order_id     BIGINT FK   │───┘
         │  │              │   │ product_id   BIGINT FK   │──────────────────────────────┘
         │  │              │   └────────────────────────┘
         │  │              │
         │  │   ┌──────────┴─────────────┐
         │  │   │        orders           │
         │  │   ├────────────────────────┤
         │  │   │ id            BIGINT PK │
         │  │   │ total_amount  DECIMAL   │
         │  │   │  (12,2)                 │
         │  │   │ status        VARCHAR   │
         │  │   │  (ORDER_PLACED/         │
         │  │   │   CONFIRMED/PROCESSING/ │
         │  │   │   SENT_FOR_DELIVERY/    │
         │  │   │   OUT_FOR_DELIVERY/     │
         │  │   │   DELIVERED/CANCELLED)  │
         │  │   │ shipping_address VARCHAR│
         │  │   │  (500)                  │
         │  │   │ created_at    TIMESTAMP │
         │  │   │ updated_at    TIMESTAMP │
         │  └───│ buyer_id      BIGINT FK │
         │      └────────────────────────┘
         │
    Legend:  PK = Primary Key │ FK = Foreign Key │ ──► = Many-to-One
```

### Relationships Summary

| Relationship | Type | Description |
|-------------|------|-------------|
| `User` → `Product` | One-to-Many | A seller can list many products |
| `User` → `Order` | One-to-Many | A buyer can place many orders |
| `User` → `CartItem` | One-to-Many | A buyer can have many items in cart |
| `User` → `Review` | One-to-Many | A buyer can write many reviews |
| `User` → `OrderItem` | One-to-Many | A seller is referenced in many order items |
| `Product` → `OrderItem` | One-to-Many | A product can appear in many order items |
| `Product` → `CartItem` | One-to-Many | A product can be in many carts |
| `Product` → `Review` | One-to-Many | A product can have many reviews |
| `Order` → `OrderItem` | One-to-Many | An order contains many line items |

---

## API Endpoints

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | Landing page with product browse, search & category filter |
| `GET` | `/products/{id}` | Product detail page with reviews and ratings |
| `GET` | `/auth/login` | Login page |
| `GET` | `/auth/register` | Registration role selection (Buyer / Seller) |
| `GET` | `/auth/register/buyer` | Buyer registration form |
| `POST` | `/auth/register/buyer` | Submit buyer registration |
| `GET` | `/auth/register/seller` | Seller registration form |
| `POST` | `/auth/register/seller` | Submit seller registration |
| `GET` | `/auth/access-denied` | Access denied error page |

### Buyer Endpoints (Role: `BUYER`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/buyer/dashboard` | Buyer dashboard with product browse |
| `GET` | `/buyer/profile` | View profile |
| `POST` | `/buyer/profile` | Update name / email |
| `GET` | `/buyer/cart` | View shopping cart |
| `POST` | `/buyer/cart/add` | Add product to cart |
| `POST` | `/buyer/cart/{id}/update` | Update cart item quantity |
| `POST` | `/buyer/cart/{id}/remove` | Remove item from cart |
| `GET` | `/buyer/checkout` | Checkout page |
| `POST` | `/buyer/checkout` | Submit shipping address |
| `GET` | `/buyer/payment` | Payment page (SSLCommerz) |
| `POST` | `/buyer/payment` | Initiate SSLCommerz payment |
| `POST` | `/buyer/payment/success` | Payment success callback* |
| `POST` | `/buyer/payment/fail` | Payment failure callback* |
| `POST` | `/buyer/payment/cancel` | Payment cancel callback* |
| `GET` | `/buyer/my-orders` | View order history |
| `GET` | `/buyer/my-orders/{id}` | View order details |
| `POST` | `/buyer/review/{productId}` | Submit / update product review |

> *Payment callbacks are server-to-server from SSLCommerz — exempt from CSRF and authentication.

### Seller Endpoints (Role: `SELLER`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/seller/dashboard` | Seller dashboard |
| `GET` | `/seller/my-products` | List seller's products |
| `GET` | `/seller/products/add` | Add product form |
| `POST` | `/seller/products` | Create new product |
| `GET` | `/seller/products/{id}/edit` | Edit product form |
| `PUT` | `/seller/products/{id}` | Update product |
| `DELETE` | `/seller/products/{id}` | Delete product |
| `GET` | `/seller/my-orders` | Orders containing seller's products |
| `POST` | `/seller/my-orders/{id}/status` | Update order status |
| `GET` | `/seller/analytics` | Sales analytics dashboard |

### Admin Endpoints (Role: `ADMIN`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/admin/dashboard` | Admin dashboard with platform overview |
| `GET` | `/admin/users` | List all users |
| `GET` | `/admin/users/{id}` | User detail page |
| `POST` | `/admin/sellers/{id}/approve` | Approve seller registration |
| `POST` | `/admin/sellers/{id}/reject` | Reject seller registration |
| `POST` | `/admin/users/{id}/disable` | Disable a user account |
| `POST` | `/admin/users/{id}/enable` | Re-enable a user account |
| `GET` | `/admin/products` | List all products |
| `DELETE` | `/admin/products/{id}` | Remove a product |
| `GET` | `/admin/orders` | List all orders |
| `GET` | `/admin/orders/{id}` | Order detail page |
| `GET` | `/admin/statistics` | Platform statistics with charts |

---

## Security

| Feature | Implementation |
|---------|---------------|
| Authentication | Spring Security form-based login with custom `UserDetailsService` |
| Password Storage | BCrypt hashing via `BCryptPasswordEncoder` |
| Authorization | URL-pattern rules (`/admin/**` → ADMIN, `/seller/**` → SELLER, `/buyer/**` → BUYER) |
| CSRF Protection | Enabled globally; exempted only for SSLCommerz server-to-server callbacks |
| Session Management | Server-side sessions; `JSESSIONID` cookie deleted on logout |
| Seller Approval Gate | Sellers with `PENDING` or `REJECTED` status are blocked at login via `CustomUserDetails.isEnabled()` |
| Global Exception Handling | `@ControllerAdvice` catches unhandled exceptions; returns proper error pages |
| Input Validation | Jakarta Bean Validation on all DTOs (`@NotBlank`, `@Email`, `@Size`) |
| Environment Secrets | No hardcoded credentials in Docker — all injected via environment variables |

---

## Run Instructions

### Prerequisites

- **Docker** & **Docker Compose** (for containerized setup)
- **Java 17+** and **Maven 3.9+** (for local development)
- **PostgreSQL 16** (for local development without Docker)

### Run with Docker (Recommended)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/<your-username>/marketx.git
   cd marketx
   ```

2. **Create a `.env` file** in the project root:
   ```env
   DB_NAME=marketx_db
   DB_USER=postgres
   DB_PASSWORD=your_secure_password
   OPENAI_API_KEY=your_openai_key
   APP_BASE_URL=http://localhost:8081
   SSLCOMMERZ_STORE_ID=your_store_id
   SSLCOMMERZ_STORE_PASSWORD=your_store_password
   SSLCOMMERZ_SANDBOX=true
   ```

3. **Build and start the containers:**
   ```bash
   docker compose up --build
   ```

4. **Access the application:**
   Open [http://localhost:8081](http://localhost:8081) in your browser.

5. **Default Admin account** (auto-seeded on first run):
   - Email: `admin@marketx.com`
   - Password: `admin123`

### Run Locally (Development)

1. **Start PostgreSQL** on `localhost:5432` and create a database named `marketx`.

2. **Run the application:**
   ```bash
   ./mvnw spring-boot:run
   ```
   The app starts on [http://localhost:8080](http://localhost:8080).

### Run Tests

```bash
# Unit tests only
./mvnw test

# Unit tests + Integration tests
./mvnw verify
```

Tests use an **H2 in-memory database** — no PostgreSQL required for testing.

---

## CI/CD Pipeline

### Pipeline Diagram

```
 ┌──────────┐     ┌──────────┐     ┌───────────────┐     ┌──────────────────┐
 │  Developer│     │  GitHub   │     │ GitHub Actions │     │     Render       │
 │  pushes   │────►│  Repo     │────►│  CI Pipeline   │────►│  Cloud Deploy    │
 │  code     │     │           │     │                │     │                  │
 └──────────┘     └──────────┘     └───────────────┘     └──────────────────┘

                    Feature Branch         │                       │
                    ───────────────►  Build + Test                 │
                                                                   │
                    Pull Request           │                       │
                    ───────────────►  Build + Test                 │
                                                                   │
                    Merge to main          │                       │
                    ───────────────►  Build + Test ──► Deploy Hook │
                                                       (curl POST)│
                                                           │       │
                                                           ▼       ▼
                                                    Render pulls latest
                                                    code, builds Docker
                                                    image, deploys to
                                                    production URL
```

### Pipeline Stages

The CI/CD pipeline is defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml) and runs on **GitHub Actions**.

| Stage | Trigger | What Happens |
|-------|---------|-------------|
| **1. Checkout** | Push to `develop`, `main`, `feature/**`; PR to `develop`, `main` | Clones the repository |
| **2. Setup JDK** | Same | Installs Temurin JDK 22 with Maven dependency caching |
| **3. Build & Test** | Same | Runs `mvn -B clean verify` — compiles code, runs all unit tests and integration tests |
| **4. Deploy** | Only on `main` branch + all tests pass | Sends a POST request to the Render deploy hook, triggering a production deployment |

**Key points:**
- Every push and pull request triggers the full build-and-test pipeline.
- Deployment **only** happens on the `main` branch after a successful build.
- The Render deploy hook URL is stored as a **GitHub Secret** (`RENDER_DEPLOY_HOOK`) — never exposed in code.
- Maven dependencies are **cached** between runs for faster CI execution.

---

## Git Workflow

```
main (protected) ◄──── Pull Request (requires review) ◄──── develop ◄──── feature/*
```

| Branch | Purpose | Rules |
|--------|---------|-------|
| `main` | Production-ready code | Protected — no direct pushes; merge via PR with review approval |
| `develop` | Integration branch | Receives feature merges; CI runs on every push |
| `feature/*` | Individual features | Created from `develop`; merged back via PR |

---

## Project Structure

```
marketx/
├── .github/workflows/ci.yml        # GitHub Actions CI/CD pipeline
├── Dockerfile                       # Multi-stage Docker build
├── docker-compose.yml               # App + PostgreSQL orchestration
├── pom.xml                          # Maven build configuration
│
└── src/
    ├── main/
    │   ├── java/com/marketx/marketplace/
    │   │   ├── MarketXApplication.java         # Entry point
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java         # Spring Security rules
    │   │   │   └── DataSeeder.java             # Admin auto-seeding
    │   │   ├── controller/
    │   │   │   ├── HomeController.java         # Public landing page
    │   │   │   ├── AuthController.java         # Login & registration
    │   │   │   ├── BuyerController.java        # Buyer operations
    │   │   │   ├── SellerController.java       # Seller operations
    │   │   │   ├── AdminController.java        # Admin operations
    │   │   │   └── ProductController.java      # Product detail (public)
    │   │   ├── dto/                            # Data Transfer Objects
    │   │   ├── entity/                         # JPA Entities & Enums
    │   │   ├── exception/                      # Custom exceptions & handler
    │   │   ├── repository/                     # Spring Data JPA repos
    │   │   ├── security/                       # UserDetails, auth handlers
    │   │   └── service/
    │   │       ├── *Service.java               # Service interfaces
    │   │       └── impl/*ServiceImpl.java      # Implementations
    │   └── resources/
    │       ├── application.yaml                # Main configuration
    │       ├── application-docker.yml          # Docker profile
    │       ├── application-prod.yml            # Production profile
    │       ├── templates/                      # Thymeleaf HTML templates
    │       └── static/                         # CSS, JS, images
    │
    └── test/
        └── java/com/marketx/marketplace/
            ├── repository/                     # Repository unit tests
            ├── service/impl/                   # Service unit tests
            └── controller/                     # Controller integration tests
```

---

## Testing Strategy

| Type | Framework | Scope | Count |
|------|-----------|-------|-------|
| **Unit Tests** | JUnit 5 + Mockito | Service layer business logic | 15+ |
| **Repository Tests** | `@DataJpaTest` + H2 | JPA query correctness | 3+ classes |
| **Integration Tests** | `@SpringBootTest` + MockMvc | Full request lifecycle per controller | 5 classes |

**Test execution in CI:**
- All tests run automatically via `mvn verify` on every push and pull request.
- Unit tests execute during the `test` phase; integration tests execute during the `verify` phase (Maven Failsafe plugin).
- Tests use **H2 in-memory database** to ensure fast, isolated, and reproducible runs.

---

<p align="center">
  Built with Spring Boot · Secured with Spring Security · Tested with JUnit & Mockito · Deployed on Render
</p>
