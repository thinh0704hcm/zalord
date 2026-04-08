GRANT pg_monitor TO zalord_user;

CREATE SCHEMA IF NOT EXISTS "user";

CREATE TABLE user.users (
    id uuid PRIMARY KEY,
    full_name text NOT NULL,
    email varchar(100) UNIQUE,
    phone_number varchar(12) UNIQUE,
    birth_date DATE,
    gender varchar(20),
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone
);

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.credentials (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    phone_number varchar(12) UNIQUE,
    email varchar(100) UNIQUE,
    password_hash varchar(255) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    last_login timestamp with time zone
)

CREATE SCHEMA IF NOT EXISTS messaging;

CREATE TABLE messaging.chats (
    id uuid PRIMARY KEY,
    chat_name text NOT NULL,
    chat_type text NOT NULL CONSTRAINT check_chat_type CHECK (chat_type in ('DIRECT', 'GROUP', 'COMMUNITY')),
    last_activity_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE messaging.messages (
    id uuid PRIMARY KEY,
    sender_id uuid NOT NULL,    -- logically references user.users.id
    member_id uuid NOT NULL,    -- logically references user.users.id
    content_type text NOT NULL CONSTRAINT check_content_type CHECK (content_type in ('TEXT', 'IMAGE', 'VIDEO', 'FILE')),
    payload jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone
);

--Indexing
CREATE INDEX messages_chat_id_idx ON messaging.messages(chat_id, created_at desc);

CREATE TABLE messaging.chat_members (
    chat_id uuid NOT NULL,    -- logically references messages.chats.id
    member_id uuid NOT NULL,    -- logically references user.users.id
    role text NOT NULL CONSTRAINT check_role CHECK (role in ('MEMBER', 'ADMIN', 'OWNER')),
    deleted_at timestamp with time zone,
    PRIMARY KEY(chat_id,member_id)
);

--Indexing
CREATE INDEX chat_members_member_id_idx ON messaging.chat_members(member_id);