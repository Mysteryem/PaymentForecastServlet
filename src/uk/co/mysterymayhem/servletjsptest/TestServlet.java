package uk.co.mysterymayhem.servletjsptest;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Created by Mysteryem on 25/04/2017.
 */
public class TestServlet extends HttpServlet {
    //Visible for testing
    String filePath;
    String message;

    public TestServlet() {
        super();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.filePath = config.getServletContext().getRealPath("payment-forecast-data.csv");
        super.init(config);
    }

    private static void prependHtml(StringBuilder builder) {
        builder.append(
                "<html>" +
                "<head>" +
                "<style>" +
                "table {" +
                    "font-family: arial, sans-serif;" +
                    "border-collapse: collapse;" +
                    "width: 100%;" +
                "}" +
                "td, th {" +
                    "border: 1px solid #dddddd;" +
                    "text-align: left;" +
                    "padding: 8px;" +
                "}" +
                "tr:nth-child(even) {" +
                    "background-color: #dddddd;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>");
    }

    private static void appendHtml(StringBuilder builder) {
        builder.append(
                "</body>" +
                "</html>");
    }

    @Override
    public void init() throws ServletException {
        // TODO: Replace with a 'normal' method instead of this 'main' method
        DataParser.main(new String[]{this.filePath});
        // FIXME: Need to know all dates and all merchants upfront so that a table can be properly constructed
        HashMap<SimpleDate, HashMap<Integer, BigDecimal>> dayToMerchantIdToAmountMapMap = DataParser.DAY_TO_MERCHANT_ID_TO_AMOUNT_MAP_MAP;
        TreeMap<SimpleDate, HashMap<Integer, BigDecimal>> sortedMap = new TreeMap<>();
        // Determines number of columns of the table, this could change for different periods of time that are to be displayed
        // for now, we're simply displaying all of the available data
        TreeSet<Integer> knownSortedMerchantIds = new TreeSet<>();
        for (Map.Entry<SimpleDate, HashMap<Integer, BigDecimal>> entry : dayToMerchantIdToAmountMapMap.entrySet()) {
            HashMap<Integer, BigDecimal> innerMap = entry.getValue();
            sortedMap.put(entry.getKey(), innerMap);
            knownSortedMerchantIds.addAll(innerMap.keySet());
        }
        Integer[] sortedMerchantIds = knownSortedMerchantIds.toArray(new Integer[knownSortedMerchantIds.size()]);
        Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ENGLISH);

        HtmlTableBuilder builder = new HtmlTableBuilder();

        builder.addHeader("Date");
        knownSortedMerchantIds.forEach(i -> builder.addHeader(DataParser.MERCHANT_ID_TO_DATA.get(i).name));

        //for (Map.Entry<SimpleDate, >)
        sortedMap.entrySet().forEach(entry -> {
            SimpleDate key = entry.getKey();
            HashMap<Integer, BigDecimal> todaysPayements = entry.getValue();
            int rowIndex = builder.addRow();
            builder.addToRow(rowIndex, key.prettyToString());

            for (Integer id : sortedMerchantIds) {
                BigDecimal bigDecimal = todaysPayements.get(id);
                if (bigDecimal == null) {
                    bigDecimal = new BigDecimal("0.00");
                }
                builder.addToRow(rowIndex, "£" + bigDecimal.toPlainString());
            }
        });
        StringBuilder stringBuilder = new StringBuilder();
        prependHtml(stringBuilder);
        stringBuilder.append(builder.toString());
        appendHtml(stringBuilder);
        this.message = stringBuilder.toString();
        //super.init();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //super.doGet(req, resp);
        // Set response content type
        resp.setContentType("text/html");

        // Actual logic goes here.
        PrintWriter out = resp.getWriter();
        //out.println("<h1>" + this.message + ", " + this.counter + ", " + this.counter2 + "</h1>");
        out.print(this.message);
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
