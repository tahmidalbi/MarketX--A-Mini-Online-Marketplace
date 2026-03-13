# MarketX — Two Complete User Journeys Explained

> **Goal of this file:** Walk through exactly what happens in the code, file by file,
> line by line, when a Buyer registers and logs in — and when a Seller registers,
> gets approved by the admin, and logs in. Written in plain language.

---

## PART 1 — BUYER REGISTERS AND LOGS IN

---

### STEP 1 — Buyer opens the site

The buyer types `http://localhost:8081` in the browser.

The browser sends:
```
GET /
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/").permitAll()
```
Spring Security reads this rule first. `/` is in the "allow everyone" list, so no login
is required. The request passes straight through to the controller.

**File:** `HomeController.java`
```java
@GetMapping("/")
public String home(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
        ...
    }
    return "index";
}
```
`Authentication authentication` is `null` here because the buyer has not logged in yet.
So the `if` block is skipped and the method returns the string `"index"`.

Spring Boot sees `"index"` and looks for a file called `index.html` inside
`src/main/resources/templates/`. It finds it, processes it with Thymeleaf,
and sends the final HTML to the browser.

**File:** `templates/index.html` + `templates/fragments/navbar.html`

Every page includes the navbar at the top via this line in `index.html`:
```html
<div th:replace="~{fragments/navbar :: navbar}"></div>
```
`th:replace` tells Thymeleaf: "go to `fragments/navbar.html`, find the piece tagged
`th:fragment="navbar"`, and paste it here."

Inside `navbar.html`, this section decides what to show on the left side of the nav:
```html
<div sec:authorize="!isAuthenticated()">
    <a th:href="@{/auth/login}" class="btn btn-outline">Login</a>
    <a th:href="@{/auth/register}" class="btn btn-primary">Register</a>
</div>
```
`sec:authorize="!isAuthenticated()"` means: only render this `div` if the user is
NOT logged in. Since the buyer is a guest, this renders. The buyer sees two buttons:
**Login** and **Register**.

---

### STEP 2 — Buyer clicks Register

The buyer clicks the Register button. The browser sends:
```
GET /auth/register
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/auth/**").permitAll()
```
Any URL starting with `/auth/` is public. Passes through.

**File:** `AuthController.java`
```java
@GetMapping("/register")
public String showRegisterPage() {
    return "auth/register";
}
```
No logic here at all. Just returns the view name `"auth/register"`.
Thymeleaf renders `templates/auth/register.html`.

**File:** `templates/auth/register.html`

The page shows two clickable cards:
- 🛒 **Buyer** — "Instant access"
- 🏪 **Seller** — "Requires admin approval"

The Buyer card is a normal `<a>` tag pointing to `/auth/register/buyer`.

---

### STEP 3 — Buyer clicks the Buyer card

Browser sends:
```
GET /auth/register/buyer
```

**File:** `AuthController.java`
```java
@GetMapping("/register/buyer")
public String showBuyerRegistrationForm(Model model) {
    model.addAttribute("buyerDto", new BuyerRegistrationDto());
    return "auth/register-buyer";
}
```
`new BuyerRegistrationDto()` creates an empty form object. It has four fields:
`name`, `email`, `password`, `confirmPassword` — all blank at this point.

`model.addAttribute("buyerDto", ...)` puts this empty object into the model.
Thymeleaf can now bind the HTML form inputs to this object's fields.

**File:** `templates/auth/register-buyer.html`
```html
<form th:action="@{/auth/register/buyer}" th:object="${buyerDto}" method="post">
    <input type="text"  th:field="*{name}">
    <input type="email" th:field="*{email}">
    <input type="password" th:field="*{password}">
    <input type="password" th:field="*{confirmPassword}">
    <button type="submit">Create Buyer Account</button>
</form>
```

`th:object="${buyerDto}"` — this form is bound to the `buyerDto` object from the model.

`th:field="*{name}"` — renders as `<input name="name" id="name" value="">`.
The `*{...}` syntax means "read from the current `th:object`", which is `buyerDto`.

