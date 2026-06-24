package handler

import (
	"context"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/presence"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

const (
	pongWait   = 60 * time.Second
	pingPeriod = (pongWait * 9) / 10
	writeWait  = 10 * time.Second
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin:     func(r *http.Request) bool { return true },
}

type WsHandler struct {
	registry *session.Registry
	router   *Router
	presence *presence.Registry
}

func NewWsHandler(reg *session.Registry, router *Router, pres *presence.Registry) *WsHandler {
	return &WsHandler{registry: reg, router: router, presence: pres}
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
		Send:   make(chan []byte, 32),
	}
	h.registry.Add(client)
	if h.presence != nil {
		h.presence.MarkOnline(c.Request.Context(), userID)
	}
	logger.Log.Info("ws connected", zap.String("userId", uidStr))

	go h.writePump(client)
	go h.readPump(client)
}

// writePump owns all writes to the socket. Also emits periodic pings + refreshes
// the presence heartbeat so a crashed instance's users eventually expire from
// the online set.
func (h *WsHandler) writePump(c *session.Client) {
	ticker := time.NewTicker(pingPeriod)
	hbTicker := time.NewTicker(presence.HeartbeatRefresh)
	defer func() {
		ticker.Stop()
		hbTicker.Stop()
		_ = c.Conn.Close()
	}()
	for {
		select {
		case msg, ok := <-c.Send:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
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
		case <-hbTicker.C:
			if h.presence != nil {
				h.presence.Heartbeat(context.Background(), c.UserID)
			}
		}
	}
}

// readPump consumes inbound frames and dispatches via Router. On exit it
// deregisters, drops watch subscriptions, and broadcasts offline if this was
// the user's last connection on this instance.
func (h *WsHandler) readPump(c *session.Client) {
	defer func() {
		h.registry.Remove(c)
		if h.router != nil {
			h.router.Watchers().Forget(c)
		}
		if h.presence != nil && len(h.registry.Get(c.UserID)) == 0 {
			h.presence.MarkOffline(context.Background(), c.UserID)
		}
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
		_, msg, err := c.Conn.ReadMessage()
		if err != nil {
			return
		}
		if h.router != nil {
			h.router.Handle(context.Background(), c, msg)
		}
	}
}
