package uk.co.mysterymayhem.paymentforecast;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static uk.co.mysterymayhem.paymentforecast.DataParser.printTimeStampedLine;

/**
 * Created by Mysteryem on 25/04/2017.
 */
public class PaymentForecastServlet extends HttpServlet {

    // Visible for testing
    String filePath;
    // Visible for testing
    String parseErrorPath;
    // Visible for testing
    String runtimeLogPath;
    // Visible for testing
    String message;

    private ServletConfig config;

    // @formatter:off
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
                                "border: 1px solid #cccccc;" +
                                "text-align: left;" +
                                "padding: 8px;" +
                            "}" +
                            "tr:nth-child(even) {" +
                                "background-color: #cccccc;" +
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
    // @formatter:on

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        this.filePath = config.getServletContext().getRealPath("payment-forecast-data.csv");
        super.init(config);
    }

    @Override
    public void init() throws ServletException {
        PrintStream printStream;
        PrintStream runtimeLogStream;
        try {
            String filePathString;
            if (this.parseErrorPath == null) {
                filePathString = config.getServletContext().getRealPath("payment-forecast-parsing-errors.txt");
            } else {
                filePathString = this.parseErrorPath;
            }
            Path path = Paths.get(filePathString);
            // StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE and StandardOpenOption.WRITE are the defaults
            OutputStream outputStream = Files.newOutputStream(path);
            printStream = new PrintStream(outputStream);

            runtimeLogStream = runtimeLogPath == null ? System.out : new PrintStream(Files.newOutputStream(Paths.get(runtimeLogPath)));
        } catch (IOException e) {
            e.printStackTrace();
            this.message = "Internal server error";
            return;
        }
        printTimeStampedLine(runtimeLogStream, "Starting reading/parsing/processing of data file");
        DataParser.parseDataFile(this.filePath, printStream, runtimeLogStream);

        HashMap<SimpleDate, HashMap<Integer, BigDecimal>> dayToMerchantIdToAmountMapMap = DataParser.DAY_TO_MERCHANT_ID_TO_AMOUNT_MAP_MAP;
        TreeMap<SimpleDate, HashMap<Integer, BigDecimal>> sortedMap = new TreeMap<>();

        printTimeStampedLine(runtimeLogStream, "Determining table columns");
        // Determines number of columns of the table, this could change for different periods of time that are to be displayed
        // for now, we're simply displaying all of the available data
        // TreeSet as uniqueness and sorting is required, using a HashSet and then converting it to a list/array and then
        // sorting would also work
        TreeSet<Integer> knownSortedMerchantIds = new TreeSet<>();
        for (Map.Entry<SimpleDate, HashMap<Integer, BigDecimal>> entry : dayToMerchantIdToAmountMapMap.entrySet()) {
            HashMap<Integer, BigDecimal> innerMap = entry.getValue();
            sortedMap.put(entry.getKey(), innerMap);
            knownSortedMerchantIds.addAll(innerMap.keySet());
        }

        HtmlTableBuilder builder = new HtmlTableBuilder();

        printTimeStampedLine(runtimeLogStream, "Building html table");
        builder.addHeader("Date");
        knownSortedMerchantIds.forEach(i -> builder.addHeader(DataParser.MERCHANT_ID_TO_DATA.get(i).name));

        sortedMap.entrySet().forEach(entry -> {
            SimpleDate key = entry.getKey();
            HashMap<Integer, BigDecimal> todaysPayements = entry.getValue();
            int rowIndex = builder.addRow();
            builder.addToRow(rowIndex, key.prettyToString());

            for (Integer id : knownSortedMerchantIds) {
                BigDecimal bigDecimal = todaysPayements.get(id);
                if (bigDecimal == null) {
                    bigDecimal = new BigDecimal("0.00");
                }
                builder.addToRow(rowIndex, "&pound;" + bigDecimal.toPlainString());
            }
        });
        StringBuilder stringBuilder = new StringBuilder();
        prependHtml(stringBuilder);
        stringBuilder.append(builder.toString());
        appendHtml(stringBuilder);
        this.message = stringBuilder.toString();
        printTimeStampedLine(runtimeLogStream, "Built html");
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
}
