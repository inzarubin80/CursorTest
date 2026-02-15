package config

import (
	"os"
	"path/filepath"
)

func SnapshotDir() string {
	if dir := os.Getenv("MCP_1C_STRUCTURE_SNAPSHOT_DIR"); dir != "" {
		return filepath.Clean(dir)
	}
	if exe, err := os.Executable(); err == nil {
		if d := filepath.Join(filepath.Dir(exe), "snapshot"); dirExists(d) {
			return d
		}
	}
	if cwd, err := os.Getwd(); err == nil {
		if d := filepath.Join(cwd, "snapshot"); dirExists(d) {
			return d
		}
	}
	return ""
}

func DatabaseURL() string {
	if u := os.Getenv("MCP_1C_STRUCTURE_DATABASE_URL"); u != "" {
		return u
	}
	return os.Getenv("POSTGRES_DSN")
}

func dirExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}