`th:action="@{/auth/register/buyer}"` — Thymeleaf builds the form's POST URL,
and **automatically adds a hidden CSRF token field** to the form. This is security
against cross-site request forgery attacks.

The buyer sees a clean registration form.

---

### STEP 4 — Buyer fills in the form and submits

Buyer types: Name = `"Jane"`, Email = `"jane@example.com"`,
Password = `"secret123"`, Confirm Password = `"secret123"`.

Clicks **Create Buyer Account**. Browser sends:
```
POST /auth/register/buyer
Body: name=Jane&email=jane@example.com&password=secret123&confirmPassword=secret123&_csrf=abc...
```

**File:** `SecurityConfig.java`

`/auth/**` is `permitAll()`, so Spring Security lets the POST through without
requiring a login. It still validates the CSRF token embedded by Thymeleaf.

**File:** `AuthController.java`
```java
@PostMapping("/register/buyer")
public String registerBuyer(
        @Valid @ModelAttribute("buyerDto") BuyerRegistrationDto dto,
        BindingResult result,
        Model model,
        RedirectAttributes redirectAttributes) {
```

Spring sees `@ModelAttribute("buyerDto")` and maps the POST body fields into
a `BuyerRegistrationDto` object:
- `dto.name = "Jane"`
- `dto.email = "jane@example.com"`
- `dto.password = "secret123"`
- `dto.confirmPassword = "secret123"`

`@Valid` tells Spring: "now run Bean Validation on this DTO before calling my method."

**File:** `dto/BuyerRegistrationDto.java`
```java
@NotBlank(message = "Name is required")
private String name;           // "Jane" → passes

@NotBlank @Email(message = "Please enter a valid email address")
private String email;          // "jane@example.com" → passes

@NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
private String password;       // "secret123" → passes (length 9)

@NotBlank(message = "Please confirm your password")
private String confirmPassword; // "secret123" → passes
```

All four validations PASS. `BindingResult result` receives no errors.

Back in `AuthController.java`:
```java
if (result.hasErrors()) {
    return "auth/register-buyer"; // would re-show the form with red error messages
}
```
`result.hasErrors()` is `false`, so this block is skipped.

```java
try {
    userService.registerBuyer(dto);
} catch (UserAlreadyExistsException | PasswordMismatchException e) {
    model.addAttribute("errorMessage", e.getMessage());
    return "auth/register-buyer";
}
```
`userService` is the interface `UserService`. At runtime, Spring injects
`UserServiceImpl` here (because it is the `@Service` that implements `UserService`).

---

### STEP 5 — Service validates and saves the buyer

**File:** `service/impl/UserServiceImpl.java`
```java
@Override
@Transactional
public void registerBuyer(BuyerRegistrationDto dto) {
```

`@Transactional` means: everything inside this method runs inside a single
database transaction. If anything fails, all DB changes are rolled back automatically.

```java
    if (userRepository.existsByEmail(dto.getEmail())) {
        throw new UserAlreadyExistsException(
            "An account with email '" + dto.getEmail() + "' already exists.");
    }
```

**File:** `repository/UserRepository.java`
```java
boolean existsByEmail(String email);
```
Spring Data JPA reads the method name `existsByEmail` and generates this SQL:
```sql
SELECT COUNT(*) > 0 FROM users WHERE email = 'jane@example.com'
```
Result: `false` (no account with that email yet). No exception is thrown. Good.

```java
    if (!dto.getPassword().equals(dto.getConfirmPassword())) {
        throw new PasswordMismatchException("Passwords do not match.");
    }
```
`"secret123".equals("secret123")` is `true`, so `!true` is `false`. No exception.

```java
    User buyer = User.builder()
            .name(dto.getName())
            .email(dto.getEmail())
            .password(passwordEncoder.encode(dto.getPassword()))
            .role(Role.BUYER)
            .approvalStatus(ApprovalStatus.APPROVED)
            .build();
```

