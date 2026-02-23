package ru.mcp.structure.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Использование объекта метаданных как типа в другом объекте
 * (реквизит, измерение, ресурс, колонка табличной части, тип значения константы).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TypeUsage {

    private String objectId;
    private String objectName;
    private String objectSynonym;
    /** requisite | dimension | resource | tabularSection | constant */
    private String role;
    private String propName;
    private String tabularSectionName;

    public TypeUsage() {
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectSynonym() {
        return objectSynonym;
    }

    public void setObjectSynonym(String objectSynonym) {
        this.objectSynonym = objectSynonym;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName;
    }

    public String getTabularSectionName() {
        return tabularSectionName;
    }

    public void setTabularSectionName(String tabularSectionName) {
        this.tabularSectionName = tabularSectionName;
    }
}
