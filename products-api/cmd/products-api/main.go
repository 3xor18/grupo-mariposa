package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/grupomariposa/platform/products-api/internal/app"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	code := app.Main(ctx, os.Args[1:], os.LookupEnv, os.Stdout)
	stop()
	os.Exit(code)
}
