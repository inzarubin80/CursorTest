package ru.mcp.structure.snapshot;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Загрузка снимка структуры 1С из файла «Структура базы данных» (XML).
 * Содержит объекты метаданных с реквизитами и табличными частями (props, tabularSections).
 * Парсинг потоковый (StAX), без загрузки всего DOM в память.
 */
public final class StructureXmlLoader {

    private static final Set<String> APPLICATION_OBJECT_TYPES = Set.of(
            "Документ", "Справочник", "Константа",
            "РегистрСведений", "Регистр сведений",
            "РегистрНакопления", "Регистр накопления",
            "РегистрБухгалтерии", "Регистр бухгалтерии",
            "РегистрРасчета", "Регистр расчёта",
            "Перечисление", "ПланВидовХарактерик", "План видов характеристик",
            "Отчет", "Отчёт", "Обработка",
            "ПланСчетов", "План счетов",
            "ПланОбмена", "ОбщийМодуль", "Общий модуль"
    );

    private static final Set<String> HEADER_PROPERTY_KINDS = Set.of("Реквизит", "Свойство");
    private static final String TABULAR_PART_KIND = "ТабличнаяЧасть";
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";
    /** Тип объекта «Набор констант» в XML; отдельные константы — его свойства. */
    private static final String CONSTANTS_SET_TYPE = "НаборКонстант";
    private static final String CONSTANT_PREFIX = "const";
    private static final String CONSTANT_TYPE_EN = "Constant";

    private static final Map<String, String> TYPE_TO_PREFIX = new HashMap<>();
    private static final Map<String, String> TYPE_TO_ENGLISH = new HashMap<>();

    static {
        TYPE_TO_PREFIX.put("Документ", "doc");
        TYPE_TO_PREFIX.put("Справочник", "cat");
        TYPE_TO_PREFIX.put("ОбщийМодуль", "commonmodule");
        TYPE_TO_PREFIX.put("Общий модуль", "commonmodule");
        TYPE_TO_PREFIX.put("РегистрСведений", "inforeg");
        TYPE_TO_PREFIX.put("Регистр сведений", "inforeg");
        TYPE_TO_PREFIX.put("РегистрНакопления", "accreg");
        TYPE_TO_PREFIX.put("Регистр накопления", "accreg");
        TYPE_TO_PREFIX.put("РегистрБухгалтерии", "acctreg");
        TYPE_TO_PREFIX.put("Регистр бухгалтерии", "acctreg");
        TYPE_TO_PREFIX.put("Константа", "const");
        TYPE_TO_PREFIX.put("Перечисление", "enum");
        TYPE_TO_PREFIX.put("ПланВидовХарактерик", "chartofcharacteristictypes");
        TYPE_TO_PREFIX.put("План видов характеристик", "chartofcharacteristictypes");
        TYPE_TO_PREFIX.put("Отчет", "report");
        TYPE_TO_PREFIX.put("Отчёт", "report");
        TYPE_TO_PREFIX.put("Обработка", "dataprocessor");
        TYPE_TO_PREFIX.put("ПланСчетов", "chartofaccounts");
        TYPE_TO_PREFIX.put("План счетов", "chartofaccounts");
        TYPE_TO_PREFIX.put("РегистрРасчета", "calculationregister");
        TYPE_TO_PREFIX.put("Регистр расчёта", "calculationregister");
        TYPE_TO_PREFIX.put("ПланОбмена", "exchangeplan");

        TYPE_TO_ENGLISH.put("Документ", "Document");
        TYPE_TO_ENGLISH.put("Справочник", "Catalog");
        TYPE_TO_ENGLISH.put("ОбщийМодуль", "CommonModule");
        TYPE_TO_ENGLISH.put("Общий модуль", "CommonModule");
        TYPE_TO_ENGLISH.put("РегистрСведений", "InformationRegister");
        TYPE_TO_ENGLISH.put("Регистр сведений", "InformationRegister");
        TYPE_TO_ENGLISH.put("РегистрНакопления", "AccumulationRegister");
        TYPE_TO_ENGLISH.put("Регистр накопления", "AccumulationRegister");
        TYPE_TO_ENGLISH.put("РегистрБухгалтерии", "AccountingRegister");
        TYPE_TO_ENGLISH.put("Регистр бухгалтерии", "AccountingRegister");
        TYPE_TO_ENGLISH.put("Константа", "Constant");
        TYPE_TO_ENGLISH.put("Перечисление", "Enum");
        TYPE_TO_ENGLISH.put("ПланВидовХарактерик", "ChartOfCharacteristicTypes");
        TYPE_TO_ENGLISH.put("План видов характеристик", "ChartOfCharacteristicTypes");
        TYPE_TO_ENGLISH.put("Отчет", "Report");
        TYPE_TO_ENGLISH.put("Отчёт", "Report");
        TYPE_TO_ENGLISH.put("Обработка", "DataProcessor");
        TYPE_TO_ENGLISH.put("ПланСчетов", "ChartOfAccounts");
        TYPE_TO_ENGLISH.put("План счетов", "ChartOfAccounts");
        TYPE_TO_ENGLISH.put("РегистрРасчета", "CalculationRegister");
        TYPE_TO_ENGLISH.put("Регистр расчёта", "CalculationRegister");
        TYPE_TO_ENGLISH.put("ПланОбмена", "ExchangePlan");
    }

