package events

import (
	"time"

	"github.com/google/uuid"
)

// MessageCreatedEvent — matches the JSON produced by message-service's
// MessageCreatedEvent record. Fields are Jackson-camelCase on the wire.
type MessageCreatedEvent struct {
	MessageId      uuid.UUID   `json:"messageId"`
	ConversationId uuid.UUID   `json:"conversationId"`
	SenderId       uuid.UUID   `json:"senderId"`
	RecipientIds   []uuid.UUID `json:"recipientIds"`
	Content        string      `json:"content"`
	AttachmentIds  []uuid.UUID `json:"attachmentIds"`
	CreatedAt      time.Time   `json:"createdAt"`
}
