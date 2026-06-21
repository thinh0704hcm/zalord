package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

const (
	// How long we wait for a pong before declaring the conn dead.
	pongWait = 60 * time.Second
	// How often we send pings — slightly less than pongWait.
	pingPeriod = (pongWait * 9) / 10
	// Max time to wait when writing one frame.
	writeWait = 10 * time.Second
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	// Dev-only: any origin can upgrade. In prod restrict to known web origins.
	CheckOrigin: func(r *http.Request) bool { return true },
}

type WsHandler struct {
	registry *session.Registry
}

func NewWsHandler(reg *session.Registry) *WsHandler {
	return &WsHandler{registry: reg}
}

// Connect upgrades the HTTP request to a WebSocket. Identity is taken from
// the Kong-injected X-User-Id on the upgrade request (validated upstream by
// the jwt plugin). After upgrade, Kong is out of the loop until close.
func (h *WsHandler) Connect(c *gin.Context) {
	rawUid, _ := c.Get(middleware.CtxUserID)
	uidStr, _ := rawUid.(string)
	userID, err := uuid.Parse(uidStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid userId"})
		return
	}

	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		logger.Log.Warn("ws upgrade failed", zap.Error(err))
		return
	}

	client := &session.Client{
		UserID: userID,
		Conn:   conn,
		Send:   make(chan []byte, 32), // bounded — drop if slow consumer
	}
	h.registry.Add(client)
	logger.Log.Info("ws connected", zap.String("userId", uidStr))

	go h.writePump(client)
	go h.readPump(client) // blocks until close, then cleans up
}

// writePump owns all writes to the socket. Reads from the Send channel; also
// emits periodic pings so idle conns don't get killed by intermediate proxies.
func (h *WsHandler) writePump(c *session.Client) {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		_ = c.Conn.Close()
	}()
	for {
		select {
		case msg, ok := <-c.Send:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Send channel closed — tell peer we're done and exit.
				_ = c.Conn.WriteMessage(websocket.CloseMessage, nil)
				return
			}
			if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				logger.Log.Debug("ws write failed", zap.Error(err))
				return
			}
		case <-ticker.C:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// readPump runs on its own goroutine. We don't expect inbound messages yet
// (chat-service only PUSHes for now), but we must read so the conn closure
// is detected and pong frames flow back. On exit, deregister + close.
func (h *WsHandler) readPump(c *session.Client) {
	defer func() {
		h.registry.Remove(c)
		close(c.Send)
		_ = c.Conn.Close()
		logger.Log.Info("ws disconnected", zap.String("userId", c.UserID.String()))
	}()
	c.Conn.SetReadLimit(4096)
	_ = c.Conn.SetReadDeadline(time.Now().Add(pongWait))
	c.Conn.SetPongHandler(func(string) error {
		return c.Conn.SetReadDeadline(time.Now().Add(pongWait))
	})
	for {
		if _, _, err := c.Conn.ReadMessage(); err != nil {
			return
		}
	}
}
