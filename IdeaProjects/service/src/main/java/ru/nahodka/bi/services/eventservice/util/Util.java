package ru.nahodka.bi.services.eventservice.util;


import ru.nahodka.bi.services.common.schemas.types.AnyTypeValue;

import javax.xml.datatype.XMLGregorianCalendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {

    public static Date toDate(XMLGregorianCalendar calendar) {
        if (calendar == null) {
            return null;
        }
        return calendar.toGregorianCalendar().getTime();
    }

    public static String asString(AnyTypeValue anyTypeValue) {
        if (anyTypeValue.isBooleanValue() != null) {

            return Boolean.toString(anyTypeValue.isBooleanValue());
        } else if (anyTypeValue.getDateTimeValue() != null) {

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            return dateFormat.format(anyTypeValue.getDateTimeValue());
        } else if (anyTypeValue.getFloatValue() != null) {

            return Float.toString(anyTypeValue.getFloatValue());
        } else if (anyTypeValue.getStringValue() != null) {

            return anyTypeValue.getStringValue();
        } else {
            throw new IllegalArgumentException();
        }

    }
}
