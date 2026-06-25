FROM golang:1.26.1-alpine AS builder

WORKDIR /app
COPY go.mod ./
COPY go.sum ./
RUN go mod download || true
COPY . .
RUN go mod tidy && CGO_ENABLED=0 go build -o server ./cmd/server

FROM alpine:latest

WORKDIR /app
COPY --from=builder /app/server .

EXPOSE 8082 9082
CMD ["./server"]
