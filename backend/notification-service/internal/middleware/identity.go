package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

const (
	CtxUserID = "userId"
	CtxRoles  = "roles"
)

// Identity reads Kong-injected X-User-Id / X-User-Roles, refuses if missing.
func Identity() gin.HandlerFunc {
	return func(c *gin.Context) {
		uid := c.GetHeader("X-User-Id")
		if uid == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "missing identity"})
			return
		}
		var roles []string
		if r := c.GetHeader("X-User-Roles"); r != "" {
			roles = strings.Split(r, ",")
		}
		c.Set(CtxUserID, uid)
		c.Set(CtxRoles, roles)
		c.Next()
	}
}
