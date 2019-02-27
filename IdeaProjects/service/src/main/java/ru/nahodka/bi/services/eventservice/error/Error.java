package ru.nahodka.bi.services.eventservice.error;


import ru.nahodka.bi.services.common.schemas.exceptions.ErrorCode;
import ru.nahodka.bi.services.eventservice.schemas.BiException;

import static java.text.MessageFormat.format;

public class Error {

    private static BiException generateException(ErrorCode errorCode, String message, String details) {
        ru.nahodka.bi.services.common.schemas.exceptions.BiException faultInfo =
                new ru.nahodka.bi.services.common.schemas.exceptions.BiException();
        faultInfo.setDetails(details);
        faultInfo.setErrorCode(errorCode);
        return new BiException(message, faultInfo);
    }

    private static BiException generateException(ErrorCode errorCode, String message) {
        return generateException(errorCode, message, null);
    }

    public static BiException nonUniqueDatasourceCodeException() {

        String message = "Обнаружено неуникальное значение DatasourceCode";
        return generateException(ErrorCode.NON_UNIQUE_DATA_SOURCE_CODE, message);
    }
    public static BiException emptyFactsException() {

        String message = "Отсутствует, либо пуст обязательный элемент Fact";
        return generateException(ErrorCode.EMPTY_FACTS, message);
    }

    public static BiException missingDateException(String elementName) {

        String message = "Отсутствует обязательный элемент";
        String details = format("Element: {0}", elementName);
        return generateException(ErrorCode.MISSING_DATE, message, details);
    }


    public static BiException nonUniqueFactTypeCodeException() {

        String message = "Обнаружено неуникальное значение FactTypeCode";
        return generateException(ErrorCode.NON_UNIQUE_FACT_TYPE_CODE, message);
    }

    public static BiException nonExistingDatasourceCodeException(String datasourceCode) {

        String message = "Указанный DatasourceCode не существует";
        String details = format("DatasourceCode: {0}", datasourceCode);
        return generateException(ErrorCode.NON_EXISTING_DATA_SOURCE_CODE, message, details);
    }

    public static BiException nonExistingFactTypeCodeException(String factTypeCode) {

        String message = "Указанный FactTypeCode не существует";
        String details = format("FactTypeCode: {0}", factTypeCode);
        return generateException(ErrorCode.NON_EXISTING_FACT_TYPE_CODE, message, details);
    }

    public static BiException nonExistingFactValueTypeCodeException(long factValueTypeCode) {

        String message = "Указанный FactValueTypeCode не существует";
        String details = format("FactValueTypeCode: {0}", factValueTypeCode);
        return generateException(ErrorCode.NON_EXISTING_FACT_VALUE_TYPE_CODE, message, details);
    }

    public static BiException emptyDimensionsException() {

        String message = "Отсутствует, либо пуст обязательный элемент Dimensions";
        return generateException(ErrorCode.EMPTY_DIMENSIONS, message);
    }

    public static BiException ambiguousDimensionReferenceException(String dimensionCode, String dimensionAlias) {

        String message =
                "Невозможно определить измерение в таблице фактов, " +
                        "так существуют несколько ссылок на одно измерение внутри таблицы фактов. " +
                        "Проверьте корректность указанного DimensionAlias. ";
        String details = format("DimensionCode: {0}, DimensionAlias: {1}", dimensionCode, dimensionAlias);
        message += details;
        return generateException(ErrorCode.AMBIGIOUS_DIMENSION_REFERENCE, message, details);
    }

    public static BiException missingReferenceLinkInDimensionException(long dimensionCode) {

        String message = "Отсутствует обязательный элемент ReferenceLink";
        String details = format("DimensionCode: {0}", dimensionCode);
        return generateException(ErrorCode.MISSING_REFERENCE_LINK_IN_DIMENSION, message, details);
    }

    public static BiException internalErrorException() {

        String message = "Произошла внутренняя ошибка";
        return generateException(ErrorCode.INTERNAL_ERROR, message);
    }

    public static BiException nonExistingEventTypeCodeException(long eventTypeCode) {

        String message = "Указанный EventTypeCode не существует";
        String details = format("EventTypeCode: {0}", eventTypeCode);
        return generateException(ErrorCode.NON_EXISTING_EVENT_TYPE_CODE, message, details);
    }

    public static BiException nonExistingDimensionInFactTableException(String dimensionCode, String dimensionAlias) {

        String message =
                "Указанное измерение не найдено в таблице фактов. " +
                        "Проверьте корректность указанного DimensionAlias.";
        String details = format("DimensionCode: {0}, DimensionAlias: {1}", dimensionCode, dimensionAlias);
        return generateException(ErrorCode.NON_EXISTING_DIMENSION_IN_FACT_TABLE, message, details);
    }

    public static BiException dimensionConflictWithSeparatedTableUsageException(String dimensionCode, String dimensionAlias) {

        String message =
                "Предоставленные данные измерения конфилктуют со свойством наличия у измерения справочника.";
        String details = format("DimensionCode: {0}, DimensionAlias: {1}", dimensionCode, dimensionAlias);
        return generateException(ErrorCode.DIMENSION_CONFLICT_WITH_SEPARATED_TABLE_USAGE, message, details);
    }

    public static BiException missingDimensionValueException(String dimensionCode) {

        String message = "Отсутствует значение справочника указанного измерения без таблицы.";
        String details = format("DimensionCode: {0}", dimensionCode);
        return generateException(ErrorCode.MISSING_DIMENSION_VALUE, message, details);
    }

    public static BiException nonExistingDimensionItemCodeException(String typeCode, long itemCode) {

        String message = "Не существует записи с указанным кодом в справочнике измерений";
        String details = format("TypeCode: {0}, itemCode: {1}", typeCode, itemCode);
        return generateException(ErrorCode.NON_EXISTING_DIMENSION_ITEM_CODE, message, details);
    }

    public static BiException notEnoughDimensionsException() {

        String message = "В факте указаны не все измерения";
        return generateException(ErrorCode.NOT_ENOUGH_DIMENSIONS, message);
    }



}
