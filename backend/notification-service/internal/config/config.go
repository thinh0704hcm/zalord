package config

import (
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	DbUri      string
	MqUri      string
	ServerPort string
}

func Load() *Config {
	_ = godotenv.Load()
	return &Config{
		DbUri:      os.Getenv("DB_URI"),
		MqUri:      os.Getenv("MQ_URI"),
		ServerPort: os.Getenv("SERVER_PORT"),
	}
}
