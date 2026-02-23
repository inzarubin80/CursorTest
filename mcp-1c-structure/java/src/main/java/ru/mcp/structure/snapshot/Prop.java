package ru.mcp.structure.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Prop {

    private String name;
    private String type;
    private String synonym;

    public Prop() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSynonym() { return synonym; }
    public void setSynonym(String synonym) { this.synonym = synonym; }
}
