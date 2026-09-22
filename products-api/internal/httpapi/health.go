package httpapi

import (
	"fmt"
	"net/http"
)

const (
	statusUp      = "UP"
	statusDown    = "DOWN"
	detailNoRoute = "No resource matches %s %s."
)

type Readiness interface {
	Ready() bool
}

type healthResponse struct {
	Status string `json:"status"`
}

func (rs responder) live(w http.ResponseWriter, r *http.Request) {
	rs.json(w, r, http.StatusOK, healthResponse{Status: statusUp})
}

func (rs responder) ready(readiness Readiness) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !readiness.Ready() {
			rs.json(w, r, http.StatusServiceUnavailable, healthResponse{Status: statusDown})
			return
		}
		rs.json(w, r, http.StatusOK, healthResponse{Status: statusUp})
	}
}

func (rs responder) noRoute(w http.ResponseWriter, r *http.Request) {
	rs.problem(w, r, kindNoRoute, fmt.Sprintf(detailNoRoute, r.Method, r.URL.Path))
}
