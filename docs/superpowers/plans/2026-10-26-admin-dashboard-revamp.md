# Admin Dashboard & Host Intelligence Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revamp the StayHub Admin Portal into a comprehensive, market-standard multi-tab operations dashboard featuring an executive overview with deep-dive host performance metrics, actionable anomaly alerts, and a dedicated host profile view with specialized sub-tabs and RESTful endpoints.

**Architecture:**
- Backend: Modular Spring Boot service layer aggregating cross-domain metrics (User, Property, Booking, Payment, Review) into specialized DTOs for platform overview, host performance, and anomaly detection.
- API: Dedicated RESTful endpoints under `/api/v1/admin/**` providing JSON responses wrapped in `ApiResponse<T>` for asynchronous tab navigation and drill-down operations.
- Frontend: Thymeleaf template with Tailwind CSS and Alpine.js providing a reactive multi-tab navigation layout (`dashboard.html`) and a specialized host deep-dive page (`host-detail.html`) with breadcrumb navigation.

**Tech Stack:** Java 21, Spring Boot 3.3.2, Spring Data JPA / Hibernate 6, Thymeleaf 3, Tailwind CSS 3, Alpine.js, JUnit 5, Mockito, Spring Security 6.

**Spec:** User requirements from conversation history (2026-10-26): Multi-tab Admin Dashboard with Host Performance and Anomaly Alerts on initial load, dedicated Host Drill-down page with 5 sub-tabs (Financials, Properties, Bookings, Reviews, Anomalies), and dedicated REST API endpoints.

## Global Constraints
- Do not break existing `/admin/bookings` or `/admin/users` URL paths (maintain backwards-compatible alias or redirection).
- All `/api/v1/admin/**` endpoints must require `ROLE_ADMIN` and return standard `ApiResponse<T>`.
- Format all currency figures in Vietnamese Dong (VND) with thousands separators.
- Preserve zero-suppression and null-safety when aggregating metrics across hosts with zero bookings or zero properties.
- Code style: Feature-based packaging within `com.stayhub.admin`, DTO separation from JPA entities, no direct entity leaks to views.

## Review Focus
1. Null/Empty Safety: Host with 0 properties or 0 bookings must display 0 VND, 0 bookings, and "N/A" rating without NullPointerException.
2. Anomaly Calculation Edge Cases: Cancellation rate calculation must not divide by zero when total bookings is 0.
3. Pagination & Search Boundaries: Page index out of bounds or negative page size must be safely clamped.
4. Security & Role Isolation: Non-admin users must receive 403 Forbidden on both View and REST endpoints.
5. Bidirectional Host Status Mutation: Locking/Unlocking a host from the detail page must update immediately and reflect in subsequent queries.

---

### Task 1: Create Admin DTOs for Host Performance, Anomalies, and Overview

**Files:**
- Create: `src/main/java/com/stayhub/admin/dto/HostPerformanceSummaryDto.java`
- Create: `src/main/java/com/stayhub/admin/dto/AnomalyAlertDto.java`
- Create: `src/main/java/com/stayhub/admin/dto/HostDetailDto.java`
- Create: `src/main/java/com/stayhub/admin/dto/AdminOverviewDto.java`
- Create: `src/main/java/com/stayhub/admin/dto/PropertyAdminViewDto.java`

- [x] Step 1: Create `HostPerformanceSummaryDto` containing host ID, name, email, phone, status, avatar, propertiesCount, bookingsCount, completedBookingsCount, cancelledBookingsCount, grossHostRevenue, commissionEarned, and averageRating.
- [x] Step 2: Create `AnomalyAlertDto` with fields: `type` (SUSPICIOUS_CANCELLATION, LOW_RATING, EXPIRED_PENDING_BOOKING, POTENTIAL_SELF_BOOKING), `severity` (HIGH, MEDIUM, LOW), `entityType`, `entityId`, `title`, `description`, `createdAt`.
- [x] Step 3: Create `HostDetailDto` encapsulating `hostProfile`, `properties` list, `recentBookings` list, `reviews` list, and `anomalies` list.
- [x] Step 4: Create `AdminOverviewDto` holding platform KPIs (GMV, Platform Commission, Active Hosts, Total Bookings, Active Properties) plus lists of top hosts and urgent anomaly alerts.
- [x] Step 5: Create `PropertyAdminViewDto` holding listing details for admin inspection (id, title, hostName, hostEmail, city, pricePerNight, status, ratingAvg, totalBookings).
- [x] Step 6: Verify compilation with `./mvnw test-compile`.

---

### Task 2: Extend Repositories for Host Performance and Anomaly Queries