`passwordEncoder.encode("secret123")` runs BCrypt — it hashes the password into
something like `$2a$10$N9qo8uLOick...` (60 characters). The raw password is
**never stored**. Even if someone steals the database, they cannot reverse a BCrypt hash.

`role(Role.BUYER)` — this user is a buyer.
`approvalStatus(ApprovalStatus.APPROVED)` — **buyers are approved immediately**. No waiting.

`@Builder` is a Lombok annotation on `User.java`. Lombok auto-generates
the `.builder()`, `.name()`, `.email()`... `.build()` methods at compile time.

```java
    userRepository.save(buyer);
```

**File:** `repository/UserRepository.java` (inherited from `JpaRepository`)

Before saving, Hibernate calls `User.java`'s `@PrePersist` method:
```java
@PrePersist
protected void onCreate() {
    this.createdAt = LocalDateTime.now();
}
```
This sets `createdAt` to the current timestamp automatically.

Hibernate then runs:
```sql
INSERT INTO users (name, email, password, role, approval_status, created_at)
VALUES ('Jane', 'jane@example.com', '$2a$10$...', 'BUYER', 'APPROVED', '2026-03-13 10:00:00')
```

The buyer is now in the database.

---

### STEP 6 — Redirect to login with success message

Back in `AuthController.java`:
```java
redirectAttributes.addFlashAttribute("successMessage",
    "Registration successful! You can now log in.");
return "redirect:/auth/login";
```

The controller does NOT render a page directly. It returns `"redirect:/auth/login"`.
Spring sends HTTP 302 to the browser, which causes the browser to immediately
make a new request: `GET /auth/login`.

`redirectAttributes.addFlashAttribute(...)` stores the success message in the
**session temporarily**. It survives this one redirect, then disappears. This is called
the **Post-Redirect-Get (PRG) pattern**. Without the redirect, pressing F5 would
re-submit the registration form and try to create a duplicate account.

---

### STEP 7 — Login page appears

Browser sends:
```
GET /auth/login
```

**File:** `AuthController.java`
```java
@GetMapping("/login")
public String showLoginPage(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String logout,
        Model model) {

    if (logout != null) {
        model.addAttribute("successMessage", "You have been logged out successfully.");
    }
    return "auth/login";
}
```

`error` is `null` (no error). `logout` is `null` (not a logout redirect).
But `successMessage` is already in the model from the flash attribute.
The login page renders with a green banner: "Registration successful! You can now log in."

---

### STEP 8 — Buyer enters credentials and submits login

Buyer types email `jane@example.com` and password `secret123` and clicks Sign In.

Browser sends:
```
POST /auth/login
Body: email=jane@example.com&password=secret123&_csrf=abc...
```

⚠️ **This POST is NOT handled by `AuthController`.**

**File:** `SecurityConfig.java`
```java
.formLogin(form -> form
    .loginPage("/auth/login")
    .loginProcessingUrl("/auth/login")
    .usernameParameter("email")
    .passwordParameter("password")
    .successHandler(successHandler)
    .failureHandler(failureHandler)
)
```

`.loginProcessingUrl("/auth/login")` tells Spring Security: "intercept POST to
`/auth/login` yourself — don't let the controller see it."

Spring Security's built-in filter `UsernamePasswordAuthenticationFilter` runs.
It reads the `email` field (renamed from the default `username` via `.usernameParameter("email")`).

---

### STEP 9 — Spring Security loads the user from the database

**File:** `security/CustomUserDetailsService.java`
```java
@Override
@Transactional(readOnly = true)
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() ->
                    new UsernameNotFoundException("No account found with email: " + email));
    return new CustomUserDetails(user);
}
```

Spring Security calls this method with `email = "jane@example.com"`.

`userRepository.findByEmail("jane@example.com")` generates:
```sql
SELECT * FROM users WHERE email = 'jane@example.com'
```
Returns the `User` row we inserted in Step 5.

`new CustomUserDetails(user)` wraps the `User` entity in a Spring Security adapter.

---

### STEP 10 — Spring Security checks if the account is allowed to log in

**File:** `security/CustomUserDetails.java`

