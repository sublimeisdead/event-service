package ru.nahodka.bi.services.eventservice.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import ru.nahodka.bi.core.Util;
import ru.nahodka.bi.core.common.DataRolapHandler;
import ru.nahodka.bi.core.common.ExtraField;
import ru.nahodka.bi.core.common.SelectItem;
import ru.nahodka.bi.core.connection.interfaces.DataSourceBuilder;
import ru.nahodka.bi.core.constant.SqlConstant;
import ru.nahodka.bi.core.dao.impl.hibernate.DataProcessor;
import ru.nahodka.bi.core.dao.interfaces.domain.*;
import ru.nahodka.bi.core.dao.interfaces.domain.notifications.EventDAO;
import ru.nahodka.bi.core.dao.interfaces.domain.notifications.EventTypeDAO;
import ru.nahodka.bi.core.domain.*;
import ru.nahodka.bi.core.domain.notifications.EventType;
import ru.nahodka.bi.core.notifications.data.DataField;
import ru.nahodka.bi.core.notifications.data.DimensionItem;
import ru.nahodka.bi.core.notifications.data.EventDataTable;
import ru.nahodka.bi.core.notifications.data.FactValueItem;
import ru.nahodka.bi.services.XmlConverter;
import ru.nahodka.bi.services.common.schemas.facts.DimensionValue;
import ru.nahodka.bi.services.common.schemas.facts.Fact;
import ru.nahodka.bi.services.common.schemas.facts.FactValue;
import ru.nahodka.bi.services.common.schemas.references.ReferenceLink;
import ru.nahodka.bi.services.common.schemas.types.AnyTypeValue;
import ru.nahodka.bi.services.common.schemas.types.ValueType;
import ru.nahodka.bi.services.eventservice.schemas.*;
import ru.nahodka.bi.services.eventservice.schemas.Dimension;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;
import static ru.nahodka.bi.services.eventservice.error.Error.*;
import static ru.nahodka.bi.services.eventservice.util.Util.asString;
import static ru.nahodka.bi.services.eventservice.util.Util.toDate;

@Service
public class EventServiceEndpoint implements EventServicePort {

    private final Logger logger = Logger.getLogger(this.getClass());

    @Resource
    private EventTypeDAO eventTypeDAO;

    @Resource
    private EventDAO eventDAO;

    @Resource
    private DatasourceDAO datasourceDAO;

    @Resource
    private DimensionDAO dimensionDAO;

    @Resource
    private FactTableDAO factTableDAO;

    @Resource
    private DimensionInFactTableDAO dimensionInFactTableDAO;

    @Resource
    private FactTableValueFieldDAO factTableValueFieldDAO;

    @Resource
    private DataProcessor dataProcessor;

    @Resource
    private MessageSource messageSource;

    @Resource
    private XmlConverter xmlConverter;

    @Resource
    private DataSourceBuilder builder;

