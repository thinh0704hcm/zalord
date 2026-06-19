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

// Identity reads Kong-injected X-User-Id / X-User-Roles from the upgrade
// HTTP request. Once the WS is upgraded, Kong is out of the loop — we trust
// the header on the initial handshake and bind it to the connection's identity.
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