    /** Варианты префикса типа для построения usedIn: prefix объекта (cat, doc, ...) -> варианты строки типа (Справочник, СправочникСсылка, ...). */
    private static final Map<String, List<String>> PREFIX_TO_TYPE_VARIANTS = new HashMap<>();
    static {
        PREFIX_TO_TYPE_VARIANTS.put("cat", List.of("Справочник", "СправочникСсылка"));
        PREFIX_TO_TYPE_VARIANTS.put("doc", List.of("Документ", "ДокументСсылка"));
        PREFIX_TO_TYPE_VARIANTS.put("enum", List.of("Перечисление", "ПеречислениеСсылка"));
        PREFIX_TO_TYPE_VARIANTS.put("chartofaccounts", List.of("ПланСчетов", "План счетов", "ПланСчетовСсылка"));
        PREFIX_TO_TYPE_VARIANTS.put("chartofcharacteristictypes", List.of("ПланВидовХарактерик", "План видов характеристик", "ПланВидовХарактерикСсылка"));
        PREFIX_TO_TYPE_VARIANTS.put("inforeg", List.of("РегистрСведений", "Регистр сведений"));
        PREFIX_TO_TYPE_VARIANTS.put("accreg", List.of("РегистрНакопления", "Регистр накопления"));
        PREFIX_TO_TYPE_VARIANTS.put("acctreg", List.of("РегистрБухгалтерии", "Регистр бухгалтерии"));
        PREFIX_TO_TYPE_VARIANTS.put("calculationregister", List.of("РегистрРасчета", "Регистр расчёта"));
        PREFIX_TO_TYPE_VARIANTS.put("exchangeplan", List.of("ПланОбмена"));
    }

    /**
     * Загружает снимок из XML «Структура базы данных».
     *
     * @param xmlPath путь к XML-файлу
     * @return снимок (meta, objects с заполненными props и tabularSections, relations пустой)
     */
    public static SnapshotLoader.Snapshot load(Path xmlPath) throws IOException {
        Path p = xmlPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) {
            throw new IOException("Not a file: " + p);
        }

        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        String configName = null;
        Map<String, ObjRecord> objectsByRef = new HashMap<>();
        List<PropRecord> allProperties = new ArrayList<>();