    public RegisterEventResponse registerEvent(Event registerEventRequest) throws BiException {


        /*
         *  Сборка фактов
         */


        if (registerEventRequest.getFact() == null) {
            throw emptyFactsException();
        }


        Fact soapFact = registerEventRequest.getFact();
        Long soapDatasourceId = soapFact.getDatasourceCode();

        String soapFactTableName = soapFact.getFactTypeCode();


        // Источник данных должен существовать в базе
        Datasource datasource = datasourceDAO.getDatasourceById(soapDatasourceId);
        if (datasource == null) {
            throw nonExistingDatasourceCodeException(String.valueOf(soapDatasourceId));
        }

        // Таблица фактов должен существовать в базе
        Long factTableId = factTableDAO.getFactTableId(soapDatasourceId, soapFactTableName);
        if (factTableId == null) {
            throw nonExistingFactTypeCodeException(soapFactTableName);
        }

        // Измерения

        if (soapFact.getDimensionValues() == null) {
            throw emptyDimensionsException();
        }

        List<DimensionValue> soapDimensionItems = soapFact.getDimensionValues();
        if (soapDimensionItems == null || soapDimensionItems.size() == 0) {
            throw emptyDimensionsException();
        }

        List<Object> params = new ArrayList<>();
        StringBuilder insert = new StringBuilder(SqlConstant.INSERT);
        StringBuilder values = new StringBuilder(SqlConstant.VALUES);

        EventDataTable eventDataTable = new EventDataTable();

        eventDataTable.setFactTableName(factTableDAO.getFactTableName(factTableId));

        insert.append(Util.wrapValue(soapFact.getFactTypeCode()))
                .append(" (id, ")
                .append(Util.wrapValue("ETLDate"))
                .append(", ");
        values.append("?, ?,");
        params.add(soapFact.getFactIdentifier());
        params.add(new Date());


        ru.nahodka.bi.core.notifications.data.Fact fact = new ru.nahodka.bi.core.notifications.data.Fact();
        List<FactValueItem> factValueItems = new ArrayList<>();
        // Значение факта
        for (FactValue soapFactValue:soapFact.getFactValues()) {
            String soapFactTableValueFieldName = soapFactValue.getFactValueCode();
            insert.append(Util.wrapValue(soapFactTableValueFieldName))
                    .append(", ");
            addParam(soapFactValue.getValue(), params);
            values.append("?, ");

            FactTableValueField factTableValueField = factTableValueFieldDAO.get(factTableId, soapFactTableValueFieldName);

            // Значение факта должно существовать в базе
            if (factTableValueField == null) {
                throw nonExistingFactValueTypeCodeException(factTableId);
            }


            FactValueItem factValueItem = new FactValueItem();
            factValueItem.setCommentaryValue(soapFactValue.getComment());
            factValueItem.setValue(asString(soapFactValue.getValue()));
            factValueItem.setName(factTableValueField.getName());
            factValueItem.setFieldName(factTableValueField.getFactTableField());

            factValueItems.add(factValueItem);
        }

        List<DimensionItem> dimensionItems = new ArrayList<>();

        List<DimensionInFactTable> dimensionInFactTables =
                dimensionInFactTableDAO.getDimensionInFactTableByFactTableId(factTableId);

        for (DimensionValue soapDimensionItem : soapDimensionItems) {
            ReferenceLink referenceLink = soapDimensionItem.getReferenceLink();

            boolean hasSeparatedTable;
            hasSeparatedTable = referenceLink != null;

            String soapDimensionAlias = soapDimensionItem.getDimensionAlias();
            String soapDimensionName;
            if (!hasSeparatedTable) {
                addParam(soapDimensionItem.getValue(), params);
                soapDimensionName = soapDimensionItem.getDimensionCode();
            } else {
                soapDimensionName = referenceLink.getTypeCode();
                params.add(referenceLink.getItemCode());
            }
            insert.append(Util.wrapValue(soapDimensionItem.getDimensionCode()))
                    .append(", ");
            values.append("?, ");

            DimensionInFactTable dimensionInFactTableDb = null;
            Iterator<DimensionInFactTable> iterator = dimensionInFactTables.iterator();
            boolean found = false;
            while (iterator.hasNext() && !found) {
                DimensionInFactTable dimensionInFactTable = iterator.next();

                String dbDimensionName;
                if (dimensionInFactTable.getDimension().isHasSeparatedTable()){
                    dbDimensionName = dimensionInFactTable.getDimension().getTable();
                }else{
                    dbDimensionName = dimensionInFactTable.getFactTableField();
                }
                String dbDimensionAlias = dimensionInFactTable.getAlias();

                if (dbDimensionName.equals(soapDimensionName)) {
                    if (dbDimensionAlias == null) {
                        if (soapDimensionAlias == null) {
                            dimensionInFactTableDb = dimensionInFactTable;
                            found = true;
                        } else {
                            throw ambiguousDimensionReferenceException(soapDimensionName, soapDimensionAlias);
                        }
                    } else {
                        // dbDimensionAlias != null
                        if (soapDimensionAlias == null) {
                            throw ambiguousDimensionReferenceException(soapDimensionName, null);

                        } else {
                            if (dbDimensionAlias.equals(soapDimensionAlias)) {
                                dimensionInFactTableDb = dimensionInFactTable;
                                break;
                            } else {
                                throw nonExistingDimensionInFactTableException(soapDimensionName, soapDimensionAlias);
                            }
                        }
                    }
                }
            }
            if (dimensionInFactTableDb == null) {
                throw nonExistingDimensionInFactTableException(soapDimensionName, soapDimensionAlias);
            }

            if (dimensionInFactTableDb.getDimension().isHasSeparatedTable() != hasSeparatedTable) {
                throw dimensionConflictWithSeparatedTableUsageException(soapDimensionName, soapDimensionAlias);
            }


            DimensionItem dimensionItem = new DimensionItem();

            ru.nahodka.bi.core.domain.Dimension dimension = dimensionInFactTableDb.getDimension();
            dimensionItem.setName(dimension.getName());

            String pseudonym = soapDimensionAlias == null ? soapDimensionName : soapDimensionAlias;
            dimensionItem.setPseudonym(pseudonym);

            if (!hasSeparatedTable) {

                //то это измерение без таблицы

                dimensionItem.setDataFields(new ArrayList<>());

                AnyTypeValue value = soapDimensionItem.getValue();
                if (value == null) {
                    throw missingDimensionValueException(soapDimensionName);
                }
                dimensionItem.setValue(asString(value));

            } else {
                //это измерение с таблицей
                long dimensionId = referenceLink.getItemCode();
                SelectItem dimensionRow;
                DataRolapHandler dimensionHandler = null;
                try {
                    dimensionHandler = new DataRolapHandler(dimension, dataProcessor, messageSource, builder);
                    dimensionRow = dimensionHandler.getHandbookByRecordId(soapDimensionItem.getReferenceLink()
                                                                                            .getItemCode());
                } catch (Exception e) {
                    logger.error("Произошла ошибка при получении записи в справочнике измерений", e);
                    throw internalErrorException();
                } finally {
                    if (dimensionHandler != null){
                        dimensionHandler.closeConnection();
                    }
                }

                if (dimensionRow == null) {
                    throw nonExistingDimensionItemCodeException(soapDimensionName, dimensionId);
                }

                dimensionItem.setValue(dimensionRow.getValue());

                List<DataField> dataFields = new ArrayList<>();
                if (dimensionRow.getExtraFields() != null) {
                    for (ExtraField extraField : dimensionRow.getExtraFields()) {

                        DataField dataField = new DataField();
                        dataField.setFieldName(extraField.getFieldName());
                        String name = dimension.findExtraFieldByFieldName(extraField.getFieldName()).getFieldName();
                        dataField.setName(name);
                        dataField.setValue(extraField.getValue() != null ?extraField.getValue().toString():null);
                        dataFields.add(dataField);
                    }
                }

                dimensionItem.setDataFields(dataFields);

            }

            dimensionItems.add(dimensionItem);
        }

        fact.setDimensionItems(dimensionItems);
        fact.setFactValueItems(factValueItems);
        insert.deleteCharAt(insert.lastIndexOf(", "))
                .append(")")
                .append(values.deleteCharAt(values.lastIndexOf(", ")).append(")"));

        /*
         * Сборка события
         */

        ru.nahodka.bi.core.domain.notifications.Event newEvent = new ru.nahodka.bi.core.domain.notifications.Event();

        // Тип события
        long eventTypeCode = eventTypeDAO.getEventTypeIdByFactTable(factTableId);
        if (!eventTypeDAO.existsEventType(eventTypeCode)) {
            throw nonExistingEventTypeCodeException(eventTypeCode);
        }
        EventType eventType = new EventType();
        eventType.setId(eventTypeCode);
        eventType.setEvents(Sets.newHashSet(newEvent));
        newEvent.setEventType(eventType);
        List<ru.nahodka.bi.core.notifications.data.Fact> facts = new ArrayList<>();
        facts.add(fact);
        eventDataTable.setFacts(facts);

        // Собранные факты

        newEvent.setProcessedMessage(eventDataTable);

        // Пометка, что событие еще не обработано

        newEvent.setHandled(false);

        String xml;
        try {
            xml = xmlConverter.objectToXml(registerEventRequest);
        } catch (IOException e) {
            logger.error("Не удалось сериализовать запрос в XML", e);
            throw internalErrorException();
        }
        newEvent.setOriginalMessage(xml);

        // Дата события

        Date soapEventDate = toDate(registerEventRequest.getSendDate());
        if (soapEventDate == null) {
            throw missingDateException("SendDate");
        }
        newEvent.setDate(soapEventDate);

        DataRolapHandler factTableHandler = null;
        try {
            factTableHandler = new DataRolapHandler(datasource, dataProcessor, messageSource, builder);
            factTableHandler.openConnection();
            factTableHandler.executeQuery(insert.toString(), params);
        } catch (Exception e) {
            logger.error("Произошла ошибка при добавлении записи", e);
            throw internalErrorException();
        } finally {
            if (factTableHandler != null){
                factTableHandler.closeConnection();
            }
        }
        /*
         * Создаем событие
         */

        eventDAO.createEvent(newEvent);
        /*
         * Возвращаем результат
         */
        String successMessage = format("Событие зарегистрировано с номером {0}", Long.toString(newEvent.getId()));
        logger.info(successMessage);

        RegisterEventResponse response = new RegisterEventResponse();
        response.setMessage(successMessage);

        return response;
    }

