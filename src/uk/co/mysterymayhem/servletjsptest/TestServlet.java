package uk.co.mysterymayhem.servletjsptest;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

public class TestServlet extends HttpServlet {

    // Compiler will insert these values
    private static final int RECIEVED_UTC = 0;
    private static final int MERCHANT_ID = 1;
    private static final int MERCHANT_NAME = 2;
    private static final int MERCHANT_PUB_KEY = 3;
    private static final int PAYER_ID = 4;
    private static final int PAYER_PUB_KEY = 5;
    private static final int DEBIT_PERMISSION_ID = 6;
    private static final int DUE_UTC = 7;
    private static final int DUE_EPOC = 8;
    private static final int CURRENCY = 9;
    private static final int AMOUNT = 10;
    private static final int SHA256 = 11;
    private static final int EXPECTED_NUM_FIELDS = 12;

    //TODO: Replace with TIntObjectHashMap from Trove collections
    // Used in validation of parsed lines
    private static final HashMap<Integer, String> MERCHANT_ID_TO_NAME = new HashMap<>();

    public static void main(String[] args) {
        try (LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(new File("payment-forecast-data.csv")))){
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

    private static void parseLine(String line) throws ParseException {
        String[] split = line.split(",");
        if (split.length != EXPECTED_NUM_FIELDS) {
            throw new ParseException("Invalid record length, got " + split.length + ", expected " + EXPECTED_NUM_FIELDS + ". Full line:\n" + line);
        }

        //TODO: More validation, e.g.:
        // MerchantId <=> MerchantName, MerchantPubKey
        // PayerId <=> PayerPubKey
        // Amount exists and is a valid positive number
        validateHash(split);

        Instant received = parseUTCData(split[RECIEVED_UTC]);
        Instant dueUTC = parseUTCData(split[DUE_UTC]);
        if (received.isAfter(dueUTC)) {
            throw new ParseException("Received UTC time is after due UTC time for " + line);
        }
        Instant dueEpoch = parseEpochData(split[DUE_EPOC]);
        if (!dueUTC.equals(dueEpoch)) {
            throw new ParseException("Due UTC (" + split[DUE_UTC] + ", " + dueUTC.getEpochSecond() + ") and due epoch (" + dueEpoch.getEpochSecond() + ") times don't match");
        }

        UTC_CALENDAR.setTimeInMillis(dueEpoch.toEpochMilli());

    }

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

    /**
     * Parse UTC string into a date of payment (year, month and day). Note that payments after 4pm are considered to
     * occur on the next day.
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
            UTC_CALENDAR.set(Integer.parseInt(ymd[YEAR]), Integer.parseInt(ymd[MONTH]) - 1, Integer.parseInt(ymd[DAY]), Integer.parseInt(hms[HOURS]), Integer.parseInt(hms[MINUTES]), Integer.parseInt(hms[SECONDS]));
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
        byte[] calculatedHash = Hasher.hash(splitLine[MERCHANT_PUB_KEY], splitLine[PAYER_PUB_KEY], splitLine[DEBIT_PERMISSION_ID], splitLine[DUE_EPOC], splitLine[AMOUNT]);
        if (!Arrays.equals(preCalculatedHash,
                calculatedHash)) {
            throw new ParseException("Hash mismatch, got " + Hasher.bytesToNiceString(calculatedHash) + ", expected " + splitLine[SHA256]);
        }
    }

    private String message;

    public TestServlet() {
        super();
    }

//    @Override
//    public void init(ServletConfig config) throws ServletException {
//        super.init(config);
//    }

    @Override
    public void init() throws ServletException {
        this.message = "Test message";
        //super.init();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //super.doGet(req, resp);
        // Set response content type
        resp.setContentType("text/html");

        // Actual logic goes here.
        PrintWriter out = resp.getWriter();
        out.println("<h1>" + this.message + "</h1>");
    }

    @Override
    protected long getLastModified(HttpServletRequest req) {
        return super.getLastModified(req);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doHead(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doDelete(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doOptions(req, resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doTrace(req, resp);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        super.service(req, res);
    }
}
