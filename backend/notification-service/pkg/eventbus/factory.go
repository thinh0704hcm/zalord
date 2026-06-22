package eventbus

import (
	"fmt"
	"os"
	"strings"

	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"go.uber.org/zap"
)

// New picks backend based on EVENT_BUS env. Defaults to rabbitmq.
// amqpURL used only for rabbitmq; kafkaBootstrap only for kafka.
func New(amqpURL, kafkaBootstrap string) (Consumer, error) {
	backend := strings.ToLower(strings.TrimSpace(os.Getenv("EVENT_BUS")))
	if backend == "" {
		backend = "rabbitmq"
	}
	logger.Log.Info("eventbus backend selected", zap.String("backend", backend))
	switch backend {
	case "rabbitmq":
		return newRabbitConsumer(amqpURL)
	case "kafka":
		return newKafkaConsumer(kafkaBootstrap)
	default:
		return nil, fmt.Errorf("unknown EVENT_BUS=%q (want rabbitmq|kafka)", backend)
	}
}