        try (var in = Files.newInputStream(p)) {
            XMLStreamReader r = factory.createXMLStreamReader(in, StandardCharsets.UTF_8.name());
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if ("Конфигурация".equals(local)) {
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if ("Имя".equals(r.getAttributeLocalName(i))) {
                                configName = r.getAttributeValue(i);
                                break;
                            }
                        }
                    } else if ("CatalogObject.Объекты".equals(local)) {
                        ObjRecord obj = readObjectRecord(r);
                        if (obj != null) {
                            objectsByRef.put(obj.ref, obj);
                        }
                    } else if ("CatalogObject.Свойства".equals(local)) {
                        PropRecord prop = readPropertyRecord(r);
                        if (prop != null) {
                            allProperties.add(prop);
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("XML parse error: " + e.getMessage(), e);
        }

        Map<String, List<PropRecord>> propsByOwner = new HashMap<>();
        for (PropRecord pr : allProperties) {
            if (pr.ownerRef != null && !pr.ownerRef.isEmpty()) {
                propsByOwner.computeIfAbsent(pr.ownerRef, k -> new ArrayList<>()).add(pr);
            }
        }

        List<StructureObject> applicationObjects = new ArrayList<>();
        for (ObjRecord obj : objectsByRef.values()) {
            if (obj.typeRu == null || !APPLICATION_OBJECT_TYPES.contains(obj.typeRu)) {
                continue;
            }
            String prefix = TYPE_TO_PREFIX.getOrDefault(obj.typeRu, obj.typeRu != null ? obj.typeRu.toLowerCase().replace(" ", "") : "");
            String name = obj.name != null && !obj.name.isEmpty() ? obj.name : extractNameFromDescription(obj.description);
            if (name == null || name.isEmpty()) {
                continue;
            }
            String id = prefix + "." + name;
            String typeEn = TYPE_TO_ENGLISH.getOrDefault(obj.typeRu, obj.typeRu);

            StructureObject so = new StructureObject();
            so.setId(id);
            so.setType(typeEn);
            so.setName(name);
            so.setSynonym(obj.synonym != null ? obj.synonym : "");
            so.setDescription(obj.description != null ? obj.description : "");
            so.setContent(null);
            so.setProps(null);
            so.setTabularSections(null);
            so.setMovements(null);

            List<PropRecord> ownerProps = propsByOwner.get(obj.ref);
            if (ownerProps != null) {
                List<Prop> headerProps = new ArrayList<>();
                List<TabularSection> tabularSections = new ArrayList<>();
                List<PropRecord> tabularPartRefs = new ArrayList<>();

                for (PropRecord pr : ownerProps) {
                    boolean isTopLevel = ZERO_UUID.equals(pr.parentRef) || (pr.parentRef == null || pr.parentRef.isEmpty());
                    if (!isTopLevel) {
                        continue;
                    }
                    if (pr.kind != null && HEADER_PROPERTY_KINDS.contains(pr.kind)) {
                        Prop prop = new Prop();
                        prop.setName(pr.description != null ? pr.description : "");
                        prop.setType(resolveType(pr.typeRef, objectsByRef));
                        prop.setSynonym(pr.synonym != null ? pr.synonym : "");
                        headerProps.add(prop);
                    } else if (pr.kind != null && TABULAR_PART_KIND.equals(pr.kind)) {
                        tabularPartRefs.add(pr);
                    }
                }

                for (PropRecord tch : tabularPartRefs) {
                    if (tch.ref == null) continue;
                    TabularSection ts = new TabularSection();
                    ts.setName(tch.description != null ? tch.description : "");
                    ts.setProps(new ArrayList<>());
                    for (PropRecord pr : ownerProps) {
                        if (pr.kind != null && HEADER_PROPERTY_KINDS.contains(pr.kind) && tch.ref.equals(pr.parentRef)) {
                            Prop col = new Prop();
                            col.setName(pr.description != null ? pr.description : "");
                            col.setType(resolveType(pr.typeRef, objectsByRef));
                            col.setSynonym(pr.synonym != null ? pr.synonym : "");
                            ts.getProps().add(col);
                        }
                    }
                    tabularSections.add(ts);
                }

                if (!headerProps.isEmpty()) {
                    so.setProps(headerProps);
                }
                if (!tabularSections.isEmpty()) {
                    so.setTabularSections(tabularSections);
                }
            }

            applicationObjects.add(so);
        }

        // Константы в XML — свойства объекта КонстантыНабор (Тип=НаборКонстант). Разворачиваем в отдельные объекты типа Constant.
        for (ObjRecord obj : objectsByRef.values()) {
            if (!CONSTANTS_SET_TYPE.equals(obj.typeRu) || obj.ref == null) {
                continue;
            }
            List<PropRecord> ownerProps = propsByOwner.get(obj.ref);
            if (ownerProps == null) {
                continue;
            }
            for (PropRecord pr : ownerProps) {
                boolean isTopLevel = ZERO_UUID.equals(pr.parentRef) || (pr.parentRef == null || pr.parentRef.isEmpty());
                if (!isTopLevel) {
                    continue;
                }
                if (pr.kind == null || !HEADER_PROPERTY_KINDS.contains(pr.kind)) {
                    continue;
                }
                String constName = pr.description != null && !pr.description.isEmpty() ? pr.description : null;
                if (constName == null) {
                    continue;
                }
                StructureObject so = new StructureObject();
                so.setId(CONSTANT_PREFIX + "." + constName);
                so.setType(CONSTANT_TYPE_EN);
                so.setName(constName);
                so.setSynonym(pr.synonym != null ? pr.synonym : "");
                so.setDescription(pr.description != null ? pr.description : "");
                so.setContent(null);
                so.setMovements(null);
                so.setTabularSections(null);
                // Тип значения константы — один реквизит
                String valueType = resolveType(pr.typeRef, objectsByRef);
                so.setValueType(valueType.isEmpty() ? null : valueType);
                if (!valueType.isEmpty()) {
                    Prop valueProp = new Prop();
                    valueProp.setName("Значение");
                    valueProp.setType(valueType);
                    valueProp.setSynonym("Значение");
                    so.setProps(List.of(valueProp));
                } else {
                    so.setProps(null);
                }
                applicationObjects.add(so);
            }
        }

        buildUsedIn(applicationObjects);

        Meta meta = new Meta();
        meta.setVersion("1.0");
        meta.setConfigName(configName != null ? configName : "Structure XML");
        meta.setConfigVersion("");
        meta.setExportedAt("");
        meta.setSource("structure-xml");
        meta.setObjectCount(applicationObjects.size());
        meta.setIndexVersion(1);

        return new SnapshotLoader.Snapshot(meta, applicationObjects, Collections.emptyList());
    }

