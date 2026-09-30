-- =====================================================================
--  Student Academic and Course Management System
--  File: database/schema.sql
--  Target: MySQL 8.0+ (verified on MySQL 8.4.9)
--
--  Run with:
--      mysql -u root -p < database/schema.sql
--
--  The script is idempotent: DROP ... IF EXISTS is used so it can be
--  re-run safely on a fresh machine.
-- =====================================================================

DROP DATABASE IF EXISTS student_academic_management;
CREATE DATABASE student_academic_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE student_academic_management;


-- ---------------------------------------------------------------------
-- 1. faculty  (created first because courses reference it)
-- ---------------------------------------------------------------------
CREATE TABLE faculty (
    faculty_id        VARCHAR(20)   NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    date_of_birth     DATE          NULL,
    gender            ENUM('MALE','FEMALE','OTHER') NOT NULL DEFAULT 'OTHER',
    email             VARCHAR(120)  NOT NULL,
    phone             VARCHAR(20)   NULL,
    address           VARCHAR(255)  NULL,
    department        VARCHAR(80)   NOT NULL,
    designation       VARCHAR(60)   NOT NULL,
    office_location   VARCHAR(60)   NULL,
    joining_date      DATE          NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_faculty          PRIMARY KEY (faculty_id),
    CONSTRAINT uq_faculty_email    UNIQUE (email),
    CONSTRAINT chk_faculty_name    CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 2. students
-- ---------------------------------------------------------------------
CREATE TABLE students (
    student_id        VARCHAR(20)       NOT NULL,
    name              VARCHAR(100)      NOT NULL,
    date_of_birth     DATE              NOT NULL,
    gender            ENUM('MALE','FEMALE','OTHER') NOT NULL,
    email             VARCHAR(120)      NOT NULL,
    phone             VARCHAR(20)       NULL,
    address           VARCHAR(255)      NULL,
    department        VARCHAR(80)       NOT NULL,
    semester          TINYINT UNSIGNED  NOT NULL,
    admission_date    DATE              NOT NULL,
    guardian_contact  VARCHAR(20)       NULL,
    created_at        TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_students           PRIMARY KEY (student_id),
    CONSTRAINT uq_students_email     UNIQUE (email),
    CONSTRAINT chk_students_name     CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_students_semester CHECK (semester BETWEEN 1 AND 10)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 3. courses   (Faculty 1 --- * Course)
-- ---------------------------------------------------------------------
CREATE TABLE courses (
    course_id     VARCHAR(20)       NOT NULL,
    course_code   VARCHAR(20)       NOT NULL,
    course_name   VARCHAR(120)      NOT NULL,
    credits       DECIMAL(3,1)      NOT NULL,
    faculty_id    VARCHAR(20)       NULL,
    department    VARCHAR(80)       NOT NULL,
    semester      TINYINT UNSIGNED  NOT NULL,
    description   VARCHAR(255)      NULL,
    created_at    TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_courses            PRIMARY KEY (course_id),
    CONSTRAINT uq_courses_code       UNIQUE (course_code),
    CONSTRAINT fk_courses_faculty    FOREIGN KEY (faculty_id)
                                     REFERENCES faculty (faculty_id)
                                     ON DELETE SET NULL
                                     ON UPDATE CASCADE,
    CONSTRAINT chk_courses_name      CHECK (CHAR_LENGTH(TRIM(course_name)) > 0),
    CONSTRAINT chk_courses_credits   CHECK (credits > 0 AND credits <= 10),
    CONSTRAINT chk_courses_semester  CHECK (semester BETWEEN 1 AND 10)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 4. enrollments   (Student 1 --- * Enrollment * --- 1 Course)
--    UNIQUE(student_id, course_id) is the database-level guarantee
--    that a student can never be enrolled in the same course twice.
-- ---------------------------------------------------------------------
CREATE TABLE enrollments (
    enrollment_id    BIGINT AUTO_INCREMENT NOT NULL,
    student_id       VARCHAR(20)       NOT NULL,
    course_id        VARCHAR(20)       NOT NULL,
    semester         TINYINT UNSIGNED  NOT NULL,
    enrollment_date  DATE              NOT NULL,
    status           ENUM('ACTIVE','COMPLETED','WITHDRAWN')
                                    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_enrollments     PRIMARY KEY (enrollment_id),
    CONSTRAINT uq_enrollment_pair UNIQUE (student_id, course_id),
    CONSTRAINT fk_enr_student     FOREIGN KEY (student_id)
                                 REFERENCES students (student_id)
                                 ON DELETE CASCADE
                                 ON UPDATE CASCADE,
    CONSTRAINT fk_enr_course      FOREIGN KEY (course_id)
                                 REFERENCES courses (course_id)
                                 ON DELETE CASCADE
                                 ON UPDATE CASCADE,
    CONSTRAINT chk_enr_semester   CHECK (semester BETWEEN 1 AND 10)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 5. attendance   (Student 1 --- * Attendance * --- 1 Course)
--    "percentage" is a STORED GENERATED column: the arithmetic lives in
--    the database and classes_held = 0 is handled explicitly so a
--    division by zero can never occur. The application recalculates the
--    same value in Java (Attendance.calculatePercentage) for display
--    and unit testing.
-- ---------------------------------------------------------------------
CREATE TABLE attendance (
    attendance_id     BIGINT AUTO_INCREMENT NOT NULL,
    student_id        VARCHAR(20)   NOT NULL,
    course_id         VARCHAR(20)   NOT NULL,
    classes_held      INT UNSIGNED  NOT NULL,
    classes_attended  INT UNSIGNED  NOT NULL,
    percentage        DECIMAL(5,2)  AS (
                          CASE
                              WHEN classes_held = 0 THEN 0.00
                              ELSE ROUND(classes_attended * 100.0
                                          / classes_held, 2)
                          END
                      ) STORED,
    recorded_on       DATE          NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_attendance       PRIMARY KEY (attendance_id),
    CONSTRAINT uq_attendance_pair  UNIQUE (student_id, course_id),
    CONSTRAINT fk_att_student      FOREIGN KEY (student_id)
                                   REFERENCES students (student_id)
                                   ON DELETE CASCADE
                                   ON UPDATE CASCADE,
    CONSTRAINT fk_att_course       FOREIGN KEY (course_id)
                                   REFERENCES courses (course_id)
                                   ON DELETE CASCADE
                                   ON UPDATE CASCADE,
    CONSTRAINT chk_att_held        CHECK (classes_held >= 0),
    CONSTRAINT chk_att_not_exceed  CHECK (classes_attended <= classes_held)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 6. results   (Student 1 --- * Result * --- 1 Course)
--    total_marks is a STORED GENERATED column (pure arithmetic).
--    percentage / grade / grade_point are written by the application
--    because they depend on the grading POLICY, which is deliberately
--    centralised in com.academic.management.util.GradingPolicy rather
--    than duplicated in SQL.
--    Mark split: internal out of 40, external out of 60.
-- ---------------------------------------------------------------------
CREATE TABLE results (
    result_id        BIGINT AUTO_INCREMENT NOT NULL,
    student_id       VARCHAR(20)      NOT NULL,
    course_id        VARCHAR(20)      NOT NULL,
    internal_marks   DECIMAL(5,2)     NOT NULL,
    external_marks   DECIMAL(5,2)     NOT NULL,
    total_marks      DECIMAL(5,2)     AS (internal_marks + external_marks) STORED,
    percentage       DECIMAL(5,2)     NOT NULL,
    grade            VARCHAR(3)       NOT NULL,
    grade_point      DECIMAL(3,2)     NOT NULL,
    is_pass          BOOLEAN          NOT NULL DEFAULT FALSE,
    result_date      DATE             NOT NULL,
    remarks          VARCHAR(120)     NULL,
    created_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_results         PRIMARY KEY (result_id),
    CONSTRAINT uq_result_pair     UNIQUE (student_id, course_id),
    CONSTRAINT fk_res_student     FOREIGN KEY (student_id)
                                 REFERENCES students (student_id)
                                 ON DELETE CASCADE
                                 ON UPDATE CASCADE,
    CONSTRAINT fk_res_course      FOREIGN KEY (course_id)
                                 REFERENCES courses (course_id)
                                 ON DELETE CASCADE
                                 ON UPDATE CASCADE,
    CONSTRAINT chk_res_internal   CHECK (internal_marks >= 0 AND internal_marks <= 40),
    CONSTRAINT chk_res_external   CHECK (external_marks >= 0 AND external_marks <= 60),
    CONSTRAINT chk_res_percentage CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT chk_res_grade      CHECK (grade <> '')
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 7. users   (application sign-in; no plaintext passwords are stored)
--    password_hash = SHA-256( salt || password ), hex encoded.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id         VARCHAR(20)   NOT NULL,
    username        VARCHAR(60)   NOT NULL,
    password_hash   VARCHAR(64)   NOT NULL,
    salt            VARCHAR(64)   NOT NULL,
    full_name       VARCHAR(100)  NOT NULL,
    role            ENUM('ADMIN','FACULTY') NOT NULL DEFAULT 'ADMIN',
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_users          PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE = InnoDB;


-- ---------------------------------------------------------------------
-- 8. v_student_performance
--    Read-only view used by the academic-profile module. Joining once in
--    the database avoids N+1 queries when building a student's history.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_student_performance AS
SELECT
    e.student_id,
    s.name            AS student_name,
    s.department      AS student_department,
    s.semester        AS student_semester,
    c.course_id,
    c.course_code,
    c.course_name,
    c.credits,
    c.semester        AS course_semester,
    f.faculty_id,
    f.name            AS faculty_name,
    e.enrollment_date,
    e.status          AS enrollment_status,
    a.classes_held,
    a.classes_attended,
    COALESCE(a.percentage, 0.00)            AS attendance_percentage,
    r.internal_marks,
    r.external_marks,
    r.total_marks,
    r.percentage       AS result_percentage,
    r.grade,
    r.grade_point,
    r.is_pass
FROM enrollments e
JOIN students s ON s.student_id = e.student_id
JOIN courses  c ON c.course_id  = e.course_id
LEFT JOIN faculty    f ON f.faculty_id = c.faculty_id
LEFT JOIN attendance a ON a.student_id = e.student_id
                        AND a.course_id  = e.course_id
LEFT JOIN results    r ON r.student_id = e.student_id
                        AND r.course_id  = e.course_id;
