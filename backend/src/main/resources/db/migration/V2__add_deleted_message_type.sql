ALTER TABLE messaging.messages
DROP CONSTRAINT check_content_type;
ALTER TABLE messaging.messages
ADD CONSTRAINT check_content_type CHECK (content_type in ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'DELETED'));