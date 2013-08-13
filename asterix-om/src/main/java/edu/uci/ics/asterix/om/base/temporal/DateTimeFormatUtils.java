/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.om.base.temporal;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TimeZone;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;

/**
 * {@link DateTimeFormatUtils} provides the utility methods to parse and print a date/time/datetime
 * value based on the given format string.
 */
public class DateTimeFormatUtils {

    private final GregorianCalendarSystem CAL = GregorianCalendarSystem.getInstance();

    // For time
    private final char HOUR_CHAR = 'h';
    private final char MINUTE_CHAR = 'm';
    private final char SECOND_CHAR = 's';
    private final char MILLISECOND_CHAR = 'n';
    private final char AMPM_CHAR = 'a';
    private final char TIMEZONE_CHAR = 'z';

    private final int MAX_HOUR_CHARS = 2;
    private final int MAX_MINUTE_CHARS = 2;
    private final int MAX_SECOND_CHARS = 2;
    private final int MAX_MILLISECOND_CHARS = 3;
    private final int MAX_AMPM_CHARS = 1;
    private final int MAX_TIMEZONE_CHARS = 1;

    private enum DateTimeProcessState {
        INIT,
        YEAR,
        MONTH,
        DAY,
        HOUR,
        MINUTE,
        SECOND,
        MILLISECOND,
        AMPM,
        TIMEZONE,
        SEPARATOR
    }

    // For date
    private final char YEAR_CHAR = 'Y';
    private final char MONTH_CHAR = 'M';
    private final char DAY_CHAR = 'D';

    private final int MAX_YEAR_CHARS = 4;
    private final int MAX_MONTH_CHARS = 3;
    private final int MAX_DAY_CHARS = 2;

    private final byte[][] MONTH_NAMES = new byte[][] { "jan".getBytes(), "feb".getBytes(), "mar".getBytes(),
            "apr".getBytes(), "may".getBytes(), "jun".getBytes(), "jul".getBytes(), "aug".getBytes(), "sep".getBytes(),
            "oct".getBytes(), "nov".getBytes(), "dec".getBytes() };

    private final byte[] UTC_BYTEARRAY = "utc".getBytes();
    private final byte[] GMT_BYTEARRAY = "gmt".getBytes();

    private final byte[] AM_BYTEARRAY = "am".getBytes();
    private final byte[] PM_BYTEARRAY = "pm".getBytes();

    // Separators, for both time and date
    private final char HYPHEN_CHAR = '-';
    private final char COLON_CHAR = ':';
    private final char SOLIDUS_CHAR = '/';
    private final char PERIOD_CHAR = '.';
    private final char COMMA_CHAR = ',';
    private final char T_CHAR = 'T';

    private final int MS_PER_MINUTE = 60 * 1000;
    private final int MS_PER_HOUR = 60 * MS_PER_MINUTE;

    private final byte TO_LOWER_OFFSET = 'A' - 'a';

    private final String[] TZ_IDS = TimeZone.getAvailableIDs();
    private final byte[][] TIMEZONE_IDS = new byte[TZ_IDS.length][];
    {
        Arrays.sort(TZ_IDS);
        for (int i = 0; i < TIMEZONE_IDS.length; i++) {
            TIMEZONE_IDS[i] = TZ_IDS[i].getBytes();
        }
    }

    private final int[] TIMEZONE_OFFSETS = new int[TIMEZONE_IDS.length];
    {
        for (int i = 0; i < TIMEZONE_IDS.length; i++) {
            TIMEZONE_OFFSETS[i] = TimeZone.getTimeZone(TZ_IDS[i]).getRawOffset();
        }
    }

    private static class DateTimeFormatUtilsHolder {
        private static final DateTimeFormatUtils INSTANCE = new DateTimeFormatUtils();
    }

    public static DateTimeFormatUtils getInstance() {
        return DateTimeFormatUtilsHolder.INSTANCE;
    }

    private DateTimeFormatUtils() {
    }

