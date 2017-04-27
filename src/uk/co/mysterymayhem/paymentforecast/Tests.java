package uk.co.mysterymayhem.paymentforecast;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Mysteryem on 24/04/2017.
 */
// TODO: Convert to a testing framework e.g. JUnit
public class Tests {
    public static void main(String[] args) throws Exception {
        hashTest();

        forecastTest();
    }

    private static void hashTest() throws Exception {
        byte[] hash = Hasher.hash("merchantPubKey", "payerPubKey", "debitPermissionId", "dueEpoc", "amount");
        String hashString = Hasher.bytesToNiceString(hash);
        if (!Arrays.equals(Hasher.fromPreComputedString(hashString), hash)) {
            throw new RuntimeException("Test failed");
        }
    }

    private static void forecastTest() throws Exception {
        PaymentForecastServlet paymentForecastServlet = new PaymentForecastServlet();
        paymentForecastServlet.filePath = "payment-forecast-data.csv";
        paymentForecastServlet.parseErrorPath = "parsing-errors.txt";
        paymentForecastServlet.runtimeLogPath = "runtime-log.log";
        paymentForecastServlet.init();
        ArrayList<String> iterableWrapper = new ArrayList<>();
        iterableWrapper.add(paymentForecastServlet.message);
        Files.write(Paths.get("test_html_output.html"), iterableWrapper);
    }
}
