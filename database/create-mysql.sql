-- Run as a local MySQL administrator before using the mysql profile.
CREATE DATABASE IF NOT EXISTS trustflow CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'trustflow'@'localhost' IDENTIFIED BY 'change-this-local-password';
GRANT ALL PRIVILEGES ON trustflow.* TO 'trustflow'@'localhost';
-- Set DB_PASSWORD to this password in the IntelliJ run configuration.
-- Tables are created by Hibernate on the first run (coursework configuration).
