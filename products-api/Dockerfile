FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 GOOS=linux \
    go build -trimpath -ldflags "-s -w" -o /out/products-api ./cmd/products-api

FROM gcr.io/distroless/static:nonroot
COPY --from=build /out/products-api /products-api
USER nonroot:nonroot
ENV PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/products-api", "-healthcheck"]
ENTRYPOINT ["/products-api"]