Spring Security calls four boolean methods on `CustomUserDetails`:

```java
@Override
public boolean isEnabled() {
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING) {
        return false;
    }
    return true;   // ← Jane is BUYER → returns true
}

@Override
public boolean isAccountNonLocked() {
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.REJECTED) {
        return false;
    }
    return true;   // ← Jane is BUYER → returns true
}

@Override
public boolean isAccountNonExpired() { return true; }

@Override
public boolean isCredentialsNonExpired() { return true; }
```

Jane is a `BUYER` with `APPROVED` status, so all four return `true`.

---

### STEP 11 — Password is verified

Spring Security calls:
```
passwordEncoder.matches("secret123", "$2a$10$N9qo8uLOick...")
```
BCrypt re-hashes the submitted password with the same salt embedded in the stored hash
and compares. They match. Authentication **succeeds**.

---

### STEP 12 — Redirected to buyer dashboard

**File:** `security/CustomAuthSuccessHandler.java`
```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request,
                                    HttpServletResponse response,
                                    Authentication authentication) throws IOException {
    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

    String redirectUrl = "/";
    for (GrantedAuthority authority : authorities) {
        redirectUrl = switch (authority.getAuthority()) {
            case "ROLE_ADMIN"  -> "/admin/dashboard";
            case "ROLE_SELLER" -> "/seller/dashboard";
            case "ROLE_BUYER"  -> "/buyer/dashboard";
            default            -> "/";
        };
        break;
    }
    response.sendRedirect(redirectUrl);
}
```

Where does `authority.getAuthority()` get `"ROLE_BUYER"` from?

**File:** `security/CustomUserDetails.java`
```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
}
```
`user.getRole()` returns `Role.BUYER`. `.name()` returns the string `"BUYER"`.
`"ROLE_" + "BUYER"` = `"ROLE_BUYER"`.

The switch matches `"ROLE_BUYER"` → `redirectUrl = "/buyer/dashboard"`.
`response.sendRedirect("/buyer/dashboard")` — browser follows the redirect.

---

### STEP 13 — Buyer sees their dashboard

Browser sends:
```
GET /buyer/dashboard
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/buyer/**").hasRole("BUYER")
```
Jane has `ROLE_BUYER`. Check passes. Request reaches the controller.

**File:** `controller/BuyerController.java`
```java
@GetMapping("/dashboard")
public String showDashboard(Authentication authentication, Model model) {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    model.addAttribute("user", userDetails.getUser());
    return "buyer/dashboard";
}
```

`authentication.getPrincipal()` returns the `CustomUserDetails` object that was
created back in Step 9. `.getUser()` returns the `User` entity.

`model.addAttribute("user", ...)` puts the User entity into the model.

**File:** `templates/buyer/dashboard.html`
```html
<h2>Welcome, <span th:text="${user.name}"></span>!</h2>
```
`${user.name}` → `"Jane"`. The browser renders: **"Welcome, Jane!"**

The navbar now shows:
```html
<div class="nav-user-info" sec:authorize="isAuthenticated()">
    Welcome, <strong sec:authentication="principal.user.name"></strong>
```
`sec:authentication="principal.user.name"`:
- `principal` = the `CustomUserDetails` in the security context
- `.user` = calls `CustomUserDetails.getUser()` → the `User` entity
- `.name` = calls `User.getName()` (Lombok-generated getter) → `"Jane"`

The buyer is now fully logged in and sees their dashboard. ✅

---
---

## PART 2 — SELLER REGISTERS, GETS APPROVED, AND LOGS IN

---

### STEP 1 — Seller navigates to registration

Same as Buyer Steps 1–2. The seller opens the site, clicks Register,
sees the role-selection page. This time they click the **Seller** card
(`🏪 Seller — Requires admin approval`).

Browser sends:
```
GET /auth/register/seller
```

**File:** `AuthController.java`
```java
@GetMapping("/register/seller")
public String showSellerRegistrationForm(Model model) {
    model.addAttribute("sellerDto", new SellerRegistrationDto());
    return "auth/register-seller";
}
```

