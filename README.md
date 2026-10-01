# Student Academic and Course Management System

A desktop application for managing students, faculty, courses, enrolments,
attendance and results. Java 17, Swing for the interface, JDBC for persistence,
MySQL 8 for storage.

## What it does

Nine screens, all reading and writing the same database:

| Screen | What it is for |
| --- | --- |
| Dashboard | Headline counts and averages across every table |
| Students | Add, edit and delete students; search by id, name or email; filter by department |
| Faculty | Add, edit and delete faculty; filter by department and designation |
| Courses | Add, edit and delete courses; assign the teaching faculty member |
| Enrollments | Enrol a student on a course, change status, remove |
| Attendance | Record classes held and attended per student and course, with a per-course summary |
| Results | Publish a result with a live grade preview, edit or delete it, per-course summary |
| Profiles | One student's calculated standing: GPA, credits, attendance and standing reasons |
| Search | Cross-entity search across students, faculty and courses |

Every screen is backed by the service layer, so a change on one screen is
reflected on the others that depend on it - editing a student refreshes the
dashboard and the profile page rather than leaving stale numbers on screen.

### Grading

Marks are 40 internal and 60 external. The percentage is graded on the
published scale: A+ >= 90, A >= 80, B+ >= 70, B >= 60, C >= 50, D >= 40,
F < 40. The pass mark is 40 per cent. The result form shows the resulting grade
as the marks are typed, before anything is saved.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8.0 or newer

## Setup

**1. Create the database and load the sample data.**

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/sample_data.sql
```

> `schema.sql` **drops and recreates** the `student_academic_management`
> database. Do not run it against a database you want to keep.

**2. Configure the connection.**

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

Edit `application.properties` and set `db.password` to your MySQL password.
That file is in `.gitignore`, so your password is never committed. The
password can also be supplied without a file, which is the better habit:

```bash
export ACADEMIC_DB_URL='jdbc:mysql://localhost:3306/student_academic_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8'
export ACADEMIC_DB_USERNAME=root
export ACADEMIC_DB_PASSWORD='your-password'
```

Resolution order is: system property, then environment variable, then
`application.properties`.

**3. Build and run.**

```bash
mvn clean package
java -jar target/student-academic-management.jar
```

## Sign in

The sample data creates two accounts:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin@123` | Administrator |
| `faculty` | `faculty@123` | Faculty |

## Tests

```bash
mvn test
```

Three suites, 120 tests:

- `DomainRulesTest` - the model in isolation: validation, grading scale, GPA.
- `ServiceRulesTest` - business rules against in-memory fakes.
- `DaoIntegrationTest` and `WorkflowIntegrationTest` - against a **live MySQL**,
  so MySQL must be running and the sample data loaded or these cannot mean
  anything. `WorkflowIntegrationTest` walks one student through a full year -
  create, enrol, attend, grade, read the profile, check the dashboard, then
  delete and confirm the cascade - and cleans up after itself.

## Layout

```
src/main/java/com/academic/management/
  Main.java              entry point; checks connectivity and the schema
  model/                 domain objects, all self-validating
  dao/                   persistence contracts
  dao/impl/              JDBC implementations, all parameterised
  service/               business rules; the only layer the UI talks to
  ui/                    Swing screens
  ui/common/             shared dialog, table, theme and error handling
  util/                  config, logging, validation, grading policy
database/
  schema.sql             normalised schema, drops and recreates
  sample_data.sql        sample records and the two accounts above
```

### Two rules the code holds to

**No database call on the event thread.** Every query runs on a worker and
posts its result back to the event thread, so the window keeps repainting
during a round trip. A form reads its fields on the event thread and only the
write happens on the worker.

**No SQL built by concatenation.** Every statement is a `PreparedStatement`
with bound parameters.
