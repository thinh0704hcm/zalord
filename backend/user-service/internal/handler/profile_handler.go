package handler

import (
	"errors"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/middleware"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/service"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"go.uber.org/zap"
)

type ProfileHandler struct {
	svc service.ProfileService
}

func NewProfileHandler(svc service.ProfileService) *ProfileHandler {
	return &ProfileHandler{svc: svc}
}

// GetMe returns the profile of the authenticated caller. userId comes from
// the Identity middleware (sourced from Kong's X-User-Id header).
func (h *ProfileHandler) GetMe(c *gin.Context) {
	raw, _ := c.Get(middleware.CtxUserID)
	uidStr, _ := raw.(string)
	userID, err := uuid.Parse(uidStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid userId in context"})
		return
	}

	prof, err := h.svc.GetByUserID(c.Request.Context(), userID)
	if errors.Is(err, pgx.ErrNoRows) {
		c.JSON(http.StatusNotFound, gin.H{"error": "profile not found"})
		return
	}
	if err != nil {
		logger.Log.Error("GetByUserID failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{"error": "internal error"})
		return
	}
	c.JSON(http.StatusOK, prof)
}