An empty `SellerRegistrationDto` is created and put in the model.

**File:** `templates/auth/register-seller.html`

The page has the same form fields as the buyer form, PLUS an info box:
```html
<div class="alert alert-info">
    Seller accounts require <strong>admin approval</strong> before you can log in.
    You will be notified once your account is reviewed.
</div>
```
The seller reads this warning and understands they'll have to wait.

---

### STEP 2 — Seller fills in and submits the form

Seller: Name = `"Sam"`, Email = `"sam@shop.com"`,
Password = `"pass123"`, Confirm = `"pass123"`.

Browser sends:
```
POST /auth/register/seller
Body: name=Sam&email=sam@shop.com&password=pass123&confirmPassword=pass123&_csrf=...
```

**File:** `AuthController.java`
```java
@PostMapping("/register/seller")
public String registerSeller(
        @Valid @ModelAttribute("sellerDto") SellerRegistrationDto dto,
        BindingResult result,
        Model model,
        RedirectAttributes redirectAttributes) {

    if (result.hasErrors()) {
        return "auth/register-seller";
    }

    try {
        userService.registerSeller(dto);
    } catch (UserAlreadyExistsException | PasswordMismatchException e) {
        model.addAttribute("errorMessage", e.getMessage());
        return "auth/register-seller";
    }

    redirectAttributes.addFlashAttribute("successMessage",
        "Seller registration submitted! Please wait for admin approval before logging in.");
    return "redirect:/auth/login";
}
```

Bean Validation passes (all fields valid). `userService.registerSeller(dto)` is called.

---

### STEP 3 — Service saves the seller as PENDING

**File:** `service/impl/UserServiceImpl.java`
```java
@Override
@Transactional
public void registerSeller(SellerRegistrationDto dto) {
    if (userRepository.existsByEmail(dto.getEmail())) {
        throw new UserAlreadyExistsException(...);
    }
    if (!dto.getPassword().equals(dto.getConfirmPassword())) {
        throw new PasswordMismatchException("Passwords do not match.");
    }

    User seller = User.builder()
            .name(dto.getName())
            .email(dto.getEmail())
            .password(passwordEncoder.encode(dto.getPassword()))
            .role(Role.SELLER)
            .approvalStatus(ApprovalStatus.PENDING)    // ← KEY DIFFERENCE from Buyer
            .build();

    userRepository.save(seller);
}
```

Email check passes. Password match passes.

`approvalStatus(ApprovalStatus.PENDING)` — this is the critical difference.
A seller starts as `PENDING`. They cannot log in until an admin changes this to `APPROVED`.

Hibernate inserts:
```sql
INSERT INTO users (name, email, password, role, approval_status, created_at)
VALUES ('Sam', 'sam@shop.com', '$2a$10$...', 'SELLER', 'PENDING', now())
```

The seller record is in the database with status `PENDING`.

---

### STEP 4 — Seller is redirected to login with a pending message

```java
redirectAttributes.addFlashAttribute("successMessage",
    "Seller registration submitted! Please wait for admin approval before logging in.");
return "redirect:/auth/login";
```

The browser follows the redirect to `GET /auth/login`.
The login page shows a green box: "Seller registration submitted! Please wait..."

---

### STEP 5 — Seller tries to log in (and gets blocked)

The seller, impatient, tries to log in immediately.

Browser sends:
```
POST /auth/login
Body: email=sam@shop.com&password=pass123
```

Spring Security intercepts. Calls `CustomUserDetailsService.loadUserByUsername("sam@shop.com")`.

SQL:
```sql
SELECT * FROM users WHERE email = 'sam@shop.com'
```
Returns Sam's user row: `role = 'SELLER'`, `approval_status = 'PENDING'`.

Returns: `new CustomUserDetails(user)` wrapping Sam's User entity.

Spring Security now checks the boolean methods:

**File:** `security/CustomUserDetails.java`
```java
@Override
public boolean isEnabled() {
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING) {
        return false;   // ← Sam is SELLER + PENDING → returns false!
    }
    return true;
}
```

