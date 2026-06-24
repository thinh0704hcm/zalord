package handler

import (
	"sync"

	"github.com/google/uuid"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
)

// watcherIndex tracks "client X wants notifications when user Y's presence
// changes." Reverse map: watcheeId → set of subscribed clients. Cleared
// implicitly when a client disconnects via Forget(client).
type watcherIndex struct {
	mu    sync.RWMutex
	by    map[uuid.UUID]map[*session.Client]struct{}
}

func newWatcherIndex() *watcherIndex {
	return &watcherIndex{by: make(map[uuid.UUID]map[*session.Client]struct{})}
}

func (w *watcherIndex) add(watchee uuid.UUID, c *session.Client) {
	w.mu.Lock()
	defer w.mu.Unlock()
	set, ok := w.by[watchee]
	if !ok {
		set = make(map[*session.Client]struct{})
		w.by[watchee] = set
	}
	set[c] = struct{}{}
}

func (w *watcherIndex) remove(watchee uuid.UUID, c *session.Client) {
	w.mu.Lock()
	defer w.mu.Unlock()
	if set, ok := w.by[watchee]; ok {
		delete(set, c)
		if len(set) == 0 {
			delete(w.by, watchee)
		}
	}
}

// Watchers returns a snapshot of clients subscribed to a watchee's transitions.
func (w *watcherIndex) Watchers(watchee uuid.UUID) []*session.Client {
	w.mu.RLock()
	defer w.mu.RUnlock()
	set, ok := w.by[watchee]
	if !ok {
		return nil
	}
	out := make([]*session.Client, 0, len(set))
	for c := range set {
		out = append(out, c)
	}
	return out
}

// Forget removes a client from every watch list. Called when the client
// disconnects so we don't leak references.
func (w *watcherIndex) Forget(c *session.Client) {
	w.mu.Lock()
	defer w.mu.Unlock()
	for watchee, set := range w.by {
		delete(set, c)
		if len(set) == 0 {
			delete(w.by, watchee)
		}
	}
}
