package snapshot

type Meta struct {
	Version       string `json:"version"`
	ConfigName    string `json:"configName"`
	ConfigVersion string `json:"configVersion"`
	ExportedAt    string `json:"exportedAt"`
	Source        string `json:"source"`
	ObjectCount   int    `json:"objectCount"`
	IndexVersion  int    `json:"indexVersion"`
}

type Prop struct {
	Name    string `json:"name"`
	Type    string `json:"type"`
	Synonym string `json:"synonym"`
}

type TabularSection struct {
	Name  string `json:"name"`
	Props []Prop `json:"props"`
}

type Object struct {
	ID              string           `json:"id"`
	Type            string           `json:"type"`
	Name            string           `json:"name"`
	Synonym         string           `json:"synonym"`
	Props           []Prop           `json:"props"`
	TabularSections []TabularSection `json:"tabularSections"`
	Forms           []string         `json:"forms"`
	Modules         []string         `json:"modules"`
	Description     string           `json:"description"`
}

type Relation struct {
	From string `json:"from"`
	To   string `json:"to"`
	Kind string `json:"kind"`
}