`user.getRole() == Role.SELLER` → `true`
`user.getApprovalStatus() == ApprovalStatus.PENDING` → `true`
Both conditions are true → `return false`

Spring Security sees `isEnabled() = false` and throws:
```
DisabledException: User is disabled
```

---

### STEP 6 — Failure handler maps the exception to a URL parameter

**File:** `security/CustomAuthFailureHandler.java`
```java
@Override
public void onAuthenticationFailure(HttpServletRequest request,
                                    HttpServletResponse response,
                                    AuthenticationException exception) throws IOException {
    String errorParam;
    if (exception instanceof DisabledException) {
        errorParam = "pending";
    } else if (exception instanceof LockedException) {
        errorParam = "rejected";
    } else {
        errorParam = "invalid";
    }
    response.sendRedirect("/auth/login?error=" + errorParam);
}
```

`exception instanceof DisabledException` → `true` → `errorParam = "pending"`

Browser is redirected to `/auth/login?error=pending`.

---

### STEP 7 — Login page shows the pending message

**File:** `AuthController.java`
```java
@GetMapping("/login")
public String showLoginPage(
        @RequestParam(required = false) String error,
        ...) {

    if (error != null) {
        String message = switch (error) {
            case "pending"  -> "Your seller account is pending admin approval. Please wait.";
            case "rejected" -> "Your seller account has been rejected by the admin.";
            default         -> "Invalid email or password. Please try again.";
        };
        model.addAttribute("errorMessage", message);
    }
    return "auth/login";
}
```

`error = "pending"` → switch matches → message = `"Your seller account is pending admin approval. Please wait."`

The login page shows a red error box with that message. The seller must wait.

---

### STEP 8 — Admin logs in

Meanwhile, the admin opens the site and logs in.

```
POST /auth/login
Body: email=albitahmid@gmail.com&password=rafiqul25
```

Spring Security loads the admin from the DB (seeded at app startup by `DataSeeder.java`).

`CustomUserDetails` for the admin:
- `isEnabled()` → not a SELLER → `return true`
- `isAccountNonLocked()` → not a SELLER → `return true`
- Password check: `passwordEncoder.matches("rafiqul25", stored_hash)` → `true`

Authentication succeeds.

**File:** `security/CustomAuthSuccessHandler.java`

`authority.getAuthority()` = `"ROLE_ADMIN"` → `redirectUrl = "/admin/dashboard"`

The admin is redirected to `/admin/dashboard`.

---

### STEP 9 — Admin sees the pending sellers dashboard

Browser sends:
```
GET /admin/dashboard
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/admin/**").hasRole("ADMIN")
```
Admin has `ROLE_ADMIN`. Passes.

**File:** `controller/AdminController.java`
```java
@GetMapping("/dashboard")
public String showDashboard(Model model) {
    model.addAttribute("pendingSellers", adminService.getPendingSellerRegistrations());
    return "admin/dashboard";
}
```

**File:** `service/impl/AdminServiceImpl.java`
```java
@Override
@Transactional(readOnly = true)
public List<User> getPendingSellerRegistrations() {
    return userRepository.findByRoleAndApprovalStatus(Role.SELLER, ApprovalStatus.PENDING);
}
```

**File:** `repository/UserRepository.java`
```java
List<User> findByRoleAndApprovalStatus(Role role, ApprovalStatus approvalStatus);
```

Spring Data JPA reads the method name and generates:
```sql
SELECT * FROM users WHERE role = 'SELLER' AND approval_status = 'PENDING'
```

Returns a list containing Sam's User object.

Back in `AdminController`:
`model.addAttribute("pendingSellers", [Sam])` — list with one entry.
Returns `"admin/dashboard"`.