**Files:**
- Modify: `src/main/java/com/stayhub/property/PropertyRepository.java`
- Modify: `src/main/java/com/stayhub/booking/BookingRepository.java`
- Modify: `src/main/java/com/stayhub/review/ReviewRepository.java`

- [x] Step 1: Add queries in `PropertyRepository`:
  - `long countByHostId(Long hostId);`
  - `List<Property> findByHostId(Long hostId);`
  - `List<Property> findByRatingAvgLessThanAndStatus(BigDecimal ratingThreshold, PropertyStatus status);`
- [x] Step 2: Add queries in `BookingRepository`:
  - `List<Booking> findByPropertyHostIdOrderByCreatedAtDesc(Long hostId);`
  - `List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant threshold);`
  - `long countByPropertyHostIdAndStatus(Long hostId, BookingStatus status);`
- [x] Step 3: Add queries in `ReviewRepository`:
  - Query reviews by property host: `List<Review> findByPropertyHostIdOrderByCreatedAtDesc(@Param("hostId") Long hostId);`
- [x] Step 4: Verify queries compile cleanly with `./mvnw test-compile`.

---

### Task 3: Implement Business Logic in AdminService

**Files:**
- Modify: `src/main/java/com/stayhub/admin/AdminService.java`
- Modify: `src/main/java/com/stayhub/admin/AdminServiceImpl.java`
- Create: `src/test/java/com/stayhub/admin/AdminServiceExtendedTest.java`

- [x] Step 1: Add method signatures to `AdminService`:
  - `AdminOverviewDto getOverviewData();`
  - `Page<HostPerformanceSummaryDto> getHostPerformances(String keyword, String status, Pageable pageable);`
  - `HostDetailDto getHostDetail(Long hostId);`
  - `List<AnomalyAlertDto> getSystemAnomalies();`
  - `Page<PropertyAdminViewDto> getPropertiesAdmin(String keyword, String status, String city, Pageable pageable);`
- [x] Step 2: Implement `getOverviewData()` in `AdminServiceImpl`:
  - Aggregate total GMV (sum of `totalPrice` from CONFIRMED and COMPLETED bookings).
  - Aggregate total platform commission (sum of `serviceFee`).
  - Count active hosts, total bookings, active properties.
  - Collect top hosts and system anomaly alerts.
- [x] Step 3: Implement `getHostPerformances(keyword, status, pageable)`:
  - Query hosts (`UserRole.HOST`), filter by keyword and status.
  - Compute each host's property count, booking volume, completed bookings, gross revenue, commission, and average rating.
  - Return paginated `HostPerformanceSummaryDto`.
- [x] Step 4: Implement `getHostDetail(hostId)`:
  - Fetch host profile or throw `ResourceNotFoundException`.
  - Fetch host's properties, bookings, and customer reviews.
  - Compute financial summary and detect host-specific anomalies (e.g. cancellation rate > 30%, properties with rating < 3.0).
- [x] Step 5: Implement `getSystemAnomalies()`:
  - Detect bookings pending payment past 30 minutes.
  - Detect properties with low ratings (< 3.0).
  - Detect bookings cancelled within 24 hours of check-in.
- [x] Step 6: Write unit tests in `AdminServiceExtendedTest` testing zero bookings, normal calculations, and anomaly detection.
- [x] Step 7: Run `./mvnw test -Dtest=AdminServiceExtendedTest` to ensure all tests pass.

---

### Task 4: Implement REST Endpoints in AdminRestController

**Files:**
- Modify: `src/main/java/com/stayhub/admin/AdminRestController.java`
- Create: `src/test/java/com/stayhub/admin/AdminRestControllerTest.java`

- [ ] Step 1: Add `GET /api/v1/admin/dashboard/overview` returning `ApiResponse<AdminOverviewDto>`.
- [ ] Step 2: Add `GET /api/v1/admin/hosts` accepting query params `keyword`, `status`, `page`, `size` returning `ApiResponse<Page<HostPerformanceSummaryDto>>`.
- [ ] Step 3: Add `GET /api/v1/admin/hosts/{id}` returning `ApiResponse<HostDetailDto>`.
- [ ] Step 4: Add `GET /api/v1/admin/anomalies` returning `ApiResponse<List<AnomalyAlertDto>>`.
- [ ] Step 5: Add `GET /api/v1/admin/properties` returning `ApiResponse<Page<PropertyAdminViewDto>>`.
- [ ] Step 6: Write mock MVC tests in `AdminRestControllerTest` checking 200 OK responses with proper JSON structure and 403 Forbidden for unauthenticated users.
- [ ] Step 7: Run `./mvnw test -Dtest=AdminRestControllerTest` to ensure all tests pass.

