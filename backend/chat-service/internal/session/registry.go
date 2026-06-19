package session

import (
	"sync"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

// Client wraps one WebSocket connection with a buffered outbound channel so
// the consumer goroutine can hand off messages without blocking. The Client's
// own writePump drains the channel and writes to the socket — keeping all
// writes on one goroutine (gorilla/websocket requires this).
type Client struct {
	UserID uuid.UUID
	Conn   *websocket.Conn
	Send   chan []byte
}

// Registry tracks active WS connections keyed by userId. Multi-device: one
// user can have N connections (web tab, mobile, desktop), all receive the
// fan-out. In-memory only — single-instance for now; later move to Redis
// per docs/architecture.md (user:sessions:{user_id}).
type Registry struct {
	mu    sync.RWMutex
	conns map[uuid.UUID]map[*Client]struct{}
}

func NewRegistry() *Registry {
	return &Registry{conns: make(map[uuid.UUID]map[*Client]struct{})}
}

func (r *Registry) Add(c *Client) {
	r.mu.Lock()
	defer r.mu.Unlock()
	set, ok := r.conns[c.UserID]
	if !ok {
		set = make(map[*Client]struct{})
		r.conns[c.UserID] = set
	}
	set[c] = struct{}{}
}

func (r *Registry) Remove(c *Client) {
	r.mu.Lock()
	defer r.mu.Unlock()
	set, ok := r.conns[c.UserID]
	if !ok {
		return
	}
	delete(set, c)
	if len(set) == 0 {
		delete(r.conns, c.UserID)
	}
}

// Get returns a snapshot slice of clients for a user. Safe to iterate after
// release of the lock since slice contents (Client pointers) are stable.
func (r *Registry) Get(userId uuid.UUID) []*Client {
	r.mu.RLock()
	defer r.mu.RUnlock()
	set, ok := r.conns[userId]
	if !ok {
		return nil
	}
	out := make([]*Client, 0, len(set))
	for c := range set {
		out = append(out, c)
	}
	return out
}

func (r *Registry) Stats() (users int, conns int) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	users = len(r.conns)
	for _, set := range r.conns {
		conns += len(set)
	}
	return
}