**File:** `templates/admin/dashboard.html`
```html
<tr th:each="seller, stat : ${pendingSellers}">
    <td th:text="${seller.name}"></td>           <!--  Sam        -->
    <td th:text="${seller.email}"></td>          <!--  sam@shop.com -->
    <td th:text="${#temporals.format(seller.createdAt, 'dd MMM yyyy, HH:mm')}"></td>

    <td>
        <form th:action="@{/admin/sellers/{id}/approve(id=${seller.id})}" method="post">
            <button type="submit" class="btn btn-success">Approve</button>
        </form>
        <form th:action="@{/admin/sellers/{id}/reject(id=${seller.id})}" method="post">
            <button type="submit" class="btn btn-danger">Reject</button>
        </form>
    </td>
</tr>
```

`th:each="seller, stat : ${pendingSellers}"` — loops over the list.
For Sam, it fills in his name, email, creation date.

`th:action="@{/admin/sellers/{id}/approve(id=${seller.id})}"` — Thymeleaf builds
the URL with Sam's actual database ID, e.g., `/admin/sellers/2/approve`.
Again, CSRF token is auto-injected into both forms.

The admin sees a row for Sam with two buttons: **Approve** and **Reject**.

---

### STEP 10 — Admin clicks Approve

The admin clicks the green Approve button for Sam.

Browser sends:
```
POST /admin/sellers/2/approve
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/admin/**").hasRole("ADMIN")
```
Admin is logged in with `ROLE_ADMIN`. Passes.

**File:** `controller/AdminController.java`
```java
@PostMapping("/sellers/{id}/approve")
public String approveSeller(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    adminService.approveSeller(id);
    redirectAttributes.addFlashAttribute("successMessage", "Seller account approved successfully.");
    return "redirect:/admin/dashboard";
}
```

`@PathVariable Long id` — Spring extracts `2` from the URL path `/admin/sellers/2/approve`.

---

### STEP 11 — Service changes the seller's status to APPROVED

**File:** `service/impl/AdminServiceImpl.java`
```java
@Override
@Transactional
public void approveSeller(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() ->
                    new EntityNotFoundException("User not found with id: " + userId));
    user.setApprovalStatus(ApprovalStatus.APPROVED);
    userRepository.save(user);
}
```

`userRepository.findById(2)` generates:
```sql
SELECT * FROM users WHERE id = 2
```
Returns Sam's User object.

`user.setApprovalStatus(ApprovalStatus.APPROVED)` — changes the field in memory.

`userRepository.save(user)` — Hibernate detects the changed field and generates:
```sql
UPDATE users SET approval_status = 'APPROVED' WHERE id = 2
```

Sam's row in the database now has `approval_status = 'APPROVED'`.

---

### STEP 12 — Admin is redirected back to dashboard

```java
redirectAttributes.addFlashAttribute("successMessage", "Seller account approved successfully.");
return "redirect:/admin/dashboard";
```

The admin's dashboard reloads. Sam is no longer in the pending list
(because the SQL `WHERE approval_status = 'PENDING'` no longer matches Sam).
A green banner shows: "Seller account approved successfully."

---

### STEP 13 — Sam tries to log in again

Sam tries again now.

Browser sends:
```
POST /auth/login
Body: email=sam@shop.com&password=pass123
```

Spring Security calls `CustomUserDetailsService.loadUserByUsername("sam@shop.com")`.

SQL:
```sql
SELECT * FROM users WHERE email = 'sam@shop.com'
```

Returns Sam's updated row: `role = 'SELLER'`, `approval_status = 'APPROVED'`.

Returns: `new CustomUserDetails(user)`.

Spring Security checks:

**File:** `security/CustomUserDetails.java`
```java
@Override
public boolean isEnabled() {
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.PENDING) {
        return false;
    }
    return true;   // ← Sam is SELLER but APPROVED now → condition is false → returns true!
}

@Override
public boolean isAccountNonLocked() {
    if (user.getRole() == Role.SELLER && user.getApprovalStatus() == ApprovalStatus.REJECTED) {
        return false;
    }
    return true;   // ← Sam is not REJECTED → returns true
}
```

`user.getApprovalStatus() == ApprovalStatus.PENDING` → `false` (Sam is APPROVED now).
So the condition `SELLER && PENDING` is `false`. Method returns `true`.

