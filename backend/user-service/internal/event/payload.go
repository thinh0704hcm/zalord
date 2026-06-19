package event

type UserCreatedPayload struct {
	UserID      string `json:"userId"`
	DisplayName string `json:"displayName"`
	PhoneNumber string `json:"phoneNumber"`
}
