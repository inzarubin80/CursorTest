package ru.mcp.structure.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Объект метаданных 1С (один элемент из objects.json).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StructureObject {

    private String id;
    private String type;
    private String name;
    private String synonym;
    private List<Prop> props;
    private List<TabularSection> tabularSections;
    /** Для документов — id регистров, в которые документ может делать записи (движения). */
    private List<String> movements;
    private String description;
    /** Полное текстовое описание объекта (markdown), например из выгрузки RAG-формата (ZIP с objects.csv + md). */
    private String content;

    /** Вектор для представления «имя + синоним» (TF-IDF), не сериализуется в API. */
    @JsonIgnore
    private float[] embeddingObjectName;
    /** Вектор для представления «тип + синоним» или контент (TF-IDF), не сериализуется в API. */
    @JsonIgnore
    private float[] embeddingFriendlyName;

    public StructureObject() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSynonym() {
        return synonym;
    }

    public void setSynonym(String synonym) {
        this.synonym = synonym;
    }

    public List<Prop> getProps() {
        return props;
    }

    public void setProps(List<Prop> props) {
        this.props = props;
    }

    public List<TabularSection> getTabularSections() {
        return tabularSections;
    }

    public void setTabularSections(List<TabularSection> tabularSections) {
        this.tabularSections = tabularSections;
    }

    public List<String> getMovements() {
        return movements;
    }

    public void setMovements(List<String> movements) {
        this.movements = movements;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbeddingObjectName() {
        return embeddingObjectName;
    }

    public void setEmbeddingObjectName(float[] embeddingObjectName) {
        this.embeddingObjectName = embeddingObjectName;
    }

    public float[] getEmbeddingFriendlyName() {
        return embeddingFriendlyName;
    }

    public void setEmbeddingFriendlyName(float[] embeddingFriendlyName) {
        this.embeddingFriendlyName = embeddingFriendlyName;
    }
}
