package handler

import (
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"go.uber.org/zap"
)

// ProfileResponse is the API representation of a profile. Decoupled from the
// sqlc-generated DB struct so the wire contract uses camelCase (frontend-friendly)
// and can evolve independently of the DB schema.
type ProfileResponse struct {
	ID          uuid.UUID `json:"id"`
	UserID      uuid.UUID `json:"userId"`
	DisplayName string    `json:"displayName"`
	PhoneNumber string    `json:"phoneNumber"`
	AvatarURL   *string   `json:"avatarUrl,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
}

func toResponse(p *queries.Profile) ProfileResponse {
	return ProfileResponse{
		ID:          p.ID,
		UserID:      p.UserID,
		DisplayName: p.DisplayName,
		PhoneNumber: p.PhoneNumber,
		AvatarURL:   p.AvatarUrl,
		CreatedAt:   p.CreatedAt,
	}
}

// ProfileListResponse is a page of profiles plus pagination metadata.
type ProfileListResponse struct {
	Items      []ProfileResponse `json:"items"`
	Page       int               `json:"page"`
	Size       int               `json:"size"`
	Total      int64             `json:"total"`
	TotalPages int               `json:"totalPages"`
}

// ErrorResponse is the shape of every non-2xx body.
type ErrorResponse struct {
	Error string `json:"error"`
}

type ProfileHandler struct {
	svc service.ProfileService
}

func NewProfileHandler(svc service.ProfileService) *ProfileHandler {
	return &ProfileHandler{svc: svc}
}

// GetMe godoc
//
//	@Summary      Get my profile
//	@Description  Returns the profile of the authenticated caller. Identity is taken from the Kong-injected X-User-Id header (derived from the verified JWT).
//	@Tags         profile
//	@Security     BearerAuth
//	@Produce      json
//	@Success      200  {object}  ProfileResponse
//	@Failure      401  {object}  ErrorResponse  "missing identity (no token / not via Kong)"
//	@Failure      404  {object}  ErrorResponse  "profile not found"
//	@Failure      500  {object}  ErrorResponse  "internal error"
//	@Router       /api/v1/users/me [get]
func (h *ProfileHandler) GetMe(c *gin.Context) {
	raw, _ := c.Get(middleware.CtxUserID)
	uidStr, _ := raw.(string)
	userID, err := uuid.Parse(uidStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid userId in context"})
		return
	}

	prof, err := h.svc.GetByUserID(c.Request.Context(), userID)
	if errors.Is(err, pgx.ErrNoRows) {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "profile not found"})
		return
	}
	if err != nil {
		logger.Log.Error("GetByUserID failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	c.JSON(http.StatusOK, toResponse(prof))
}

// GetByPhone godoc
//
//	@Summary      Look up a profile by phone number
//	@Description  Returns the profile whose phone_number matches. Open to any authenticated user (user discovery feature).
//	@Tags         profile
//	@Security     BearerAuth
//	@Produce      json
//	@Param        phone  path  string  true  "Phone number (e.g. 0900000999)"
//	@Success      200  {object}  ProfileResponse
//	@Failure      401  {object}  ErrorResponse
//	@Failure      404  {object}  ErrorResponse  "no profile with this phone"
//	@Failure      500  {object}  ErrorResponse
//	@Router       /api/v1/users/by-phone/{phone} [get]
func (h *ProfileHandler) GetByPhone(c *gin.Context) {
	phone := c.Param("phone")
	if phone == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "phone required"})
		return
	}
	prof, err := h.svc.GetByPhone(c.Request.Context(), phone)
	if errors.Is(err, pgx.ErrNoRows) {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "profile not found"})
		return
	}
	if err != nil {
		logger.Log.Error("GetByPhone failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}
	c.JSON(http.StatusOK, toResponse(prof))
}

// List godoc
//
//	@Summary      List all profiles (paginated)
//	@Description  ADMIN-only. Returns profiles ordered by created_at DESC. `page` is 1-indexed; `size` is clamped to [1, 100].
//	@Tags         profile
//	@Security     BearerAuth
//	@Produce      json
//	@Param        page  query  int  false  "Page number (default 1)"
//	@Param        size  query  int  false  "Page size (default 20, max 100)"
//	@Success      200  {object}  ProfileListResponse
//	@Failure      401  {object}  ErrorResponse
//	@Failure      403  {object}  ErrorResponse  "ADMIN role required"
//	@Failure      500  {object}  ErrorResponse
//	@Router       /api/v1/users [get]
func (h *ProfileHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "20"))

	items, total, err := h.svc.List(c.Request.Context(), page, size)
	if err != nil {
		logger.Log.Error("List failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "internal error"})
		return
	}

	out := make([]ProfileResponse, len(items))
	for i := range items {
		out[i] = toResponse(&items[i])
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
	totalPages := int((total + int64(size) - 1) / int64(size))

	c.JSON(http.StatusOK, ProfileListResponse{
		Items:      out,
		Page:       page,
		Size:       size,
		Total:      total,
		TotalPages: totalPages,
	})
}
