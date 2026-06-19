package config

import (
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	MqUri      string
	ServerPort string
}

func Load() *Config {
	_ = godotenv.Load()
	return &Config{
		MqUri:      os.Getenv("MQ_URI"),
		ServerPort: os.Getenv("SERVER_PORT"),
	}
}
