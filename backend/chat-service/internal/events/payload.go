package events

import (
	"time"

	"github.com/google/uuid"
)

// MessageCreatedEvent — matches the JSON produced by message-service's
// MessageCreatedEvent record. Fields are Jackson-camelCase on the wire.
type MessageCreatedEvent struct {
	MessageId      uuid.UUID         `json:"messageId"`
	ConversationId uuid.UUID         `json:"conversationId"`
	SenderId       uuid.UUID         `json:"senderId"`
	RecipientIds   []uuid.UUID       `json:"recipientIds"`
	Content        string            `json:"content"`
	AttachmentIds  []uuid.UUID       `json:"attachmentIds"`
	CreatedAt      time.Time         `json:"createdAt"`
	ReplyTo        *ReplyToSnippet   `json:"replyTo,omitempty"`
}

// ReplyToSnippet — snapshot of a quoted message embedded in message.created.
// Stable even if the quoted message is later recalled.
type ReplyToSnippet struct {
	MessageId uuid.UUID `json:"messageId"`
	SenderId  uuid.UUID `json:"senderId"`
	Preview   string    `json:"preview"`
}

// MessageRecalledEvent — sender retracted a previously sent message.
// chat-service pushes this to all members so live UIs blank the bubble.
type MessageRecalledEvent struct {
	MessageId      uuid.UUID `json:"messageId"`
	ConversationId uuid.UUID `json:"conversationId"`
	SenderId       uuid.UUID `json:"senderId"`
	RecalledAt     time.Time `json:"recalledAt"`
}

// MessageReadEvent — published by message-service when a user marks a
// conversation as read. chat-service fans it out to the other members so
// "Seen" markers update in real time.
type MessageReadEvent struct {
	ConversationId    uuid.UUID `json:"conversationId"`
	ReaderId          uuid.UUID `json:"readerId"`
	LastReadMessageId uuid.UUID `json:"lastReadMessageId"`
	ReadAt            time.Time `json:"readAt"`
}
