package handler

import (
	"errors"
	"net/http"
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
	AvatarURL   *string   `json:"avatarUrl,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
}

func toResponse(p *queries.Profile) ProfileResponse {
	return ProfileResponse{
		ID:          p.ID,
		UserID:      p.UserID,
		DisplayName: p.DisplayName,
		AvatarURL:   p.AvatarUrl,
		CreatedAt:   p.CreatedAt,
	}
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