Both booleans return `true`. Password check passes. Authentication **succeeds**.

---

### STEP 14 — Sam is redirected to the seller dashboard

**File:** `security/CustomAuthSuccessHandler.java`

`getAuthorities()` returns `["ROLE_SELLER"]`.

```java
case "ROLE_SELLER" -> "/seller/dashboard";
```

`response.sendRedirect("/seller/dashboard")`.

Browser sends:
```
GET /seller/dashboard
```

**File:** `SecurityConfig.java`
```java
.requestMatchers("/seller/**").hasRole("SELLER")
```
Sam has `ROLE_SELLER`. Passes.

**File:** `controller/SellerController.java`
```java
@GetMapping("/dashboard")
public String showDashboard(Authentication authentication, Model model) {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    model.addAttribute("user", userDetails.getUser());
    return "seller/dashboard";
}
```

`authentication.getPrincipal()` retrieves the `CustomUserDetails` from the security context.
`.getUser()` gives back Sam's `User` entity.

**File:** `templates/seller/dashboard.html`
```html
<h2>Welcome, <span th:text="${user.name}"></span>!</h2>
<p>You are logged in as a <strong>Seller</strong>.</p>
```

The browser renders: **"Welcome, Sam!"** ✅

Sam is now fully logged in as an approved seller.

---

## Summary Table

| Step | Who | Action | File Involved | Key Line |
|------|-----|--------|--------------|---------|
| 1 | Buyer | Opens site | `HomeController` | `return "index"` |
| 2 | Buyer | Clicks Register | `AuthController` | `return "auth/register"` |
| 3 | Buyer | Opens buyer form | `AuthController` | `model.addAttribute("buyerDto", new BuyerRegistrationDto())` |
| 4 | Buyer | Submits form | `AuthController` | `@Valid @ModelAttribute BuyerRegistrationDto dto` |
| 5 | System | Validates + saves | `UserServiceImpl` | `approvalStatus(ApprovalStatus.APPROVED)` |
| 6 | System | Redirects | `AuthController` | `return "redirect:/auth/login"` |
| 7 | Buyer | Sees login page | `AuthController` | flash `successMessage` |
| 8 | Buyer | Submits login | Spring Security | `loginProcessingUrl("/auth/login")` |
| 9 | Spring Security | Loads user | `CustomUserDetailsService` | `userRepository.findByEmail(email)` |
| 10 | Spring Security | Checks booleans | `CustomUserDetails` | `isEnabled()` → `true` |
| 11 | Spring Security | Checks password | Spring Security | `passwordEncoder.matches(...)` |
| 12 | System | Redirects by role | `CustomAuthSuccessHandler` | `case "ROLE_BUYER" -> "/buyer/dashboard"` |
| 13 | Buyer | Sees dashboard | `BuyerController` | `return "buyer/dashboard"` |
| — | — | — | — | — |
| S1 | Seller | Registers | `UserServiceImpl` | `approvalStatus(ApprovalStatus.PENDING)` |
| S2 | Seller | Tries login | `CustomUserDetails` | `isEnabled()` → `false` (PENDING) |
| S3 | System | Blocks login | `CustomAuthFailureHandler` | `errorParam = "pending"` |
| S4 | Admin | Logs in | `CustomAuthSuccessHandler` | `case "ROLE_ADMIN" -> "/admin/dashboard"` |
| S5 | Admin | Sees pending sellers | `AdminServiceImpl` | `findByRoleAndApprovalStatus(SELLER, PENDING)` |
| S6 | Admin | Approves seller | `AdminServiceImpl` | `user.setApprovalStatus(ApprovalStatus.APPROVED)` |
| S7 | Seller | Tries login again | `CustomUserDetails` | `isEnabled()` → `true` (now APPROVED) |
| S8 | System | Redirects by role | `CustomAuthSuccessHandler` | `case "ROLE_SELLER" -> "/seller/dashboard"` |
| S9 | Seller | Sees dashboard | `SellerController` | `return "seller/dashboard"` |
