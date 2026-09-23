ARG GO_IMAGE=golang:1.26.8-alpine3.24
ARG GO_DIGEST=sha256:8ac98ca534ac3f51e1f420a1dd2c15e74c75cfa0f23f3ad27eb5d7236c349a0c
ARG RUNTIME_IMAGE=gcr.io/distroless/static:nonroot
ARG RUNTIME_DIGEST=sha256:e2e927ec666bae08560abb3c55d0659eceabb657f56b6782ab500a9fc7f555e3

FROM ${GO_IMAGE}@${GO_DIGEST} AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 GOOS=linux \
    go build -trimpath -ldflags "-s -w" -o /out/products-api ./cmd/products-api

FROM ${RUNTIME_IMAGE}@${RUNTIME_DIGEST}
COPY --from=build /out/products-api /products-api
USER nonroot:nonroot
ENV PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/products-api", "-healthcheck"]
ENTRYPOINT ["/products-api"]
