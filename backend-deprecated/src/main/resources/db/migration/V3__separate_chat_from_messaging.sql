CREATE SCHEMA IF NOT EXISTS chat;
ALTER TABLE messaging.chats SET SCHEMA chat;
ALTER TABLE messaging.chat_members SET SCHEMA chat;