    private int parseFormatField(byte[] format, int formatStart, int formatLength, int formatPointer, char formatChar,
            int maxAllowedFormatCharCopied) {

        int formatCharCopies = 0;

        formatPointer++;
        formatCharCopies++;
        while (formatPointer < formatLength && format[formatStart + formatPointer] == formatChar) {
            formatPointer++;
            formatCharCopies++;
        }
        if (formatCharCopies > maxAllowedFormatCharCopied) {
            throw new IllegalStateException("The format string for " + formatChar
                    + " is too long: expected no more than " + maxAllowedFormatCharCopied + " but got "
                    + formatCharCopies);
        }

        return formatCharCopies;
    }

    public enum DateTimeParseMode {
        DATE_ONLY,
        TIME_ONLY,
        DATETIME
    }

    private boolean byteArrayEqualToString(byte[] barray, int start, int length, byte[] str) {
        boolean equal = true;
        if (length == str.length) {
            for (int i = 0; i < length; i++) {
                if (toLower(barray[start + i]) != str[i]) {
                    equal = false;
                }
            }
        } else {
            equal = false;
        }
        return equal;
    }

    private Comparator<byte[]> byteArrayComparator = new Comparator<byte[]>() {
        @Override
        public int compare(byte[] o1, byte[] o2) {
            int i = 0;
            for (; i < o1.length && i < o2.length; i++) {
                if (o1[i] != o2[i]) {
                    return o1[i] - o2[i];
                }
            }
            if (i < o1.length) {
                return -1;
            } else if (i < o2.length) {
                return 1;
            }
            return 0;
        }
    };

