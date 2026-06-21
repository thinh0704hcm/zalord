// Package eventbus is the broker-agnostic consumer abstraction for the
// message.created chain. Two backends: rabbitmq | kafka, selected at
// startup via EVENT_BUS env (must match message-service's choice so they
// share a broker for this event).
package eventbus

import "context"

type HandlerFunc func(ctx context.Context, body []byte) error

type Consumer interface {
	Subscribe(ctx context.Context, eventName, consumerGroup string, handler HandlerFunc) error
	Close() error
}

type PermanentError struct{ Err error }

func (e *PermanentError) Error() string { return e.Err.Error() }
func (e *PermanentError) Unwrap() error { return e.Err }