---

### Task 5: Implement MVC Controller Routes in AdminController

**Files:**
- Modify: `src/main/java/com/stayhub/admin/AdminController.java`

- [ ] Step 1: Add `GET /admin` redirecting to `/admin/dashboard`.
- [ ] Step 2: Add `GET /admin/dashboard` returning `admin/dashboard` template:
  - Inject `overview` (via `adminService.getOverviewData()`).
  - Inject `hostPage` (via `adminService.getHostPerformances("", "", PageRequest.of(0, 10))`).
  - Inject `bookingPage` (via `adminService.getBookings("", "", PageRequest.of(0, 10))`).
  - Inject `propertyPage` (via `adminService.getPropertiesAdmin("", "", "", PageRequest.of(0, 10))`).
  - Inject active tab parameter (default `overview`).
- [ ] Step 3: Add `GET /admin/hosts/{id}` returning `admin/host-detail` template:
  - Fetch `hostDetail` via `adminService.getHostDetail(id)`.
  - Add `hostDetail` to model.
- [ ] Step 4: Keep `/admin/bookings` and `/admin/users` working for backward compatibility.
- [ ] Step 5: Verify controller compiles with `./mvnw test-compile`.

---

### Task 6: Build the Multi-Tab Admin Dashboard Template (`dashboard.html`)

**Files:**
- Create: `src/main/resources/templates/admin/dashboard.html`

- [x] Step 1: Create layout with left sidebar (StayHub Admin logo, navigation links, quick shortcuts) and responsive main content area.
- [x] Step 2: Implement Alpine.js tab controller `x-data="{ activeTab: 'overview' }"` with 5 tab buttons:
  - Tab 1: Tổng quan & Hiệu suất Host (Overview & Hosts)
  - Tab 2: Đơn đặt phòng (Bookings)
  - Tab 3: Chỗ nghỉ (Properties)
  - Tab 4: Tài chính & Ký quỹ (Financials)
  - Tab 5: Người dùng (Users)
- [x] Step 3: Render Tab 1 (Default view):
  - 4 Executive KPI cards (GMV, Commission, Active Hosts, Active Anomalies).
  - Anomaly Alert Banner/Box displaying urgent flags with severity badges.
  - Host Performance Matrix Table with search input, sorting, and pagination. Each host row has a direct link to `/admin/hosts/{id}`.
- [x] Step 4: Render Tab 2 (System Bookings) with keyword search, status filter, and detailed booking table.
- [x] Step 5: Render Tab 3 (Properties Management) with thumbnail, host name, city, price, status badge, and view link.
- [x] Step 6: Render Tab 4 (Financials) showing platform commission, escrow held, and payout status.
- [x] Step 7: Render Tab 5 (User Management) with lock/unlock action buttons powered by AJAX.

---

### Task 7: Build the Dedicated Host Drill-Down Template (`host-detail.html`)

**Files:**
- Create: `src/main/resources/templates/admin/host-detail.html`

- [x] Step 1: Create header with breadcrumb `Admin > Quản lý Host > [Tên Host]`, Host avatar, full name, email, phone, role badge, status badge, and Lock/Unlock toggle button.
- [x] Step 2: Implement Alpine.js sub-tab controller with 5 tabs:
  - Sub-tab 1: Tài chính & Doanh số (Gross revenue, net payout, commission, room nights).
  - Sub-tab 2: Chỗ nghỉ (List of properties owned by this host).
  - Sub-tab 3: Lịch sử đơn phòng (Table of all bookings on host's properties).
  - Sub-tab 4: Đánh giá & Uy tín (Customer reviews and host replies).
  - Sub-tab 5: Cảnh báo bất thường (Host-specific anomalies and flags).
- [x] Step 3: Style all tables and cards with Tailwind CSS following the StayHub Design System (`color-primary-500`, `rounded-2xl`, subtle borders).

---

### Task 8: End-to-End Verification and Integration Tests

**Files:**
- Modify: `src/test/java/com/stayhub/admin/AdminWebIntegrationTest.java`

- [x] Step 1: Add integration tests verifying `/admin/dashboard` renders successfully with status 200 for ADMIN users and 403 for GUEST/anonymous users.
- [x] Step 2: Add integration tests verifying `/admin/hosts/{id}` renders host profile with status 200.
- [x] Step 3: Add integration tests verifying REST endpoints `/api/v1/admin/hosts` and `/api/v1/admin/hosts/{id}` return valid JSON responses.
- [x] Step 4: Run `./mvnw verify` (or targeted test suite) and verify all tests pass.
- [x] Step 5: Test Tailwind CSS build with `npm run build:css`.
