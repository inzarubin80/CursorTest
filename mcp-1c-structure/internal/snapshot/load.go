package snapshot

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
)

// LoadMeta reads meta.json from rootDir. rootDir must be an absolute path; path traversal is rejected.
func LoadMeta(rootDir string) (Meta, error) {
	path := filepath.Join(rootDir, "meta.json")
	if err := ensureInsideRoot(rootDir, path); err != nil {
		return Meta{}, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return Meta{}, err
	}
	var m Meta
	if err := json.Unmarshal(data, &m); err != nil {
		return Meta{}, err
	}
	return m, nil
}

// LoadObjects reads objects.json from rootDir (JSON array).
func LoadObjects(rootDir string) ([]Object, error) {
	path := filepath.Join(rootDir, "objects.json")
	if err := ensureInsideRoot(rootDir, path); err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var list []Object
	if err := json.Unmarshal(data, &list); err != nil {
		return nil, err
	}
	return list, nil
}

// LoadRelations reads relations.json from rootDir.
func LoadRelations(rootDir string) ([]Relation, error) {
	path := filepath.Join(rootDir, "relations.json")
	if err := ensureInsideRoot(rootDir, path); err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var list []Relation
	if err := json.Unmarshal(data, &list); err != nil {
		return nil, err
	}
	return list, nil
}

// LoadSnapshot loads meta, objects, and relations from rootDir. rootDir must be absolute.
func LoadSnapshot(rootDir string) (Meta, []Object, []Relation, error) {
	rootDir = filepath.Clean(rootDir)
	if rootDir == "" || rootDir == "." {
		abs, err := filepath.Abs(rootDir)
		if err != nil {
			return Meta{}, nil, nil, err
		}
		rootDir = abs
	}
	meta, err := LoadMeta(rootDir)
	if err != nil {
		return Meta{}, nil, nil, err
	}
	objects, err := LoadObjects(rootDir)
	if err != nil {
		return Meta{}, nil, nil, err
	}
	relations, err := LoadRelations(rootDir)
	if err != nil {
		return Meta{}, nil, nil, err
	}
	return meta, objects, relations, nil
}

func ensureInsideRoot(root, path string) error {
	absPath, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	absRoot, err := filepath.Abs(root)
	if err != nil {
		return err
	}
	rel, err := filepath.Rel(absRoot, absPath)
	if err != nil {
		return err
	}
	if strings.HasPrefix(rel, "..") {
		return errors.New("path outside snapshot root")
	}
	return nil
}
