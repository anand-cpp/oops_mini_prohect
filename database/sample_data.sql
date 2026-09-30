-- =====================================================================
--  Student Academic and Course Management System
--  File: database/sample_data.sql
--
--  Run AFTER database/schema.sql:
--      mysql -u root -p < database/sample_data.sql
--
--  This file is OPTIONAL. The application works with an empty
--  database; this only exists so the UI has something to show during a
--  demo/evaluation.
--
--  Demo sign-in credentials (documented in README.md):
--      username: admin     password: admin@123
--      username: faculty   password: faculty@123
--  Passwords are stored only as SHA-256(salt || password).
-- =====================================================================

USE student_academic_management;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE results;
TRUNCATE TABLE attendance;
TRUNCATE TABLE enrollments;
TRUNCATE TABLE courses;
TRUNCATE TABLE students;
TRUNCATE TABLE faculty;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
INSERT INTO users (user_id, username, password_hash, salt, full_name, role, active) VALUES
('USR001', 'admin',    'fc27102522979f3299df409c758eda0d82bf988f82cf01681a718bf3691469a2',
 'a1b2c3d4e5f60718293a4b5c6d7e8f90', 'System Administrator', 'ADMIN', TRUE),
('USR002', 'faculty',  '13687ade427e0e9e439c602b3efa89679428c70f927ac3b2b28d7317d9df1d89',
 'f0e9d8c7b6a5948372615f4e3d2c1b0a', 'Dr. Anita Rao',         'FACULTY', TRUE);


-- ---------------------------------------------------------------------
-- faculty
-- ---------------------------------------------------------------------
INSERT INTO faculty (faculty_id, name, date_of_birth, gender, email, phone,
                     address, department, designation, office_location, joining_date) VALUES
('FAC001', 'Dr. Anita Rao',      '1980-04-12', 'FEMALE', 'anita.rao@university.edu',  '9845012301', '12 Marine Drive, Kochi',   'Computer Science', 'Professor',              'Block A-301', '2012-07-01'),
('FAC002', 'Dr. Rahul Menon',    '1985-11-03', 'MALE',   'rahul.menon@university.edu','9845012302', '45 Hill Street, Kochi',   'Computer Science', 'Associate Professor',    'Block A-305', '2016-08-15'),
('FAC003', 'Prof. Meera Nair',    '1978-06-25', 'FEMALE', 'meera.nair@university.edu','9845012303', '78 Lake View, Thrissur',   'Mathematics',      'Professor',              'Block C-102', '2010-06-01'),
('FAC004', 'Dr. Suresh Kumar',   '1982-01-19', 'MALE',   'suresh.kumar@university.edu','9845012304', '9 Station Road, Kochi',  'Electronics',      'Assistant Professor',    'Block B-210', '2019-01-07'),
('FAC005', 'Dr. Kavya Iyer',     '1990-09-08', 'FEMALE', 'kavya.iyer@university.edu', '9845012305', '22 Garden Lane, Kochi',   'Computer Science', 'Assistant Professor',    'Block A-307', '2021-09-01');


-- ---------------------------------------------------------------------
-- students
-- ---------------------------------------------------------------------
INSERT INTO students (student_id, name, date_of_birth, gender, email, phone,
                      address, department, semester, admission_date, guardian_contact) VALUES
('STU001', 'Arjun Krishnan', '2005-03-14', 'MALE',   'arjun.k@university.edu',   '9876500001', '14 Palm Street, Kochi',    'Computer Science', 3, '2023-07-24', '9876500101'),
('STU002', 'Diya Menon',     '2004-11-27', 'FEMALE', 'diya.menon@university.edu','9876500002', '62 Rose Villa, Kochi',     'Computer Science', 3, '2023-07-24', '9876500102'),
('STU003', 'Vikram Shah',    '2003-07-09', 'MALE',   'vikram.shah@university.edu','9876500003','5 Church Lane, Thrissur', 'Mathematics',      2, '2024-07-22', '9876500103'),
('STU004', 'Anjali Verma',    '2005-01-30', 'FEMALE', 'anjali.v@university.edu',  '9876500004', '31 Nehru Nagar, Kochi',    'Electronics',      1, '2025-07-21', '9876500104'),
('STU005', 'Rohan Pillai',    '2004-05-22', 'MALE',   'rohan.pillai@university.edu','9876500005','88 Marine Drive, Kochi',  'Computer Science', 2, '2024-07-22', '9876500105'),
('STU006', 'Sneha Varma',     '2005-09-16', 'FEMALE', 'sneha.varma@university.edu','9876500006','17 Green Meadows, Kochi', 'Electronics',      1, '2025-07-21', '9876500106');


-- ---------------------------------------------------------------------
-- courses
-- ---------------------------------------------------------------------
INSERT INTO courses (course_id, course_code, course_name, credits, faculty_id,
                     department, semester, description) VALUES
