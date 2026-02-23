package ru.mcp.structure.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Meta {

    private String version;
    private String configName;
    private String configVersion;
    private String exportedAt;
    private String source;
    private int objectCount;
    private int indexVersion;

    public Meta() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getConfigName() { return configName; }
    public void setConfigName(String configName) { this.configName = configName; }
    public String getConfigVersion() { return configVersion; }
    public void setConfigVersion(String configVersion) { this.configVersion = configVersion; }
    public String getExportedAt() { return exportedAt; }
    public void setExportedAt(String exportedAt) { this.exportedAt = exportedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getObjectCount() { return objectCount; }
    public void setObjectCount(int objectCount) { this.objectCount = objectCount; }
    public int getIndexVersion() { return indexVersion; }
    public void setIndexVersion(int indexVersion) { this.indexVersion = indexVersion; }
}
