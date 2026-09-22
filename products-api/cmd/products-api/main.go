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
	runtime := app.Runtime{Env: os.LookupEnv, Out: os.Stdout, Listen: app.DefaultListen}
	code := app.Main(ctx, os.Args[1:], runtime)
	stop()
	os.Exit(code)
}