    @Override
    public RegisterDataResponse registerData(Data registerDataRequest) throws BiException {
        StringBuilder query = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Fact fact: registerDataRequest.getFacts()){
            FactTable factTable = factTableDAO.getFactTableByName(fact.getFactTypeCode());
            factTable.getDatasource();
            Map<DimensionValue, DimensionInFactTable> dimensionMap = new HashMap<>();
            Map<FactValue, FactTableValueField> valueMap = new HashMap<>();
            for (DimensionValue dimensionValue: fact.getDimensionValues()){
                Optional<DimensionInFactTable> first = factTable.getDimensionInFactTable().stream()
                        .filter(dimensionInFactTable -> dimensionValue.getDimensionCode().equalsIgnoreCase(dimensionInFactTable.getFactTableField()))
                        .findFirst();
                first.ifPresent(dimensionInFactTable -> dimensionMap.put(dimensionValue, dimensionInFactTable));
            }
            for (FactValue value: fact.getFactValues()){
                Optional<FactTableValueField> first = factTable.getFactTableValueFields().stream()
                        .filter(factTableValueField -> value.getFactValueCode().equalsIgnoreCase(factTableValueField.getFactTableField()))
                        .findFirst();
                first.ifPresent(factTableValueField -> valueMap.put(value, factTableValueField));
            }
            query.append("Insert into ")
                    .append(Util.wrapValue(factTable.getTable()))
                    .append("(");
            query.append(valueMap.values().stream().map(
                    factTableValueField -> Util.wrapValue(factTableValueField.getFactTableField())).collect(Collectors.joining(",")))
                    .append(",")
                    .append(
                    dimensionMap.values()
                            .stream()
                            .map(dimensionInFactTable -> Util.wrapValue(dimensionInFactTable.getFactTableField()))
                            .collect(Collectors.joining(",")));
            factTable.getDimensionInFactTable().removeAll(dimensionMap.values());
            factTable.getFactTableValueFields().removeAll(valueMap.values());
            valueMap.keySet().forEach(factValue -> addParam(factValue.getValue(), params));
            dimensionMap.keySet().forEach(dimensionValue -> {
                if (dimensionValue.getDimensionCode().equalsIgnoreCase("DateField")){
                    if (fact.getFactTypeCode().contains("month")){
                        params.add(dimensionValue.getValue().getStringValue() + "01");
                    }else if (fact.getFactTypeCode().contains("month")){
                        String quarter = dimensionValue.getValue().getStringValue().substring(dimensionValue.getValue().getStringValue().length() - 2, dimensionValue.getValue().getStringValue().length());
                        if (quarter.equalsIgnoreCase("01")){
                            params.add(dimensionValue.getValue().getStringValue().substring(0, dimensionValue.getValue().getStringValue().length() - 2) + "0101");
                        }else if (quarter.equalsIgnoreCase("02")){
                            params.add(dimensionValue.getValue().getStringValue().substring(0, dimensionValue.getValue().getStringValue().length() - 2) + "0401");
                        }else if (quarter.equalsIgnoreCase("03")){
                            params.add(dimensionValue.getValue().getStringValue().substring(0, dimensionValue.getValue().getStringValue().length() - 2) + "0601");
                        }else if (quarter.equalsIgnoreCase("04")){
                            params.add(dimensionValue.getValue().getStringValue().substring(0, dimensionValue.getValue().getStringValue().length() - 2) + "1101");
                        }
                    }else addParam(dimensionValue.getValue(), params);

                }else {
                    addParam(dimensionValue.getValue(), params);
                }
            });
            String user = factTable.getDimensionInFactTable().stream()
                    .map(dimensionInFactTable -> {
                        if (dimensionInFactTable.getDimension().getName().equalsIgnoreCase("ФИО объекта -пользователя")){
                            params.add("Все пользователи");
                        }else{
                            params.add(0);
                        }
                        return Util.wrapValue(dimensionInFactTable.getFactTableField());
                    })
                    .collect(Collectors.joining(","));
            query.append(",");
            if (user.length() > 0) {
                query
                        .append(factTable.getDimensionInFactTable().stream()
                                .map(dimensionInFactTable -> {
                                    if (dimensionInFactTable.getDimension().getName().equalsIgnoreCase("ФИО объекта -пользователя")) {
                                        params.add("Все пользователи");
                                    } else {
                                        params.add(0);
                                    }
                                    return Util.wrapValue(dimensionInFactTable.getFactTableField());
                                })
                                .collect(Collectors.joining(",")))
                        .append(",");
            }
                    query.append(Util.wrapValue("ETLDate"))
                    .append(") values(");
            int valuesCount = dimensionMap.values().size() + valueMap.values().size() +
                    factTable.getDimensionInFactTable().size() + factTable.getFactTableValueFields().size();
            SimpleDateFormat dateFormat  = new SimpleDateFormat("YYYY-MM-DD HH:mm:ss");
            query.append(
                    Collections.nCopies(valuesCount, "?")
                            .stream().collect(Collectors.joining(",")))
                    .append(", to_timestamp('")
                    .append(dateFormat.format(new Date()))
                    .append("', 'YYYY-MM-DD HH24:MI:ss')")
                    .append(")")
                    .append(" ON CONFLICT ON CONSTRAINT ")
                    .append(Util.wrapValue(fact.getFactTypeCode() + "_constraint"))
                    .append(" do UPDATE set  ");
            valueMap.forEach((factValue, factTableValueField) -> {
                query.append(Util.wrapValue(factValue.getFactValueCode()))
                        .append("= ?, ");

                addParam(factValue.getValue(), params);
            });
            query.deleteCharAt(query.lastIndexOf(","));
            DataRolapHandler factTableHandler = null;
            try {
                factTableHandler = new DataRolapHandler(factTable.getDatasource(), dataProcessor, messageSource, builder);
                factTableHandler.openConnection();
                factTableHandler.executeQuery(query.toString(), params);
            } catch (Exception e) {
                logger.error("Произошла ошибка при добавлении записи", e);
                throw internalErrorException();
            } finally {
                if (factTableHandler != null){
                    factTableHandler.closeConnection();
                }
            }
        }
        RegisterDataResponse result = new RegisterDataResponse();
        result.setMessage("Сообщения зарегистрированы");
        return result;
    }

    @Override
    public RegisterDimensionResponse registerDimension(Dimension registerDimensionRequest) throws BiException {

        if(registerDimensionRequest.getDimensions()==null){
            throw emptyDimensionsException();
        }

        List<ru.nahodka.bi.services.common.schemas.dimensions.Dimension> dimensions=registerDimensionRequest.getDimensions();

        for(ru.nahodka.bi.services.common.schemas.dimensions.Dimension dimension : dimensions){
         //   Long dimId = dimension.getDimensionId();
         //   if(dimId==null){
         //       throw emptyDimensionsException();
         //   }
            String dimName=dimension.getDimensionName();
            if(dimName==null){
                throw emptyDimensionsException();
            }
            String dimType=dimension.getDimensionType();
            if(dimType==null){
                throw emptyDimensionsException();
            }
            Boolean dimHasSepTable = dimension.isDimensionHasSeparatedTable();
            if(dimHasSepTable==null){
                throw emptyDimensionsException();
            }
            String dimTable=dimension.getDimensionTable();
            String tableFieldId=dimension.getTableFieldId();
            String tableFieldValue=dimension.getTableFieldValue();
            Boolean dimIsDeleted=dimension.isDimensionIsDeleted();
            if(dimIsDeleted==null){
                throw emptyDimensionsException();
            }
            String dimExtraFields=dimension.getTableExtraFields();
            String dimSett=dimension.getDimensionSettings();
            Long dimDatasourceCode=dimension.getDatasourceCode();
            ValueType dimValueType=dimension.getValueType();
            if(dimValueType==null){
                throw emptyDimensionsException();
            }
            String dimETLName=dimension.getETLName();






            ObjectMapper objectMapper=new ObjectMapper();
            TableExtraFields tableExtraField=null;
            DimensionSettings dimensionSettings=null;
            try {
                tableExtraField=objectMapper.readValue(dimExtraFields,TableExtraFields.class);
                dimensionSettings=objectMapper.readValue(dimSett,DimensionSettings.class);
            } catch (IOException e) {
                e.printStackTrace();
            }

            ru.nahodka.bi.core.domain.Dimension dimToSave = new ru.nahodka.bi.core.domain.Dimension();
        //    dimToSave.setId(dimId);
            dimToSave.setName(dimName);
            dimToSave.setType(dimType);
            dimToSave.setHasSeparatedTable(dimHasSepTable);
            if(dimTable.equalsIgnoreCase("") || dimTable.equalsIgnoreCase("null")){
                dimToSave.setTable(null);
            }else{
                dimToSave.setTable(dimTable);
            }
            if(tableFieldId.equalsIgnoreCase("") || tableFieldId.equalsIgnoreCase("null")){
                dimToSave.setTableFieldId(null);
            }else{
                dimToSave.setTableFieldId(tableFieldId);
            }
            if(tableFieldValue.equalsIgnoreCase("") || tableFieldValue.equalsIgnoreCase("null")){
                dimToSave.setTableFieldValue(null);
            }else{
                dimToSave.setTableFieldValue(tableFieldValue);
            }


            dimToSave.setDeleted(dimIsDeleted);
            dimToSave.setTableExtraFields(tableExtraField);
            dimToSave.setSettings(dimensionSettings);
            dimToSave.setDatasource(datasourceDAO.getDatasourceById(dimDatasourceCode));
            String vt=dimValueType.toString().toUpperCase();
            dimToSave.setValueType(ru.nahodka.bi.core.constant.enums.ValueType.valueOf(vt));

            dimensionDAO.saveDimension(dimToSave);
        }

        RegisterDimensionResponse message= new RegisterDimensionResponse();
        message.setMessage("Измерения успешно записаны");
        return message;
    }

    private void addParam(AnyTypeValue value, List<Object> params){
        if (value.isBooleanValue() != null){
            params.add(value.isBooleanValue());
        }else if (value.getFloatValue() != null){
            params.add(value.getFloatValue());
        }else if (value.getStringValue() != null){
            params.add(value.getStringValue());
        }else if (value.getIntValue() != null){
            params.add(value.getIntValue());
        }else if (value.getDateTimeValue() != null){
            params.add(value.getDateTimeValue().toGregorianCalendar().getTime());
        }
    }

}