    /**
     * Заполняет usedIn у объектов: где каждый объект используется как тип (реквизиты, ТЧ, тип значения константы).
     */
    private static void buildUsedIn(List<StructureObject> applicationObjects) {
        Map<String, StructureObject> typeStringToObject = new HashMap<>();
        for (StructureObject so : applicationObjects) {
            if (so == null || so.getId() == null) continue;
            int dot = so.getId().indexOf('.');
            if (dot <= 0 || dot == so.getId().length() - 1) continue;
            String prefix = so.getId().substring(0, dot);
            String name = so.getId().substring(dot + 1);
            List<String> variants = PREFIX_TO_TYPE_VARIANTS.get(prefix);
            if (variants == null) continue;
            for (String typePrefix : variants) {
                typeStringToObject.put(typePrefix + "." + name, so);
            }
        }

        for (StructureObject so : applicationObjects) {
            if (so == null) continue;
            String ownerId = so.getId();
            String ownerName = so.getName() != null ? so.getName() : "";
            String ownerSynonym = so.getSynonym() != null ? so.getSynonym() : "";

            if (so.getProps() != null) {
                for (Prop p : so.getProps()) {
                    if (p == null || p.getType() == null || p.getType().isEmpty()) continue;
                    StructureObject target = typeStringToObject.get(p.getType());
                    if (target != null) {
                        if (target.getUsedIn() == null) target.setUsedIn(new ArrayList<>());
                        TypeUsage u = new TypeUsage();
                        u.setObjectId(ownerId);
                        u.setObjectName(ownerName);
                        u.setObjectSynonym(ownerSynonym);
                        u.setRole("requisite");
                        u.setPropName(p.getName() != null ? p.getName() : "");
                        u.setTabularSectionName(null);
                        target.getUsedIn().add(u);
                    }
                }
            }
            if (so.getTabularSections() != null) {
                for (TabularSection ts : so.getTabularSections()) {
                    if (ts == null || ts.getProps() == null) continue;
                    String tsName = ts.getName() != null ? ts.getName() : "";
                    for (Prop p : ts.getProps()) {
                        if (p == null || p.getType() == null || p.getType().isEmpty()) continue;
                        StructureObject target = typeStringToObject.get(p.getType());
                        if (target != null) {
                            if (target.getUsedIn() == null) target.setUsedIn(new ArrayList<>());
                            TypeUsage u = new TypeUsage();
                            u.setObjectId(ownerId);
                            u.setObjectName(ownerName);
                            u.setObjectSynonym(ownerSynonym);
                            u.setRole("tabularSection");
                            u.setPropName(p.getName() != null ? p.getName() : "");
                            u.setTabularSectionName(tsName);
                            target.getUsedIn().add(u);
                        }
                    }
                }
            }
            if (CONSTANT_TYPE_EN.equals(so.getType()) && so.getValueType() != null && !so.getValueType().isEmpty()) {
                StructureObject target = typeStringToObject.get(so.getValueType());
                if (target != null) {
                    if (target.getUsedIn() == null) target.setUsedIn(new ArrayList<>());
                    TypeUsage u = new TypeUsage();
                    u.setObjectId(ownerId);
                    u.setObjectName(ownerName);
                    u.setObjectSynonym(ownerSynonym);
                    u.setRole("constant");
                    u.setPropName("Значение");
                    u.setTabularSectionName(null);
                    target.getUsedIn().add(u);
                }
            }
        }
    }

