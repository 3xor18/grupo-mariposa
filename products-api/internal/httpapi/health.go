package httpapi

import (
	"context"
	"net/http"
	"strings"
)

const (
	statusUp               = "UP"
	statusDown             = "DOWN"
	detailNoRoute          = "No resource matches the requested path."
	detailMethodNotAllowed = "The requested method is not supported by this resource."
	allowSeparator         = ", "
)

type Readiness interface {
	Ready(ctx context.Context) bool
}

type healthResponse struct {
	Status string `json:"status"`
}

func (rs responder) live(w http.ResponseWriter, r *http.Request) {
	rs.json(w, r, http.StatusOK, healthResponse{Status: statusUp})
}

func (rs responder) ready(readiness Readiness) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !readiness.Ready(r.Context()) {
			rs.json(w, r, http.StatusServiceUnavailable, healthResponse{Status: statusDown})
			return
		}
		rs.json(w, r, http.StatusOK, healthResponse{Status: statusUp})
	}
}

func (rs responder) noRoute(w http.ResponseWriter, r *http.Request) {
	rs.problem(w, r, kindNoRoute, detailNoRoute)
}

func (rs responder) methodNotAllowed(allowed ...string) http.HandlerFunc {
	allow := strings.Join(allowed, allowSeparator)
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set(headerAllow, allow)
		rs.problem(w, r, kindMethodNotAllowed, detailMethodNotAllowed)
	}
}
