package uk.co.mysterymayhem.servletjsptest;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class DataParser {

    // Constants for csv record parsing
    private static final int RECIEVED_UTC = 0;
    private static final int MERCHANT_ID = 1;
    private static final int MERCHANT_NAME = 2;
    private static final int MERCHANT_PUB_KEY = 3;
    private static final int PAYER_ID = 4;
    private static final int PAYER_PUB_KEY = 5;
    private static final int DEBIT_PERMISSION_ID = 6;
    private static final int DUE_UTC = 7;
    private static final int DUE_EPOCH = 8;
    private static final int CURRENCY = 9;
    private static final int AMOUNT = 10;
    private static final int SHA256 = 11;
    private static final int EXPECTED_NUM_FIELDS = 12;

    // Replace with TIntObjectHashMap from Trove collections to maybe increase performance? (int keys instead of Integer keys)
    // Used in validation of merchant related fields
    private static final HashMap<Integer, MerchantData> MERCHANT_ID_TO_DATA = new HashMap<>();
    // Used in validation of payer related fields
    private static final HashMap<Integer, String> PAYER_ID_TO_PUB_KEY = new HashMap<>();
    // Not very useful for multi-threading, a blocking queue of Calendar objects might work
    // Don't care about initial time of calendar
    private static final Calendar UTC_CALENDAR = GregorianCalendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ENGLISH);

    // Constants for date conversion
    private static final int YEAR = 0;
    private static final int MONTH = 1;
    private static final int DAY = 2;
    private static final int HOURS = 0;
    private static final int MINUTES = 1;
    private static final int SECONDS = 2;
    private static final int PUBLIC_KEY_LENGTH = 20;

    public static void main(String[] args) {
        try (LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(new File("payment-forecast-data.csv")))) {
            // First line contains descriptive headers so is skipped
            lineNumberReader.readLine();

            while (lineNumberReader.ready()) {
                try {
                    parseLine(lineNumberReader.readLine());
                } catch (ParseException parseException) {
                    //TODO: log some info to a file here instead of printing to console
                    System.out.println("Failed to parse line " + lineNumberReader.getLineNumber() + ": " + parseException.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseLine(String line) throws ParseException {
        String[] split = line.split(",");
        if (split.length != EXPECTED_NUM_FIELDS) {
            throw new ParseException("Invalid record length, got " + split.length + ", expected " + EXPECTED_NUM_FIELDS + ". Full line:\n" + line);
        }

        //TODO: More validation, e.g.:
        // Amount exists and is a valid positive number
        // Currency is GBP (assume other currencies are unsupported at this time)
        // Debit permission ID is a parsable number and unique
        validateHash(split);

        SimpleDate paymentDate = parseTimeData(split[RECIEVED_UTC], split[DUE_UTC], split[DUE_EPOCH]);
        MerchantData merchantData = parseMerchantData(split[MERCHANT_ID], split[MERCHANT_NAME], split[MERCHANT_PUB_KEY]);
        parsePayerData(split[PAYER_ID], split[PAYER_PUB_KEY]);
        parseDebitPermissionID(split[DEBIT_PERMISSION_ID]);

    }


    // TODO: Replace with a Trove collection
    private static final HashSet<Integer> debitPermissionIDs = new HashSet<>();

    private static void parseDebitPermissionID(String permissionIDString) throws ParseException{
        try {
            if (!debitPermissionIDs.add(Integer.parseInt(permissionIDString))) {
                throw new ParseException(String.format("Debit permission ID \"%s\" is not unique", permissionIDString));
            }
        } catch (NumberFormatException e) {
            throw new ParseException(e);
        }
    }

    /**
     * Payer data is currently unused aside from checking data consistency, so currently, nothing is returned
     * @param payerIDString
     * @param payerPubKey
     * @throws ParseException
     */
    private static void parsePayerData(String payerIDString, String payerPubKey) throws ParseException {
        try {
            Integer payerID = Integer.parseInt(payerIDString);
            String retrievedPubKey = PAYER_ID_TO_PUB_KEY.get(payerID);
            if (retrievedPubKey == null) {
                PAYER_ID_TO_PUB_KEY.put(payerID, payerPubKey);
            }
            else if (!retrievedPubKey.equals(payerPubKey)) {
                throw new ParseException(String.format("Parsed payer public key for ID %d (%s) does not match existing payer public key (%s)",
                        payerID, payerPubKey, retrievedPubKey));
            }
        } catch (NumberFormatException e) {
            throw new ParseException(e);
        }
    }

    /**
     * Returns the year/month/day that payment will occur on as well as validates all date data
     *
     * @param receivedUTCStr
     * @param dueUTCStr
     * @param dueEpochStr
     * @return
     * @throws ParseException
     */
    private static SimpleDate parseTimeData(String receivedUTCStr, String dueUTCStr, String dueEpochStr) throws ParseException {
        Instant received = parseUTCData(receivedUTCStr);
        Instant dueUTC = parseUTCData(dueUTCStr);
        if (received.isAfter(dueUTC)) {
            throw new ParseException(String.format("Received UTC time (%s) is after due UTC time (%s)", receivedUTCStr, dueUTCStr));
        }
        Instant dueEpoch = parseEpochData(dueEpochStr);
        if (!dueUTC.equals(dueEpoch)) {
            throw new ParseException(String.format("Due UTC (%s, %s) and due epoch (%s) times don't match",
                    dueUTCStr, dueUTC.getEpochSecond(), dueEpoch.getEpochSecond()));
        }

        UTC_CALENDAR.setTimeInMillis(dueEpoch.toEpochMilli());
        // Due time after 4pm is processed the day after
        if (UTC_CALENDAR.get(Calendar.HOUR_OF_DAY) >= 16) {
            UTC_CALENDAR.add(Calendar.DAY_OF_MONTH, 1);
        }

        return new SimpleDate(UTC_CALENDAR);
    }

    // Assuming public keys are always 20 characters long
    private static MerchantData parseMerchantData(String merchantIDString, String merchantName, String merchantPubKey) throws ParseException {
        try {
            Integer merchantID = Integer.parseInt(merchantIDString);
            if (merchantPubKey.length() != PUBLIC_KEY_LENGTH) {
                throw new ParseException(String.format("Public key for merchant %s with id %s is %d characters long, expected %d",
                        merchantName, merchantIDString, merchantPubKey.length(), PUBLIC_KEY_LENGTH));
            }
            MerchantData retrievedData = MERCHANT_ID_TO_DATA.get(merchantID);
            if (retrievedData == null) {
                retrievedData = new MerchantData(merchantID, merchantName, merchantPubKey);
                MERCHANT_ID_TO_DATA.put(merchantID, retrievedData);
            }
            // It's unnecessary to create a new instance in most cases, so the field values are compared in the same way as if by call of .equals(...)
            else if (!retrievedData.matches(merchantID, merchantName, merchantPubKey)) {
                throw new ParseException(String.format("Parsed merchant data (%s) does not match existing merchant data (%s)",
                        new MerchantData(merchantID, merchantName, merchantPubKey), retrievedData));
            }
            // Return whatever's in the map as that won't be garbage collected
            return retrievedData;
        } catch (NumberFormatException e) {
            throw new ParseException(e);
        }
    }

    /**
     * Parse UTC string into a date of payment (year, month and day). Note that payments after 4pm are considered to
     * occur on the next day.
     *
     * @param utcDate
     * @return
     * @throws ParseException
     */
    private static Instant parseUTCData(String utcDate) throws ParseException {
        int firstSplit = utcDate.indexOf('T');
        if (firstSplit == -1) {
            throw new ParseException("Failed to parse UTC date (unable to find 'T') in \"" + utcDate + "\"");
        }
        String yearMonthDay = utcDate.substring(0, firstSplit);
        String hourDaySeconds = utcDate.substring(firstSplit + 1, utcDate.length() - 1);
        String[] ymd = yearMonthDay.split("-");
        String[] hms = hourDaySeconds.split(":");
        UTC_CALENDAR.clear();
        try {
            //noinspection MagicConstant
            UTC_CALENDAR.set(Integer.parseInt(ymd[YEAR]), Integer.parseInt(ymd[MONTH]) - 1, Integer.parseInt(ymd[DAY]),
                    Integer.parseInt(hms[HOURS]), Integer.parseInt(hms[MINUTES]), Integer.parseInt(hms[SECONDS]));
//            // If after 4pm, then payment occurs the next day
//            if (CALENDAR.get(Calendar.HOUR_OF_DAY) >= 16) {
//                CALENDAR.add(Calendar.DAY_OF_YEAR, 1);
//            }
        } catch (NumberFormatException e) {
            throw new ParseException("Failed to parse UTC date \"" + utcDate + "\"", e);
        }
        return UTC_CALENDAR.toInstant();
        //return new SimpleDate(CALENDAR);
    }

    private static Instant parseEpochData(String epochSeconds) throws ParseException {
        try {
            int parsedSeconds = Integer.parseInt(epochSeconds);
            return Instant.ofEpochSecond(parsedSeconds);
//            CALENDAR.clear();
//            CALENDAR.add(Calendar.SECOND, i);
//            // If after 4pm, then payment occurs the next day
//            if (CALENDAR.get(Calendar.HOUR_OF_DAY) >= 16) {
//                CALENDAR.add(Calendar.DAY_OF_YEAR, 1);
//            }
//            return CALENDAR.toInstant();
            //return new SimpleDate(CALENDAR);
        } catch (NumberFormatException e) {
            throw new ParseException("Failed to parse seconds since epoch\"" + epochSeconds + "\"", e);
        }
    }

    private static void validateHash(String[] splitLine) throws ParseException {
        byte[] preCalculatedHash = Hasher.fromPreComputedString(splitLine[SHA256]);
//        byte[] preCalculatedHash = splitLine[SHA256].getBytes();
        byte[] calculatedHash = Hasher.hash(splitLine[MERCHANT_PUB_KEY], splitLine[PAYER_PUB_KEY], splitLine[DEBIT_PERMISSION_ID], splitLine[DUE_EPOCH], splitLine[AMOUNT]);
        if (!Arrays.equals(preCalculatedHash,
                calculatedHash)) {
            throw new ParseException("Hash mismatch, got " + Hasher.bytesToNiceString(calculatedHash) + ", expected " + splitLine[SHA256]);
        }
    }

    static class ParseException extends Exception {
        public ParseException() {
            super();
        }

        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public ParseException(Throwable cause) {
            super(cause);
        }
    }
}