    private static String extractNameFromDescription(String description) {
        if (description == null || description.isEmpty()) return null;
        int dot = description.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < description.length()) {
            return description.substring(dot + 1);
        }
        return description;
    }

    private static String resolveType(String typeRef, Map<String, ObjRecord> objectsByRef) {
        if (typeRef == null || typeRef.isEmpty()) return "";
        ObjRecord typeObj = objectsByRef.get(typeRef);
        if (typeObj == null) return typeRef;
        if (typeObj.description != null && !typeObj.description.isEmpty()) {
            return typeObj.description;
        }
        if (typeObj.name != null && !typeObj.name.isEmpty()) {
            return typeObj.typeRu != null ? typeObj.typeRu + "." + typeObj.name : typeObj.name;
        }
        return typeObj.typeRu != null ? typeObj.typeRu : "";
    }

    private static ObjRecord readObjectRecord(XMLStreamReader r) throws XMLStreamException {
        ObjRecord rec = new ObjRecord();
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = r.getLocalName();
                if ("Ref".equals(local)) {
                    rec.ref = trim(r.getElementText());
                    depth--;
                } else if ("Parent".equals(local)) {
                    rec.parentRef = trim(r.getElementText());
                    depth--;
                } else if ("Description".equals(local)) {
                    rec.description = trim(r.getElementText());
                    depth--;
                } else if ("Имя".equals(local)) {
                    rec.name = trim(r.getElementText());
                    depth--;
                } else if ("Синоним".equals(local)) {
                    rec.synonym = trim(r.getElementText());
                    depth--;
                } else if ("Тип".equals(local)) {
                    rec.typeRu = trim(r.getElementText());
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth == 0) break;
            }
        }
        return rec.ref != null ? rec : null;
    }

    private static PropRecord readPropertyRecord(XMLStreamReader r) throws XMLStreamException {
        PropRecord rec = new PropRecord();
        int depth = 1;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String local = r.getLocalName();
                if ("Ref".equals(local)) {
                    rec.ref = trim(r.getElementText());
                    depth--;
                } else if ("Owner".equals(local)) {
                    rec.ownerRef = trim(r.getElementText());
                    depth--;
                } else if ("Parent".equals(local)) {
                    rec.parentRef = trim(r.getElementText());
                    depth--;
                } else if ("Description".equals(local)) {
                    rec.description = trim(r.getElementText());
                    depth--;
                } else if ("Синоним".equals(local)) {
                    rec.synonym = trim(r.getElementText());
                    depth--;
                } else if ("Вид".equals(local)) {
                    rec.kind = trim(r.getElementText());
                    depth--;
                } else if ("Типы".equals(local)) {
                    rec.typeRef = readFirstTypeRef(r);
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth == 0) break;
            }
        }
        return rec.ownerRef != null ? rec : null;
    }

    /** Consumes the whole Типы subtree and returns the first Тип UUID, or null. */
    private static String readFirstTypeRef(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        String firstRef = null;
        while (depth > 0 && r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if ("Тип".equals(r.getLocalName()) && firstRef == null) {
                    firstRef = trim(r.getElementText());
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return firstRef;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class ObjRecord {
        String ref;
        String parentRef;
        String description;
        String name;
        String synonym;
        String typeRu;
    }

    private static final class PropRecord {
        String ref;
        String ownerRef;
        String parentRef;
        String description;
        String synonym;
        String kind;
        String typeRef;
    }
}
