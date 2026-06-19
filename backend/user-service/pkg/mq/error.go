package mq

// PermanentError marks an error that the consumer should NOT requeue.
// Wrap unmarshal failures, validation failures, or any "won't fix by retrying"
// condition. The consumer detects it via errors.As and acks the message
// instead of nack-requeueing, so it doesn't loop forever (poison message).
type PermanentError struct{ Err error }

func (e *PermanentError) Error() string { return e.Err.Error() }
func (e *PermanentError) Unwrap() error { return e.Err }
