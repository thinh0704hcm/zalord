package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

// Context keys for identity values injected by Kong. Use these (not raw
// strings) when reading from gin.Context to avoid typos.
const (
	CtxUserID = "userId"
	CtxRoles  = "roles"
)

// Identity reads the X-User-Id and X-User-Roles headers that Kong injects
// from the verified JWT and stashes them on the request context. It rejects
// requests missing X-User-Id (defensive — Kong should never let one through
// to a protected route, but if traffic somehow reaches us directly we refuse).
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

// RequireRole enforces that the caller has the given role. Must run AFTER
// Identity. Roles come from the JWT (via the X-User-Roles header Kong injects),
// so a client can't forge them — only auth-service's signed token can grant them.
func RequireRole(role string) gin.HandlerFunc {
	return func(c *gin.Context) {
		raw, _ := c.Get(CtxRoles)
		roles, _ := raw.([]string)
		for _, r := range roles {
			if r == role {
				c.Next()
				return
			}
		}
		c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": role + " role required"})
	}
}
