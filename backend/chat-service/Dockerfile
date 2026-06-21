FROM golang:1.26.1-alpine AS builder

WORKDIR /app
COPY go.mod ./
RUN go mod download || true
COPY . .
RUN go mod tidy && CGO_ENABLED=0 go build -o server ./cmd/server

FROM alpine:latest
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=builder /app/server .

EXPOSE 8084
CMD ["./server"]
