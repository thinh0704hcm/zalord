package mq

import (
	"context"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/trace"
)

// amqpCarrier adapts amqp.Table to propagation.TextMapCarrier for OTel context extraction.
type amqpCarrier struct {
	headers amqp.Table
}

func (c amqpCarrier) Get(key string) string {
	v, ok := c.headers[key]
	if !ok {
		return ""
	}
	s, _ := v.(string)
	return s
}

func (c amqpCarrier) Set(key, value string) {
	c.headers[key] = value
}

func (c amqpCarrier) Keys() []string {
	keys := make([]string, 0, len(c.headers))
	for k := range c.headers {
		keys = append(keys, k)
	}
	return keys
}

// startConsumerSpan extracts W3C trace context from AMQP headers and starts a consumer span.
// Returns a context with the extracted span and a span that the caller must defer End() on.
func startConsumerSpan(ctx context.Context, headers amqp.Table, queueName string) (context.Context, trace.Span) {
	propagator := otel.GetTextMapPropagator()
	extractedCtx := propagator.Extract(ctx, amqpCarrier{headers: headers})

	spanCtx, span := otel.Tracer("rabbitmq-consumer").Start(
		extractedCtx,
		queueName+" process",
		trace.WithSpanKind(trace.SpanKindConsumer),
	)
	return spanCtx, span
}
