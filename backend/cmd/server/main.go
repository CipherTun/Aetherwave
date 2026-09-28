package main

import (
	"flag"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const jamendoBase = "https://api.jamendo.com/"

func main() {
	addr := flag.String("addr", "127.0.0.1:17843", "HTTP listen address")
	flag.Parse()

	client := &http.Client{Timeout: 30 * time.Second}
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"status":"ok","service":"aetherwave-backend"}`)
	})

	mux.HandleFunc("/api/jamendo/", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}

		suffix := strings.TrimPrefix(r.URL.Path, "/api/jamendo/")
		target, err := url.Parse(jamendoBase + suffix)
		if err != nil {
			http.Error(w, "invalid upstream URL", http.StatusBadGateway)
			return
		}
		target.RawQuery = r.URL.RawQuery

		req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, target.String(), nil)
		if err != nil {
			http.Error(w, "failed to create upstream request", http.StatusBadGateway)
			return
		}
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", "Aetherwave/1.0")

		resp, err := client.Do(req)
		if err != nil {
			http.Error(w, "Jamendo upstream unavailable", http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()

		if contentType := resp.Header.Get("Content-Type"); contentType != "" {
			w.Header().Set("Content-Type", contentType)
		} else {
			w.Header().Set("Content-Type", "application/json")
		}
		w.WriteHeader(resp.StatusCode)
		_, _ = io.Copy(w, resp.Body)
	})

	log.Printf("Aetherwave backend listening on %s", *addr)
	log.Fatal(http.ListenAndServe(*addr, mux))
}