('CRS001', 'CS301', 'Data Structures',      4.0, 'FAC001', 'Computer Science', 3, 'Arrays, linked lists, stacks, queues, trees and graphs.'),
('CRS002', 'CS302', 'Database Management Systems', 4.0, 'FAC002', 'Computer Science', 3, 'Relational design, SQL, normalisation, transactions and indexing.'),
('CRS003', 'CS303', 'Operating Systems',    3.0, 'FAC002', 'Computer Science', 3, 'Processes, threads, scheduling, memory management and file systems.'),
('CRS004', 'MA201', 'Discrete Mathematics',3.5, 'FAC003', 'Mathematics',      2, 'Logic, sets, relations, graph theory, combinatorics and recurrences.'),
('CRS005', 'EC101', 'Basic Electronics',   4.0, 'FAC004', 'Electronics',      1, 'Ohms law, semiconductor devices, amplifiers and digital logic.'),
('CRS006', 'CS101', 'Programming Fundamentals', 3.0, 'FAC005', 'Computer Science', 1, 'Core Java and Python syntax, control flow, functions and OOP basics.');


-- ---------------------------------------------------------------------
-- enrollments
-- ---------------------------------------------------------------------
INSERT INTO enrollments (student_id, course_id, semester, enrollment_date, status) VALUES
('STU001', 'CRS001', 3, '2025-08-01', 'ACTIVE'),
('STU001', 'CRS002', 3, '2025-08-01', 'ACTIVE'),
('STU001', 'CRS003', 3, '2025-08-01', 'ACTIVE'),
('STU002', 'CRS001', 3, '2025-08-01', 'ACTIVE'),
('STU002', 'CRS002', 3, '2025-08-01', 'ACTIVE'),
('STU002', 'CRS003', 3, '2025-08-01', 'ACTIVE'),
('STU003', 'CRS001', 3, '2025-08-01', 'ACTIVE'),
('STU003', 'CRS004', 2, '2024-08-01', 'COMPLETED'),
('STU004', 'CRS005', 1, '2025-08-01', 'ACTIVE'),
('STU004', 'CRS006', 1, '2025-08-01', 'ACTIVE'),
('STU005', 'CRS001', 3, '2025-08-01', 'ACTIVE'),
('STU005', 'CRS004', 2, '2024-08-01', 'COMPLETED'),
('STU006', 'CRS005', 1, '2025-08-01', 'ACTIVE'),
('STU006', 'CRS006', 1, '2025-08-01', 'ACTIVE');


-- ---------------------------------------------------------------------
-- attendance
-- ---------------------------------------------------------------------
INSERT INTO attendance (student_id, course_id, classes_held, classes_attended, recorded_on) VALUES
('STU001', 'CRS001', 40, 36, '2025-11-28'),
('STU001', 'CRS002', 40, 30, '2025-11-28'),
('STU001', 'CRS003', 36, 34, '2025-11-28'),
('STU002', 'CRS001', 40, 22, '2025-11-28'),
('STU002', 'CRS002', 40, 38, '2025-11-28'),
('STU002', 'CRS003', 36, 20, '2025-11-28'),
('STU003', 'CRS001', 40, 35, '2025-11-28'),
('STU003', 'CRS004', 45, 41, '2025-03-10'),
('STU004', 'CRS005', 38, 33, '2025-11-28'),
('STU004', 'CRS006', 42, 40, '2025-11-28'),
('STU005', 'CRS001', 40, 28, '2025-11-28'),
('STU005', 'CRS004', 45, 25, '2025-03-10'),
('STU006', 'CRS005', 38, 36, '2025-11-28'),
('STU006', 'CRS006', 42, 30, '2025-11-28');


-- ---------------------------------------------------------------------
-- results
--   internal out of 40, external out of 60
--   percentage / grade / grade_point are normally written by the
--   application (GradingPolicy). They are supplied here so the sample
--   data is consistent with what the app would have produced.
-- ---------------------------------------------------------------------
INSERT INTO results (student_id, course_id, internal_marks, external_marks,
                     percentage, grade, grade_point, is_pass, result_date) VALUES
('STU001', 'CRS001', 36.00, 54.00, 90.00, 'A+',  5.00, TRUE,  '2025-12-05'),
('STU001', 'CRS002', 32.00, 45.00, 77.00, 'B+',  3.50, TRUE,  '2025-12-05'),
('STU001', 'CRS003', 28.00, 36.00, 64.00, 'B',   3.00, TRUE,  '2025-12-05'),
('STU002', 'CRS001', 24.00, 32.00, 56.00, 'C',   2.00, TRUE,  '2025-12-05'),
('STU002', 'CRS002', 34.00, 48.00, 82.00, 'A',   4.00, TRUE,  '2025-12-05'),
('STU002', 'CRS003', 21.00, 24.00, 45.00, 'D',   1.00, TRUE,  '2025-12-05'),
('STU003', 'CRS001', 33.00, 50.00, 83.00, 'A',   4.00, TRUE,  '2025-12-05'),
('STU003', 'CRS004', 30.00, 40.00, 70.00, 'B+',  3.50, TRUE,  '2025-03-15'),
('STU004', 'CRS005', 31.00, 46.00, 77.00, 'B+',  3.50, TRUE,  '2025-12-05'),
('STU004', 'CRS006', 35.00, 53.00, 88.00, 'A',   4.00, TRUE,  '2025-12-05'),
('STU005', 'CRS001', 26.00, 33.00, 59.00, 'C',   2.00, TRUE,  '2025-12-05'),
('STU005', 'CRS004', 22.00, 26.00, 48.00, 'D',   1.00, TRUE,  '2025-03-15'),
('STU006', 'CRS005', 37.00, 55.00, 92.00, 'A+',  5.00, TRUE,  '2025-12-05'),
('STU006', 'CRS006', 29.00, 38.00, 67.00, 'B',   3.00, TRUE,  '2025-12-05');
