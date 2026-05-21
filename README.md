# Course Catalog API

A Spring Boot REST API for managing **courses**, **customers**, and **employees** (content creators) across **three separate PostgreSQL databases**. The project demonstrates multi-datasource JPA, cross-database relationship management, orchestrated **saga** workflows with compensating transactions, validation, structured logging with correlation IDs, and OpenAPI documentation.

| | |
|---|---|
| **Java** | 21 |
| **Spring Boot** | 4.0.6 |
| **Build** | Maven |
| **Databases** | PostgreSQL (3 instances) |

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Configuration](#configuration)
- [How to Run](#how-to-run)
- [API Endpoints](#api-endpoints)
- [Calling the API](#calling-the-api)
  - [Step 1 — Login](#step-1--login)
  - [Step 2 — Using the session in Postman](#step-2--using-the-session-in-postman)
  - [Auth endpoints](#auth-endpoints-1)
  - [Employee endpoints](#employee-endpoints-1)
  - [Customer endpoints](#customer-endpoints-1)
  - [Course endpoints](#course-endpoints-1)
- [Dummy Data Seeding](#dummy-data-seeding)
- [Swagger / OpenAPI](#swagger--openapi)
- [Error Responses](#error-responses)

---

## Features

| Feature | Description |
|---------|-------------|
| **Multi-database** | Course, customer, and employee data each use a dedicated PostgreSQL database and JPA persistence unit (`CourseDBConfig`, `CustomerDBConfig`, `EmployeeDBConfig`). |
| **Domain-driven packages** | Code is organized by bounded context: `course`, `customer`, `employee`, plus shared `common` utilities. |
| **CRUD APIs** | Full create, read, update, and delete for all three domains under versioned paths (`/api/v1/...`). |
| **DTO layer** | Request/response DTOs with **ModelMapper** for entity mapping. |
| **Jakarta Validation** | `@Valid` on request bodies; field-level constraints on DTOs (`@NotBlank`, `@Email`, `@Positive`, etc.). |
| **Global exception handling** | `GlobalExceptionHandler` returns consistent JSON error payloads (404, 400, 409, 500). |
| **Saga pattern** | `CatalogSagaService` + `SagaOrchestrator` coordinate cross-DB operations (enrollment, course creation, deletes) with **compensating steps** on failure. |
| **Cross-DB queries** | Resolve linked entities by ID lists (e.g. customers for a course, courses for an employee). |
| **Pagination & sorting** | `getAll*` endpoints support `page`, `sortBy`, and `sortDir`; page size is configurable per domain via `PageSettings`. |
| **Structured logging** | SLF4J + Logback; controllers and services log key actions at INFO/DEBUG. |
| **Correlation ID** | `CorrelationIdFilter` reads or generates `X-Correlation-Id`, stores it in MDC, echoes it in the response, and includes it in log output. |
| **OpenAPI / Swagger** | springdoc-openAPI with grouped APIs for Courses, Customers, and Employees. |
| **Dummy data seeder** | `CatalogDataSeeder` loads sample employees, customers, and courses on startup (configurable). |
| **Spring Security** | Session-based auth with BCrypt (12), custom login/authorization filters, JDBC-backed persistent sessions, remember-me. |
| **Element collections** | Course–creator and course–customer links stored as `@ElementCollection` ID lists (no cross-DB JPA associations). |

---

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  course_course  │     │ course_customer │     │ course_employee │
│      _db        │     │      _db        │     │      _db        │
│  (courses)      │     │  (customers)    │     │  (employees)    │
└────────▲────────┘     └────────▲────────┘     └────────▲────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │   Course Catalog App   │
                    │  Controllers → Services │
                    │  CatalogSagaService    │
                    └────────────────────────┘
```

**Relationships (logical, not FK across DBs):**

- `Course.contentCreatorIds` → `Employee.id`
- `Course.enrolledCustomerIds` ↔ `Customer.enrolledCourseIds` (kept in sync by saga/seeder)

---

## Project Structure

```
course-catalog/
├── pom.xml
├── mvnw / mvnw.cmd
├── README.md
└── src/
    └── main/
        ├── java/com/koushik/course_catalog/
        │   ├── CourseCatalogApplication.java
        │   ├── course/
        │   │   ├── controller/v1/CourseController.java
        │   │   ├── dto/CourseRequestDTO.java, CourseResponseDTO.java, CreatorAssignmentDTO.java
        │   │   ├── entity/Course.java
        │   │   ├── repository/CourseRepository.java
        │   │   └── service/CourseService.java
        │   ├── customer/
        │   │   ├── controller/v1/CustomerController.java
        │   │   ├── dto/CustomerRequestDTO.java, CustomerResponseDTO.java
        │   │   ├── entity/Customer.java
        │   │   ├── repository/CustomerRepository.java
        │   │   └── service/CustomerService.java
        │   ├── employee/
        │   │   ├── controller/v1/EmployeeController.java
        │   │   ├── dto/EmployeeRequestDTO.java, EmployeeResponseDTO.java
        │   │   ├── entity/Employee.java
        │   │   ├── repository/EmployeeRepository.java
        │   │   └── service/EmployeeService.java
        │   └── common/
        │       ├── config/
        │       │   ├── CatalogDataSeeder.java
        │       │   ├── DataSeedProperties.java
        │       │   ├── ModelMapperConfig.java
        │       │   └── OpenApiConfig.java
        │       ├── dbconfig/
        │       │   ├── CourseDBConfig.java
        │       │   ├── CustomerDBConfig.java
        │       │   └── EmployeeDBConfig.java
        │       ├── entity/PageSettings.java
        │       ├── exception/
        │       │   ├── BadRequestException.java
        │       │   ├── GlobalExceptionHandler.java
        │       │   └── ResourceNotFoundException.java
        │       ├── logging/CorrelationIdFilter.java
        │       └── saga/
        │           ├── CatalogSagaService.java
        │           ├── LinkIdHelper.java
        │           ├── SagaContext.java
        │           ├── SagaException.java
        │           ├── SagaOrchestrator.java
        │           └── SagaStep.java
        ├── security/
        │   ├── authorization/RoleAccessRules.java
        │   ├── config/SecurityConfig.java, SessionConfig.java
        │   ├── controller/AuthController.java
        │   ├── filter/JsonAuthenticationFilter.java, RoleAuthorizationFilter.java
        │   └── service/CatalogUserDetailsService.java
        └── resources/
            ├── application.yml
            ├── application-users.yml
            ├── application.properties
            └── logback-spring.xml
```

---

## Dependencies

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-data-jpa` | JPA / Hibernate persistence |
| `spring-boot-starter-webmvc` | REST controllers |
| `spring-boot-starter-validation` | Jakarta Bean Validation |
| `postgresql` (runtime) | PostgreSQL JDBC driver |
| `lombok` | Boilerplate reduction (`@Data`, constructors) |
| `modelmapper` (3.2.0) | DTO ↔ entity mapping |
| `springdoc-openapi-starter-webmvc-ui` (2.5.0) | Swagger UI & OpenAPI docs |
| `spring-boot-starter-security` | Authentication & authorization |
| `spring-session-jdbc` | Persistent HTTP sessions in PostgreSQL |
| `spring-boot-devtools` (optional, runtime) | Dev-time reload |

---

## Prerequisites

- **JDK 21**
- **Maven 3.9+** (or use included `./mvnw`)
- **PostgreSQL** with three databases and users configured as follows:

### 1. Create roles and databases

Run as a PostgreSQL superuser (`postgres`):

```sql
CREATE ROLE course WITH LOGIN PASSWORD 'course';
CREATE DATABASE course_course_db OWNER course;

CREATE ROLE cust WITH LOGIN PASSWORD 'cust';
CREATE DATABASE course_customer_db OWNER cust;

CREATE ROLE emp WITH LOGIN PASSWORD 'emp';
CREATE DATABASE course_employee_db OWNER emp;
```

### 2. Enable login (if roles already exist)

If the roles were created without login access, enable it:

```sql
ALTER ROLE course WITH LOGIN;
ALTER ROLE cust WITH LOGIN;
ALTER ROLE emp WITH LOGIN;
```

### 3. Grant schema permissions

PostgreSQL 15+ revokes `CREATE` on the `public` schema by default. Connect to **each database** and grant the permission to its owner:

```sql
-- Connect to course_course_db, then run:
GRANT CREATE ON SCHEMA public TO course;

-- Connect to course_customer_db, then run:
GRANT CREATE ON SCHEMA public TO cust;

-- Connect to course_employee_db, then run:
GRANT CREATE ON SCHEMA public TO emp;
```

Or using `psql` flags:

```bash
psql -U postgres -d course_course_db   -c "GRANT CREATE ON SCHEMA public TO course;"
psql -U postgres -d course_customer_db -c "GRANT CREATE ON SCHEMA public TO cust;"
psql -U postgres -d course_employee_db -c "GRANT CREATE ON SCHEMA public TO emp;"
```

Hibernate `ddl-auto: update` handles table creation on startup once these permissions are in place.

---

## Database Setup

Create three databases and users (adjust names/passwords to match `application.yml`):

| Database | URL | Username | Password |
|----------|-----|----------|----------|
| `course_course_db` | `jdbc:postgresql://localhost:5432/course_course_db` | `course` | `course` |
| `course_customer_db` | `jdbc:postgresql://localhost:5432/course_customer_db` | `cust` | `cust` |
| `course_employee_db` | `jdbc:postgresql://localhost:5432/course_employee_db` | `emp` | `emp` |

See the [Prerequisites](#prerequisites) section for the full step-by-step setup (create roles, enable login, grant schema permissions).

---

## Configuration

Main settings in `src/main/resources/application.yml`:

```yaml
app:
  data:
    seed:
      enabled: true        # Load dummy data on startup
      only-if-empty: true  # Skip if any DB already has rows
      employees: 20
      customers: 50
      courses: 30

spring:
  datasource:
    course:   # course DB connection
    customer: # customer DB connection
    employee: # employee DB connection
```

---

## How to Run

### 1. Build

```bash
mvn clean compile
```

### 2. Run the application

```bash
mvn spring-boot:run
```

Or with the wrapper:

```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

### 3. Log in (required for API calls)

See **[USERS-CREDENTIALS.md](USERS-CREDENTIALS.md)** for usernames and passwords.

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"Admin@123\"}"
```

Use `-b cookies.txt` on subsequent requests.

### 4. Verify

- API base: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 4. Optional: send a correlation ID

```bash
curl -H "X-Correlation-Id: my-trace-123" http://localhost:8080/api/v1/courses/getAllCourses
```

The same ID appears in response headers and logs.

---

## API Endpoints

Base URL: `http://localhost:8080`  
**Authentication:** Session cookie (`CATALOG_SESSION`) required except for login and Swagger.

### Auth — `/api/v1/auth`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/login` | JSON body `{ "username", "password" }` — creates persistent session |
| `POST` | `/logout` | Ends session |
| `GET` | `/me` | Current user details |

### Courses — `/api/v1/courses`

| Method | Endpoint | Description | Saga |
|--------|----------|-------------|------|
| `POST` | `/addCourse` | Create course (validates creators/customers, links enrollments) | Yes |
| `PUT` | `/updateCourse/{id}` | Update course; enrollment/creator changes use saga | Partial |
| `DELETE` | `/deleteCourse/{id}` | Unlink customers, then delete course | Yes |
| `GET` | `/getCourse/{id}` | Get one course | No |
| `GET` | `/getAllCourses?page=0&sortBy=id&sortDir=asc` | Paginated list with sorting | No |
| `POST` | `/setRecordsinPage?size=10` | Set page size for course list | No |
| `GET` | `/getCustomersByCourse/{courseId}` | Enrolled customers for a course | No |
| `GET` | `/getCreatorsByCourse/{courseId}` | Content creators (employees) for a course | No |
| `POST` | `/enrollCustomer/{courseId}/{customerId}` | Enroll customer (both DBs) | Yes |
| `DELETE` | `/unenrollCustomer/{courseId}/{customerId}` | Unenroll customer | Yes |
| `PUT` | `/assignCreators/{courseId}` | Assign creator employee IDs (body below) | Yes |

**Assign creators body:**

```json
{
  "contentCreatorIds": [1, 2, 3]
}
```

---

### Customers — `/api/v1/customers`

| Method | Endpoint | Description | Saga |
|--------|----------|-------------|------|
| `POST` | `/addCustomer` | Create customer | No |
| `PUT` | `/updateCustomer/{id}` | Update customer | No |
| `DELETE` | `/deleteCustomer/{id}` | Unlink from courses, then delete | Yes |
| `GET` | `/getCustomer/{id}` | Get one customer | No |
| `GET` | `/getAllCustomers?page=0&sortBy=id&sortDir=asc` | Paginated list with sorting | No |
| `POST` | `/setRecordsinPage?size=10` | Set page size for customer list | No |
| `POST` | `/enrollInCourse/{customerId}/{courseId}` | Enroll in course (both DBs) | Yes |

---

### Employees — `/api/v1/employees`

| Method | Endpoint | Description | Saga |
|--------|----------|-------------|------|
| `POST` | `/addEmployee` | Create employee (content creator) | No |
| `PUT` | `/updateEmployee/{id}` | Update employee | No |
| `DELETE` | `/deleteEmployee/{id}` | Unlink from courses as creator, then delete | Yes |
| `GET` | `/getEmployee/{id}` | Get one employee | No |
| `GET` | `/getAllEmployees?page=0&sortBy=id&sortDir=asc` | Paginated list with sorting | No |
| `POST` | `/setRecordsinPage?size=10` | Set page size for employee list | No |
| `GET` | `/getCoursesByEmployee/{employeeId}` | Courses where employee is a creator | No |

---

### Pagination query parameters

Used by all `getAll*` endpoints:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Zero-based page index |
| `sortBy` | `id` | Entity field to sort on |
| `sortDir` | `asc` | `asc` or `desc` |

Page size defaults to **5** per domain until changed via `setRecordsinPage`.

---

## Calling the API

Every endpoint except login requires an active session. The workflow is always: **login first, then call the endpoint**. Postman handles the session cookie automatically once you log in.

---

### Step 1 — Login

**POST** `http://localhost:8080/api/v1/auth/login`

In Postman: set Body → raw → JSON.

```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

A successful login returns `200 OK` and sets a `CATALOG_SESSION` cookie. Postman stores this automatically and sends it on every subsequent request to `localhost`.

Other available accounts:

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@123` | Full access |
| `manager` | `Manager@123` | Full access |
| `employee` | `Employee@123` | GET only (courses & employees) |
| `customer` | `Customer@123` | GET + enroll (courses & customers) |

---

### Step 2 — Using the session in Postman

No extra setup is needed. After logging in, Postman's cookie jar holds `CATALOG_SESSION` for `localhost`. Every request you send to `http://localhost:8080` will include it automatically. You can confirm this by clicking **Cookies** on any request tab.

To log out at any time: **POST** `http://localhost:8080/api/v1/auth/logout` (no body needed).

---

### Auth endpoints

#### GET current user
**GET** `http://localhost:8080/api/v1/auth/me`

No body. Returns the currently authenticated user's details.

```json
{
  "username": "admin",
  "role": "ROLE_ADMIN",
  "displayName": "Administrator"
}
```

#### Logout
**POST** `http://localhost:8080/api/v1/auth/logout`

No body. Clears the session and cookies, returns `200 OK`.

---

### Employee endpoints

#### Get all employees
**GET** `http://localhost:8080/api/v1/employees/getAllEmployees`

Optional query params: `?page=0&sortBy=name&sortDir=asc`

#### Get one employee
**GET** `http://localhost:8080/api/v1/employees/getEmployee/1`

Replace `1` with the employee ID.

#### Get courses by employee
**GET** `http://localhost:8080/api/v1/employees/getCoursesByEmployee/1`

Returns all courses where employee `1` is a content creator.

#### Add employee
**POST** `http://localhost:8080/api/v1/employees/addEmployee`

Body:
```json
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "role": "Senior Instructor",
  "department": "Engineering",
  "salary": 95000.00,
  "experience": 8.5
}
```

#### Update employee
**PUT** `http://localhost:8080/api/v1/employees/updateEmployee/1`

Body (same structure as add, all fields required):
```json
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "role": "Curriculum Lead",
  "department": "Education",
  "salary": 105000.00,
  "experience": 9.0
}
```

#### Delete employee
**DELETE** `http://localhost:8080/api/v1/employees/deleteEmployee/1`

No body. Unlinks the employee from all courses as a creator, then deletes.

#### Set page size
**POST** `http://localhost:8080/api/v1/employees/setRecordsinPage?size=10`

No body. Sets how many employees are returned per page on `getAllEmployees`.

---

### Customer endpoints

#### Get all customers
**GET** `http://localhost:8080/api/v1/customers/getAllCustomers`

Optional query params: `?page=0&sortBy=name&sortDir=asc`

#### Get one customer
**GET** `http://localhost:8080/api/v1/customers/getCustomer/1`

#### Add customer
**POST** `http://localhost:8080/api/v1/customers/addCustomer`

Body:
```json
{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "phone": "+1-555-0101",
  "enrolledCourseIds": []
}
```

#### Update customer
**PUT** `http://localhost:8080/api/v1/customers/updateCustomer/1`

Body:
```json
{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "phone": "+1-555-0199",
  "enrolledCourseIds": []
}
```

#### Delete customer
**DELETE** `http://localhost:8080/api/v1/customers/deleteCustomer/1`

No body. Unenrolls the customer from all courses, then deletes.

#### Enroll customer in a course (from customer side)
**POST** `http://localhost:8080/api/v1/customers/enrollInCourse/1/2`

No body. Enrolls customer `1` in course `2`. Updates both the customer and course records (saga).

#### Set page size
**POST** `http://localhost:8080/api/v1/customers/setRecordsinPage?size=10`

---

### Course endpoints

#### Get all courses
**GET** `http://localhost:8080/api/v1/courses/getAllCourses`

Optional query params: `?page=0&sortBy=title&sortDir=asc`

#### Get one course
**GET** `http://localhost:8080/api/v1/courses/getCourse/1`

#### Get customers enrolled in a course
**GET** `http://localhost:8080/api/v1/courses/getCustomersByCourse/1`

Returns full customer details for all customers enrolled in course `1`.

#### Get creators of a course
**GET** `http://localhost:8080/api/v1/courses/getCreatorsByCourse/1`

Returns full employee details for all content creators of course `1`.

#### Add course
**POST** `http://localhost:8080/api/v1/courses/addCourse`

Body (`contentCreatorIds` must reference existing employee IDs):
```json
{
  "title": "Spring Boot Mastery",
  "description": "A comprehensive guide to Spring Boot.",
  "contentCreatorIds": [1, 2],
  "enrolledCustomerIds": []
}
```

#### Update course
**PUT** `http://localhost:8080/api/v1/courses/updateCourse/1`

Body:
```json
{
  "title": "Spring Boot Mastery — Updated",
  "description": "Updated course description.",
  "contentCreatorIds": [1],
  "enrolledCustomerIds": []
}
```

#### Delete course
**DELETE** `http://localhost:8080/api/v1/courses/deleteCourse/1`

No body. Unenrolls all customers, then deletes the course.

#### Enroll a customer in a course (from course side)
**POST** `http://localhost:8080/api/v1/courses/enrollCustomer/1/2`

No body. Enrolls customer `2` in course `1`. Equivalent to calling `enrollInCourse` from the customer side — both update the same records.

#### Unenroll a customer from a course
**DELETE** `http://localhost:8080/api/v1/courses/unenrollCustomer/1/2`

No body. Removes customer `2` from course `1` and updates both records.

#### Assign content creators to a course
**PUT** `http://localhost:8080/api/v1/courses/assignCreators/1`

Body:
```json
{
  "contentCreatorIds": [1, 3, 5]
}
```

Replaces the current creator list with the provided IDs. All IDs must be valid existing employees.

#### Set page size
**POST** `http://localhost:8080/api/v1/courses/setRecordsinPage?size=10`

---

## Dummy Data Seeding

When `app.data.seed.enabled=true` and databases are empty, startup loads:

| Entity | Count | Notes |
|--------|-------|-------|
| Employees | 20 | Emails `employee1@course-catalog.test` … `employee20@...` |
| Customers | 50 | Emails `customer1@course-catalog.test` … `customer50@...` |
| Courses | 30 | Titles like `Java Fundamentals (1)`, `Spring Boot (2)`, … |

Each course gets 1–3 random creators and 3–12 random enrolled customers (bidirectional links).

To **re-seed**, truncate tables in all three databases and restart the app.

---

## Swagger / OpenAPI

Grouped API documentation:

- **Courses** — `/api/v1/courses/**`
- **Customers** — `/api/v1/customers/**`
- **Employees** — `/api/v1/employees/**`

Open `http://localhost:8080/swagger-ui.html` to explore and try endpoints interactively.

---

## Error Responses

| HTTP Status | When |
|-------------|------|
| `400` | Validation failure (`MethodArgumentNotValidException`) or `BadRequestException` |
| `404` | `ResourceNotFoundException` (entity not found) |
| `409` | `SagaException` (distributed workflow failed; compensations attempted) |
| `500` | Unhandled exceptions |

Example error body:

```json
{
  "timestamp": "2026-05-20T10:15:30",
  "status": 404,
  "message": "Course not found with id: 99"
}
```

Validation errors return an `errors` map with field names and messages.

---

## Saga workflows (summary)

| Saga | Triggered by |
|------|----------------|
| **CreateCourse** | `POST addCourse` |
| **DeleteCourse** | `DELETE deleteCourse` |
| **EnrollCustomer** | `POST enrollCustomer`, `POST enrollInCourse` |
| **UnenrollCustomer** | `DELETE unenrollCustomer` |
| **AssignCreators** | `PUT assignCreators`, creator update on `updateCourse` |
| **DeleteCustomer** | `DELETE deleteCustomer` |
| **DeleteEmployee** | `DELETE deleteEmployee` |

On failure, completed steps run **compensating actions** in reverse order (e.g. restore enrollment lists or delete a partially created course).

---

## Security

| Component | Purpose |
|-----------|---------|
| `JsonAuthenticationFilter` | JSON login at `POST /api/v1/auth/login` |
| `RoleAuthorizationFilter` | Role-based access control per API path |
| `CatalogUserDetailsService` | Loads users from `application-users.yml` |
| `SessionConfig` | JDBC session store + remember-me tokens in course DB |
| BCrypt strength **12** | Password hashing |

**Roles:** `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_EMPLOYEE`, `ROLE_CUSTOMER` — see [USERS-CREDENTIALS.md](USERS-CREDENTIALS.md).

---

## Running Tests

```bash
mvn test
```

Test suite (**48 tests**) uses JUnit 5, Mockito, AssertJ, and standalone MockMvc (no live PostgreSQL required).

| Area | Test classes |
|------|----------------|
| Saga | `SagaOrchestratorTest`, `CatalogSagaServiceTest`, `LinkIdHelperTest` |
| Services | `CourseServiceTest`, `CustomerServiceTest`, `EmployeeServiceTest` |
| Controllers | `CourseControllerTest`, `CustomerControllerTest`, `EmployeeControllerTest` |
| Cross-cutting | `GlobalExceptionHandlerTest`, `CorrelationIdFilterTest` |
| Security | `RoleAccessRulesTest`, `CatalogUserDetailsServiceTest` |

Seeding is disabled in tests via `src/test/resources/application.properties`.

---

## License

This project is for learning and demonstration purposes.