    private int monthIDSearch(byte[] barray, int start, int length) {
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (byteArrayEqualToString(barray, start, length, MONTH_NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    private int binaryTimezoneIDSearch(byte[] barray, int start, int length) {
        return Arrays.binarySearch(TIMEZONE_IDS, 0, TIMEZONE_IDS.length,
                Arrays.copyOfRange(barray, start, start + length), byteArrayComparator);
    }

    private int indexOf(byte[] barray, int start, int length, char c) {
        int i = 0;
        for (; i < length; i++) {
            if (barray[start + i] == c) {
                return i;
            }
        }
        return -1;
    }

    private void append(Appendable appender, byte[] bs) throws IOException {
        for (int i = 0; i < bs.length; i++) {
            appender.append((char) bs[i]);
        }
    }

    private byte toLower(byte b) {
        if (b >= 'A' && b <= 'Z') {
            return (byte) (b - TO_LOWER_OFFSET);
        }
        return b;
    }

    public long parseDateTime(byte[] data, int dataStart, int dataLength, byte[] format, int formatStart,
            int formatLength, DateTimeParseMode parseMode) throws HyracksDataException {
        int year = 0, month = 0, day = 0, hour = 0, min = 0, sec = 0, ms = 0, timezone = 0;

        boolean negativeYear = false;
        int formatCharCopies = 0;

        int dataStringPointer = 0, formatPointer = 0;

        byte separatorChar = '\0';

        DateTimeProcessState processState = DateTimeProcessState.INIT;

        int pointerMove = 0;

        while (dataStringPointer < dataLength && formatPointer < formatLength) {
            formatCharCopies = 0;
            switch (format[formatStart + formatPointer]) {
                case YEAR_CHAR:
                    processState = DateTimeProcessState.YEAR;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, YEAR_CHAR,
                            MAX_YEAR_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MONTH_CHAR:
                    processState = DateTimeProcessState.MONTH;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MONTH_CHAR,
                            MAX_MONTH_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case DAY_CHAR:
                    processState = DateTimeProcessState.DAY;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, DAY_CHAR,
                            MAX_DAY_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case HOUR_CHAR:
                    processState = DateTimeProcessState.HOUR;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, HOUR_CHAR,
                            MAX_HOUR_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MINUTE_CHAR:
                    processState = DateTimeProcessState.MINUTE;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MINUTE_CHAR,
                            MAX_MINUTE_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case SECOND_CHAR:
                    processState = DateTimeProcessState.SECOND;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, SECOND_CHAR,
                            MAX_SECOND_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MILLISECOND_CHAR:
                    processState = DateTimeProcessState.MILLISECOND;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MILLISECOND_CHAR,
                            MAX_MILLISECOND_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;

                case AMPM_CHAR:
                    processState = DateTimeProcessState.AMPM;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, AMPM_CHAR,
                            MAX_AMPM_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;

                case TIMEZONE_CHAR:
                    processState = DateTimeProcessState.TIMEZONE;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, TIMEZONE_CHAR,
                            MAX_TIMEZONE_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case ' ':
                case HYPHEN_CHAR:
                case COLON_CHAR:
                case SOLIDUS_CHAR:
                case PERIOD_CHAR:
                case COMMA_CHAR:
                case T_CHAR:
                    // separator
                    separatorChar = format[formatStart + formatPointer];
                    processState = DateTimeProcessState.SEPARATOR;
                    formatPointer++;
                    formatCharCopies++;
                    while (formatPointer < formatLength && format[formatStart + formatPointer] == separatorChar) {
                        formatPointer++;
                        formatCharCopies++;
                    }
                    break;

                default:
                    throw new HyracksDataException("Unexpected date format string at " + (formatStart + formatPointer)
                            + ": " + format[formatStart + formatPointer]);
            }

            // check whether the process state is valid for the parse mode

            switch (processState) {
                case YEAR:
                case MONTH:
                case DAY:
                    if (parseMode == DateTimeParseMode.TIME_ONLY) {
                        throw new HyracksDataException("Unexpected date format string when parsing a time value");
                    }
                    break;
                case HOUR:
                case MINUTE:
                case SECOND:
                case MILLISECOND:
                case AMPM:
                case TIMEZONE:
                    if (parseMode == DateTimeParseMode.DATE_ONLY) {
                        throw new HyracksDataException("Unexpected time format string when parsing a date value");
                    }
                    break;
                default:
                    // do nothing
            }

            switch (processState) {
                case INIT:
                    break;
                case YEAR:
                    if (dataStringPointer < dataLength && data[dataStart + dataStringPointer] == HYPHEN_CHAR) {
                        negativeYear = true;
                        dataStringPointer++;
                    }
                case DAY:
                    int maxAllowedFormatCharCopies = (processState == DateTimeProcessState.YEAR) ? 4 : 2;
                    int parsedValue = 0;

                    int processedFieldsCount = 0;
                    for (int i = 0; i < formatCharCopies; i++) {
                        if (data[dataStart + dataStringPointer] < '0' || data[dataStart + dataStringPointer] > '9') {
                            throw new HyracksDataException("Unexpected char for year field at "
                                    + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                        }
                        parsedValue = parsedValue * 10 + (data[dataStart + dataStringPointer] - '0');
                        dataStringPointer++;
                        processedFieldsCount++;
                    }
                    // for more digits
                    while (processedFieldsCount < maxAllowedFormatCharCopies && dataStringPointer < dataLength
                            && data[dataStart + dataStringPointer] >= '0' && data[dataStart + dataStringPointer] <= '9') {
                        parsedValue = parsedValue * 10 + (data[dataStart + dataStringPointer] - '0');
                        dataStringPointer++;
                        processedFieldsCount++;
                    }
                    if (processState == DateTimeProcessState.YEAR) {
                        year = parsedValue;
                        if (negativeYear) {
                            year *= -1;
                        }
                    } else {
                        day = parsedValue;
                    }
                    break;
                case MONTH:
                    if (formatCharCopies == 3) {
                        // the month is in the text format
                        int monthNameMatch = monthIDSearch(data, dataStart + dataStringPointer, 3);
                        if (monthNameMatch >= 0) {
                            month = monthNameMatch + 1;
                            dataStringPointer += 3;
                        } else {
                            throw new HyracksDataException("Unrecognizable month string "
                                    + data[dataStart + dataStringPointer] + " "
                                    + data[dataStart + dataStringPointer + 1] + " "
                                    + data[dataStart + dataStringPointer + 2]);
                        }
                    } else {
                        int processedMonthFieldsCount = 0;
                        for (int i = 0; i < formatCharCopies; i++) {
                            if (data[dataStart + dataStringPointer] < '0' || data[dataStart + dataStringPointer] > '9') {
                                throw new HyracksDataException("Unexpected char for month field at "
                                        + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                            }
                            month = month * 10 + (data[dataStart + dataStringPointer] - '0');
                            dataStringPointer++;
                            if (processedMonthFieldsCount++ > 2) {
                                throw new HyracksDataException("Unexpected char for month field at "
                                        + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                            }
                        }
                        // if there are more than 2 digits for the day string
                        while (processedMonthFieldsCount < 2 && dataStringPointer < dataLength
                                && data[dataStart + dataStringPointer] >= '0'
                                && data[dataStart + dataStringPointer] <= '9') {
                            month = month * 10 + (data[dataStart + dataStringPointer] - '0');
                            dataStringPointer++;
                            processedMonthFieldsCount++;
                        }
                    }
                    break;
                case HOUR:
                case MINUTE:
                case SECOND:
                case MILLISECOND:
                    int processFieldsCount = 0;
                    int expectedMaxCount = (processState == DateTimeProcessState.MILLISECOND) ? 3 : 2;
                    parsedValue = 0;
                    for (int i = 0; i < formatCharCopies; i++) {
                        if (data[dataStart + dataStringPointer] < '0' || data[dataStart + dataStringPointer] > '9') {
                            throw new HyracksDataException("Unexpected char for " + processState.name() + " field at "
                                    + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                        }
                        parsedValue = parsedValue * 10 + (data[dataStart + dataStringPointer] - '0');
                        dataStringPointer++;
                        if (processFieldsCount++ > expectedMaxCount) {
                            throw new HyracksDataException("Unexpected char for " + processState.name() + " field at "
                                    + dataStringPointer + ": " + data[dataStart + dataStringPointer]);
                        }
                    }
                    // if there are more than formatCharCopies digits for the hour string
                    while (processFieldsCount < expectedMaxCount && dataStringPointer < dataLength
                            && data[dataStart + dataStringPointer] >= '0' && data[dataStart + dataStringPointer] <= '9') {
                        parsedValue = parsedValue * 10 + (data[dataStart + dataStringPointer] - '0');
                        dataStringPointer++;
                        processFieldsCount++;
                    }
                    if (processState == DateTimeProcessState.HOUR) {
                        hour = parsedValue;
                    } else if (processState == DateTimeProcessState.MINUTE) {
                        min = parsedValue;
                    } else if (processState == DateTimeProcessState.SECOND) {
                        sec = parsedValue;
                    } else if (processState == DateTimeProcessState.MILLISECOND) {
                        ms = parsedValue;
                        for (int i = processFieldsCount; i < 3; i++) {
                            ms *= 10;
                        }
                    }
                    break;
                case TIMEZONE:
                    if (data[dataStart + dataStringPointer] == 'Z'
                            && ((dataStringPointer + 1 >= dataLength) || (data[dataStart + dataStringPointer + 1] < 'A'
                                    && data[dataStart + dataStringPointer + 1] > 'Z'
                                    && data[dataStart + dataStringPointer + 1] < 'a' && data[dataStart
                                    + dataStringPointer + 1] > 'z'))) {
                        // UTC as Z
                        timezone = 0;
                    } else if ((data[dataStart + dataStringPointer] == '+' || data[dataStart + dataStringPointer] == '-')
                            || (dataStringPointer + 3 < dataLength && (data[dataStart + dataStringPointer + 3] == '+' || data[dataStart
                                    + dataStringPointer + 3] == '-'))) {
                        // UTC+ or GMT+ format
                        if (dataStringPointer + 3 < dataLength
                                && (byteArrayEqualToString(data, dataStart + dataStringPointer, 3, UTC_BYTEARRAY) || byteArrayEqualToString(
                                        data, dataStart + dataStringPointer, 3, GMT_BYTEARRAY))) {
                            dataStringPointer += 3;
                        }
                        // parse timezone as +zz:zz or +zzzz
                        boolean negativeTimeZone = false;
                        if (data[dataStart + dataStringPointer] == '-') {
                            negativeTimeZone = true;
                            dataStringPointer++;
                        } else if (data[dataStart + dataStringPointer] == '+') {
                            dataStringPointer++;
                        } else {
                            throw new HyracksDataException(
                                    "Incorrect timezone hour field: expecting sign + or - but got: "
                                            + data[dataStart + dataStringPointer]);
                        }
                        // timezone hours
                        for (int i = 0; i < 2; i++) {
                            if (data[dataStart + dataStringPointer + i] >= '0'
                                    && data[dataStart + dataStringPointer + i] <= '9') {
                                timezone += (data[dataStart + dataStringPointer + i] - '0') * MS_PER_HOUR;
                            } else {
                                throw new HyracksDataException("Unexpected character for timezone hour field at "
                                        + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                            }
                        }
                        dataStringPointer += 2;
                        // skip the ":" separator
                        if (data[dataStart + dataStringPointer] == ':') {
                            dataStringPointer++;
                        }
                        // timezone minutes
                        for (int i = 0; i < 2; i++) {
                            if (data[dataStart + dataStringPointer + i] >= '0'
                                    && data[dataStart + dataStringPointer + i] <= '9') {
                                timezone += (data[dataStart + dataStringPointer + i] - '0') * MS_PER_MINUTE;
                            } else {
                                throw new HyracksDataException("Unexpected character for timezone minute field at "
                                        + (dataStart + dataStringPointer) + ": " + data[dataStart + dataStringPointer]);
                            }
                        }
                        dataStringPointer += 2;
                        if (!negativeTimeZone) {
                            timezone *= -1;
                        }
                    } else {
                        // do lookup from the zoneinfor database
                        int timezoneEndField = dataStringPointer;
                        while (timezoneEndField < dataLength
                                && ((data[dataStart + timezoneEndField] >= '0' && data[dataStart + timezoneEndField] <= '9')
                                        || (data[dataStart + timezoneEndField] >= 'a' && data[dataStart
                                                + timezoneEndField] <= 'z')
                                        || (data[dataStart + timezoneEndField] >= 'A' && data[dataStart
                                                + timezoneEndField] <= 'Z')
                                        || data[dataStart + timezoneEndField] == '/' || data[dataStart
                                        + timezoneEndField] == '_')) {
                            timezoneEndField++;
                        }
                        int searchIdx = binaryTimezoneIDSearch(data, dataStart + dataStringPointer, timezoneEndField
                                - dataStringPointer);
                        if (searchIdx >= 0) {
                            timezone = TIMEZONE_OFFSETS[searchIdx];
                        } else {
                            throw new HyracksDataException("Unexpected timezone string: "
                                    + new String(Arrays.copyOfRange(data, dataStart + dataStringPointer, dataStart
                                            + dataStringPointer)));
                        }
                        dataStringPointer = timezoneEndField;
                    }
                    break;
                case AMPM:
                    if (dataStringPointer + 1 < dataLength) {
                        if (hour > 12 || hour <= 0) {
                            throw new IllegalStateException("Hour " + hour + " cannot be a time for AM.");
                        }
                        if (byteArrayEqualToString(data, dataStart + dataStringPointer, 2, AM_BYTEARRAY)) {
                            // do nothing
                        } else if (byteArrayEqualToString(data, dataStart + dataStringPointer, 2, PM_BYTEARRAY)) {
                            hour += 12;
                            if (hour == 24) {
                                hour = 0;
                            }
                        } else {
                            throw new HyracksDataException("Unexpected string for AM/PM marker "
                                    + new String(Arrays.copyOfRange(data, dataStart + dataStringPointer, dataStart
                                            + dataStringPointer + 2)));
                        }
                        dataStringPointer += 2;
                    } else {
                        throw new HyracksDataException("Cannot find valid AM/PM marker.");
                    }
                    break;
                case SEPARATOR:
                    if (separatorChar == '\0') {
                        throw new HyracksDataException("Incorrect separator char in date string as "
                                + data[dataStart + dataStringPointer]);
                    }
                    for (int i = 0; i < formatCharCopies; i++) {
                        if (data[dataStart + dataStringPointer] != separatorChar) {
                            throw new HyracksDataException("Expecting separator " + separatorChar + " but got "
                                    + data[dataStart + dataStringPointer]);
                        }
                        dataStringPointer++;
                    }
                    break;
                default:
                    throw new HyracksDataException("Unexpected time format information when parsing a date value");
            }
        }

        if (parseMode == DateTimeParseMode.TIME_ONLY) {
            return CAL.getChronon(hour, min, sec, ms, timezone);
        }
        return CAL.getChronon(year, month, day, hour, min, sec, ms, timezone);
    }

    public void printDateTime(long chronon, int timezone, byte[] format, int formatStart, int formatLength,
            Appendable appender, DateTimeParseMode parseMode) throws HyracksDataException {
        int year = CAL.getYear(chronon);
        int month = CAL.getMonthOfYear(chronon, year);
        int day = CAL.getDayOfMonthYear(chronon, year, month);
        int hour = CAL.getHourOfDay(chronon);
        int min = CAL.getMinOfHour(chronon);
        int sec = CAL.getSecOfMin(chronon);
        int ms = CAL.getMillisOfSec(chronon);

        int formatCharCopies = 0;

        int formatPointer = 0;

        byte separatorChar = '\0';

        DateTimeProcessState processState = DateTimeProcessState.INIT;

        int pointerMove = 0;

        boolean usePM = false;
        if (indexOf(format, formatStart, formatLength, 'a') >= 0) {
            if (hour >= 12) {
                usePM = true;
                hour -= 12;
            }
            if (hour == 0) {
                hour = 12;
            }
        }

        while (formatPointer < formatLength) {

            formatCharCopies = 0;

            switch (format[formatStart + formatPointer]) {
                case YEAR_CHAR:
                    processState = DateTimeProcessState.YEAR;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, YEAR_CHAR,
                            MAX_YEAR_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MONTH_CHAR:
                    processState = DateTimeProcessState.MONTH;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MONTH_CHAR,
                            MAX_MONTH_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case DAY_CHAR:
                    processState = DateTimeProcessState.DAY;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, DAY_CHAR,
                            MAX_DAY_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case HOUR_CHAR:
                    processState = DateTimeProcessState.HOUR;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, HOUR_CHAR,
                            MAX_HOUR_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MINUTE_CHAR:
                    processState = DateTimeProcessState.MINUTE;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MINUTE_CHAR,
                            MAX_MINUTE_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case SECOND_CHAR:
                    processState = DateTimeProcessState.SECOND;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, SECOND_CHAR,
                            MAX_SECOND_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case MILLISECOND_CHAR:
                    processState = DateTimeProcessState.MILLISECOND;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, MILLISECOND_CHAR,
                            MAX_MILLISECOND_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;

                case AMPM_CHAR:
                    processState = DateTimeProcessState.AMPM;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, AMPM_CHAR,
                            MAX_AMPM_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;

                case TIMEZONE_CHAR:
                    processState = DateTimeProcessState.TIMEZONE;
                    pointerMove = parseFormatField(format, formatStart, formatLength, formatPointer, TIMEZONE_CHAR,
                            MAX_TIMEZONE_CHARS);
                    formatPointer += pointerMove;
                    formatCharCopies += pointerMove;
                    break;
                case ' ':
                case HYPHEN_CHAR:
                case COLON_CHAR:
                case SOLIDUS_CHAR:
                case PERIOD_CHAR:
                case COMMA_CHAR:
                case T_CHAR:
                    // separator
                    separatorChar = format[formatPointer];
                    processState = DateTimeProcessState.SEPARATOR;
                    formatPointer++;
                    formatCharCopies++;
                    while (formatPointer < formatLength && format[formatPointer] == separatorChar) {
                        formatPointer++;
                        formatCharCopies++;
                    }
                    break;

                default:
                    throw new HyracksDataException("Unexpected format string at " + (formatStart + formatPointer)
                            + ": " + format[formatStart + formatPointer]);
            }

            // check whether the process state is valid for the parse mode

            switch (processState) {
                case YEAR:
                case MONTH:
                case DAY:
                    if (parseMode == DateTimeParseMode.TIME_ONLY) {
                        throw new HyracksDataException("Unexpected date format string when parsing a time value");
                    }
                    break;
                case HOUR:
                case MINUTE:
                case SECOND:
                case MILLISECOND:
                case AMPM:
                case TIMEZONE:
                    if (parseMode == DateTimeParseMode.DATE_ONLY) {
                        throw new HyracksDataException("Unexpected time format string when parsing a date value");
                    }
                    break;
                default:
                    // do nothing
            }

            try {
                switch (processState) {
                    case INIT:
                        break;
                    case YEAR:
                        if (year < 0) {
                            appender.append('-');
                            year *= -1;
                        }
                    case MONTH:
                        if (processState == DateTimeProcessState.MONTH && formatCharCopies == 3) {
                            append(appender, MONTH_NAMES[month - 1]);
                            break;
                        }
                    case DAY:
                        int val = 0;
                        if (processState == DateTimeProcessState.YEAR) {
                            val = year;
                        } else if (processState == DateTimeProcessState.MONTH) {
                            val = month;
                        } else {
                            val = day;
                        }
                        int valFieldCount = (int) Math.ceil(Math.log10(val));
                        if (val == 1 || val == 0) {
                            valFieldCount = 1;
                        }
                        for (int i = 0; i < formatCharCopies - valFieldCount; i++) {
                            appender.append('0');
                        }
                        appender.append(String.valueOf(val));
                        break;
                    case HOUR:
                    case MINUTE:
                    case SECOND:
                        val = 0;
                        if (processState == DateTimeProcessState.HOUR) {
                            val = hour;
                        } else if (processState == DateTimeProcessState.MINUTE) {
                            val = min;
                        } else if (processState == DateTimeProcessState.SECOND) {
                            val = sec;
                        }

                        if (val < 10) {
                            for (int i = 0; i < formatCharCopies - 1; i++) {
                                appender.append('0');
                            }
                        }
                        appender.append(String.valueOf(val));
                        break;
                    case MILLISECOND:
                        int msFieldCount = (int) Math.ceil(Math.log10(ms));
                        for (int i = 0; i < 3 - msFieldCount; i++) {
                            appender.append('0');
                        }
                        if (formatCharCopies < 3) {

                            if (formatCharCopies == 1) {
                                if (ms % 100 == 0) {
                                    // the tailing two zeros can be removed
                                    ms = ms / 100;
                                } else if (ms % 10 == 0) {
                                    // the tailing one zero can be removed
                                    ms = ms / 10;
                                }
                            } else {
                                if (ms % 10 == 0) {
                                    // the tailing one zero can be removed
                                    ms = ms / 10;
                                }
                            }

                        }
                        appender.append(String.valueOf(ms));
                        break;
                    case TIMEZONE:
                        if (timezone == 0) {
                            appender.append('Z');
                            break;
                        }
                        if (timezone < 0) {
                            appender.append('-');
                            timezone *= -1;
                        }
                        int timezoneField = timezone / MS_PER_HOUR;
                        if (timezoneField < 10) {
                            appender.append('0');
                        }
                        appender.append(String.valueOf(timezoneField));
                        timezoneField = timezone % MS_PER_HOUR / MS_PER_MINUTE;
                        if (timezoneField < 10) {
                            appender.append('0');
                        }
                        appender.append(String.valueOf(timezoneField));
                        break;
                    case AMPM:
                        if (usePM) {
                            appender.append("PM");
                        } else {
                            appender.append("AM");
                        }
                        break;
                    case SEPARATOR:
                        if (separatorChar == '\0') {
                            throw new HyracksDataException(
                                    "Incorrect separator: separator char is not initialized properly!");
                        }
                        for (int i = 0; i < formatCharCopies; i++) {
                            appender.append(String.valueOf(separatorChar));
                        }
                        break;
                    default:
                        throw new HyracksDataException("Unexpected time state when printing a date value");
                }
            } catch (IOException ex) {
                throw new HyracksDataException(ex);
            }
        }
    }
}
