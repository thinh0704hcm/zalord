GRANT pg_monitor TO giano_user;

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
    id uuid PRIMARY KEY,
    full_name text NOT NULL,
    email varchar(100) UNIQUE,
    phone_number varchar(20) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    birth_date DATE,
    gender varchar(20),
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone
);