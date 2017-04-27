package uk.co.mysterymayhem.paymentforecast;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class DataParser {

    // Replace with TIntObjectHashMap from Trove collections to maybe increase performance? (int keys instead of Integer keys)
    // Used in validation of merchant related fields
    static final HashMap<Integer, MerchantData> MERCHANT_ID_TO_DATA = new HashMap<>();
    // Main results map, Date -> Map<MerchantID, Amount in £>
    static final HashMap<SimpleDate, HashMap<Integer, BigDecimal>> DAY_TO_MERCHANT_ID_TO_AMOUNT_MAP_MAP = new HashMap<>();
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

    public static void parseDataFile(String fileURI) {
        parseDataFile(fileURI, System.err);
    }

    public static void parseDataFile(String fileURI, PrintStream parsingErrorOutput) {
        try (LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(new File(fileURI)))) {
            // First line contains descriptive headers, so is skipped
            lineNumberReader.readLine();

            // Easily parallelisable via lineNumberReader.lines().parallel().forEach(...) once other code is made thread-safe
            lineNumberReader.lines().forEach(s -> {
                try {
                    parseLine(s);
                } catch (ParseException parseException) {
                    //TODO: log some info to a file here instead of printing to console
                    parsingErrorOutput.println("Failed to parse line " + lineNumberReader.getLineNumber() + ": " + parseException.getMessage());
                }
            });
        } catch (FileNotFoundException e) {
            System.err.println("Current path: " + Paths.get("").toAbsolutePath().toString());
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseLine(String line) throws ParseException {
        String[] split = line.split(",");
        if (split.length != EXPECTED_NUM_FIELDS) {
            throw new ParseException("Invalid record length, got " + split.length + ", expected " + EXPECTED_NUM_FIELDS + ". Full line:\n" + line);
        }

        // Parsing/validation order is mostly arbitrary, more expensive operations you would want last, but more likely
        // to not parse you would want earlier.
        // Either way, most entries will parse fine, so it's not very important

        // Both types of date are parsable, received date is before due date and due utc and due epoch dates match
        SimpleDate paymentDate = parseTimeData(split[RECIEVED_UTC], split[DUE_UTC], split[DUE_EPOCH]);

        // Each merchant id is a number and has a single corresponding merchant name and a single merchant public key
        MerchantData merchantData = parseMerchantData(split[MERCHANT_ID], split[MERCHANT_NAME], split[MERCHANT_PUB_KEY]);

        // Each payer id is a number and has a single corresponding payer public key
        parsePayerData(split[PAYER_ID], split[PAYER_PUB_KEY]);

        // Debit permission ID is a parsable number
        parseDebitPermissionID(split[DEBIT_PERMISSION_ID]);

        BigDecimal paymentAmount = parsePaymentAmount(split[CURRENCY], split[AMOUNT]);

        // Validates (and parses) the SHA256 hash. This is done last as missing data would cause a hash mismatch, but the
        // other checks would provide a more useful output
        validateHash(split);

        HashMap<Integer, BigDecimal> idToAmount = DAY_TO_MERCHANT_ID_TO_AMOUNT_MAP_MAP.get(paymentDate);
        if (idToAmount == null) {
            idToAmount = new HashMap<>();
            DAY_TO_MERCHANT_ID_TO_AMOUNT_MAP_MAP.put(paymentDate, idToAmount);
        }

        Integer merchantID = merchantData.id;
        BigDecimal amount = idToAmount.get(merchantID);
        if (amount == null) {
            idToAmount.put(merchantID, paymentAmount);
        } else {
            idToAmount.put(merchantID, amount.add(paymentAmount));
        }
    }

    private static BigDecimal parsePaymentAmount(String currencyType, String amount) throws ParseException {
        if (!currencyType.equals("GBP")) {
            throw new ParseException("Unrecognised currency type \"" + currencyType + "\"");
        }
        int pointIndex = amount.indexOf('.');
        // To extend functionality, a Map<String, Function<String, BigDecimal> could be used, where there is a function for
        // converting amount to BigDecimal for each recognised currency type
        try {
            // If a decimal point is found
            if (pointIndex != -1) {
                // Need 2 digits after the decimal point to be valid
                if (pointIndex != amount.length() - 3) {
                    throw new ParseException(String.format("Invalid amount (%s) for currency type %s", amount, currencyType));
                }
            }
            BigDecimal parsedAmount = new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP);
            if (parsedAmount.compareTo(BigDecimal.ZERO) < 1) {
                throw new ParseException(String.format("Invalid amount (%s). Must be greater than zero", amount));
            }
            return parsedAmount;
        } catch (NumberFormatException e) {
            throw new ParseException(e);
        }
    }


    /**
     * Uniqueness is not ensured. Only check is that the permissionID is a valid number
     *
     * @param permissionIDString
     * @throws ParseException
     */
    private static void parseDebitPermissionID(String permissionIDString) throws ParseException {
        try {
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt(permissionIDString);
        } catch (NumberFormatException e) {
            throw new ParseException(e);
        }
    }

    /**
     * Payer data is currently unused aside from checking data consistency, so currently, nothing is returned
     *
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
            } else if (!retrievedPubKey.equals(payerPubKey)) {
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
