package handler

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/notification-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"go.uber.org/zap"
)

// NotificationResponse — wire DTO with camelCase + parsed payload.
type NotificationResponse struct {
	ID        uuid.UUID       `json:"id"`
	Type      string          `json:"type"`
	Title     *string         `json:"title,omitempty"`
	Body      *string         `json:"body,omitempty"`
	Payload   json.RawMessage `json:"payload,omitempty"` // pre-serialized JSON, sent inline
	IsRead    bool            `json:"isRead"`
	CreatedAt time.Time       `json:"createdAt"`
	ReadAt    *time.Time      `json:"readAt,omitempty"`
}

type ListResponse struct {
	Items      []NotificationResponse `json:"items"`
	Page       int                    `json:"page"`
	Size       int                    `json:"size"`
	Total      int64                  `json:"total"`
	TotalPages int                    `json:"totalPages"`
}

type ErrorResponse struct {
	Error string `json:"error"`
}

type UnreadCountResponse struct {
	Unread int64 `json:"unread"`
}

func toResp(n queries.Notification) NotificationResponse {
	return NotificationResponse{
		ID:        n.ID,
		Type:      n.Type,
		Title:     n.Title,
		Body:      n.Body,
		Payload:   json.RawMessage(n.Payload),
		IsRead:    n.IsRead,
		CreatedAt: n.CreatedAt,
		ReadAt:    n.ReadAt,
	}
}

type Handler struct {
	svc service.NotificationService
}

func New(svc service.NotificationService) *Handler {
	return &Handler{svc: svc}
}

// List godoc
//
//	@Summary      List my notifications (paginated, newest first)
//	@Tags         notification
//	@Security     BearerAuth
//	@Produce      json
//	@Param        page  query  int  false  "Page (default 1)"
//	@Param        size  query  int  false  "Size (default 20, max 100)"
//	@Success      200  {object}  ListResponse
//	@Failure      401  {object}  ErrorResponse
//	@Router       /api/v1/notifications [get]
func (h *Handler) List(c *gin.Context) {
	uid := uuid.MustParse(c.GetString(middleware.CtxUserID))
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "20"))

	items, total, err := h.svc.List(c.Request.Context(), uid, page, size)
	if err != nil {
		logger.Log.Error("list failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	if page < 1 {
		page = 1
	}
	if size < 1 {
		size = 20
	}
	if size > 100 {
		size = 100
	}
	out := make([]NotificationResponse, len(items))
	for i := range items {
		out[i] = toResp(items[i])
	}
	totalPages := int((total + int64(size) - 1) / int64(size))
	c.JSON(http.StatusOK, ListResponse{Items: out, Page: page, Size: size, Total: total, TotalPages: totalPages})
}

// UnreadCount godoc
//
//	@Summary      Get my unread count (for the bell badge)
//	@Tags         notification
//	@Security     BearerAuth
//	@Produce      json
//	@Success      200  {object}  UnreadCountResponse
//	@Failure      401  {object}  ErrorResponse
//	@Router       /api/v1/notifications/unread-count [get]
func (h *Handler) UnreadCount(c *gin.Context) {
	uid := uuid.MustParse(c.GetString(middleware.CtxUserID))
	cnt, err := h.svc.UnreadCount(c.Request.Context(), uid)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	c.JSON(http.StatusOK, UnreadCountResponse{Unread: cnt})
}

// MarkRead godoc
//
//	@Summary      Mark a single notification as read
//	@Tags         notification
//	@Security     BearerAuth
//	@Param        id   path  string  true  "Notification id"
//	@Success      200  {object}  map[string]bool  "{\"updated\": true|false}"
//	@Failure      401  {object}  ErrorResponse
//	@Router       /api/v1/notifications/{id}/read [post]
func (h *Handler) MarkRead(c *gin.Context) {
	uid := uuid.MustParse(c.GetString(middleware.CtxUserID))
	id, err := uuid.Parse(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid id"})
		return
	}
	ok, err := h.svc.MarkRead(c.Request.Context(), id, uid)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"updated": ok})
}

// MarkAllRead godoc
//
//	@Summary      Mark all my notifications as read
//	@Tags         notification
//	@Security     BearerAuth
//	@Success      200  {object}  map[string]int64  "{\"updated\": n}"
//	@Failure      401  {object}  ErrorResponse
//	@Router       /api/v1/notifications/read-all [post]
func (h *Handler) MarkAllRead(c *gin.Context) {
	uid := uuid.MustParse(c.GetString(middleware.CtxUserID))
	n, err := h.svc.MarkAllRead(c.Request.Context(), uid)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"updated": n})
}
