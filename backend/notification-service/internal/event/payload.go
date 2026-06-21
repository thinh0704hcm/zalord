package event

import (
	"time"

	"github.com/google/uuid"
)

// Mirror of message-service's MessageCreatedEvent.
type MessageCreatedEvent struct {
	MessageId      uuid.UUID   `json:"messageId"`
	ConversationId uuid.UUID   `json:"conversationId"`
	SenderId       uuid.UUID   `json:"senderId"`
	RecipientIds   []uuid.UUID `json:"recipientIds"`
	Content        string      `json:"content"`
	AttachmentIds  []uuid.UUID `json:"attachmentIds"`
	CreatedAt      time.Time   `json:"createdAt"`
}

// Mirror of group-service's GroupCreatedEvent.
type GroupCreatedEvent struct {
	GroupId   uuid.UUID   `json:"groupId"`
	Name      string      `json:"name"`
	CreatedBy uuid.UUID   `json:"createdBy"`
	MemberIds []uuid.UUID `json:"memberIds"`
	CreatedAt time.Time   `json:"createdAt"`
}

type GroupMemberAddedEvent struct {
	GroupId  uuid.UUID `json:"groupId"`
	UserId   uuid.UUID `json:"userId"`
	JoinedAt time.Time `json:"joinedAt"`
}
