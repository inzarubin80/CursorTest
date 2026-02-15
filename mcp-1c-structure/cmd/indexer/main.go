package main

import (
	"context"
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"os"

	"github.com/ser/mcp-1c-structure/internal/config"
	"github.com/ser/mcp-1c-structure/internal/snapshot"
	"github.com/ser/mcp-1c-structure/internal/store"
	"github.com/ser/mcp-1c-structure/internal/store/postgres"
)

func main() {
	snapshotDir := flag.String("snapshot", "", "Path to snapshot directory (meta.json, objects.json, relations.json). Default: MCP_1C_STRUCTURE_SNAPSHOT_DIR or ./snapshot")
	httpAddr := flag.String("http", "", "If set, run HTTP server on this address (e.g. :8080) and accept POST with snapshot JSON instead of loading from disk")
	flag.Parse()

	dbURL := config.DatabaseURL()
	if dbURL == "" {
		log.Fatal("Set MCP_1C_STRUCTURE_DATABASE_URL or POSTGRES_DSN to run indexer")
	}

	if *httpAddr != "" {
		runHTTPServer(dbURL, *httpAddr)
		return
	}

	// CLI: load from directory
	if *snapshotDir == "" {
		*snapshotDir = config.SnapshotDir()
	}
	if *snapshotDir == "" {
		*snapshotDir = "snapshot"
	}
	meta, objects, relations, err := snapshot.LoadSnapshot(*snapshotDir)
	if err != nil {
		log.Fatalf("Load snapshot: %v", err)
	}
	log.Printf("Loaded %d objects, %d relations from %s", len(objects), len(relations), *snapshotDir)
	s, err := postgres.New(dbURL)
	if err != nil {
		log.Fatalf("Connect: %v", err)
	}
	defer s.Close()
	if err := s.Import(context.Background(), meta, objects, relations); err != nil {
		log.Fatalf("Import: %v", err)
	}
	log.Printf("Import done: %s %s", meta.ConfigName, meta.ConfigVersion)
	os.Exit(0)
}

// SnapshotPayload — тело POST запроса с полным снимком структуры
type SnapshotPayload struct {
	Meta     snapshot.Meta      `json:"meta"`
	Objects  []snapshot.Object  `json:"objects"`
	Relations []snapshot.Relation `json:"relations"`
}

func runHTTPServer(dbURL, addr string) {
	s, err := postgres.New(dbURL)
	if err != nil {
		log.Fatalf("Connect: %v", err)
	}
	defer s.Close()

	http.HandleFunc("/import", handleImport(s))
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Write([]byte("Indexer. POST /import with JSON body: { \"meta\": {...}, \"objects\": [...], \"relations\": [...] }\n"))
	})

	log.Printf("HTTP indexer listening on %s", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("HTTP server: %v", err)
	}
}

func handleImport(s store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			w.Header().Set("Allow", "POST")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var payload SnapshotPayload
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
			http.Error(w, "invalid JSON: "+err.Error(), http.StatusBadRequest)
			return
		}
		if err := s.Import(r.Context(), payload.Meta, payload.Objects, payload.Relations); err != nil {
			log.Printf("Import error: %v", err)
			http.Error(w, "import failed: "+err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		resp := map[string]any{
			"ok":               true,
			"objectCount":      len(payload.Objects),
			"relationsImported": len(payload.Relations),
			"configName":       payload.Meta.ConfigName,
			"configVersion":    payload.Meta.ConfigVersion,
		}
		_ = json.NewEncoder(w).Encode(resp)
	}
}
