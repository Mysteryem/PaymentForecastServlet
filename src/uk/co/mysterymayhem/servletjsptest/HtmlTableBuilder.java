package uk.co.mysterymayhem.servletjsptest;

import java.util.ArrayList;

/**
 * Created by Mysteryem on 26/04/2017.
 */
public class HtmlTableBuilder {
    private final ArrayList<String> headers = new ArrayList<>();
    private final ArrayList<ArrayList<String>> tableContents = new ArrayList<>();

    public void addHeader(String header) {
        headers.add(header);
    }

    /**
     * Returns index of new row
     * @return
     */
    public int addRow() {
        ArrayList<String> row = new ArrayList<>();
        this.tableContents.add(row);
        return this.tableContents.size() - 1;
    }

    public void addToRow(int rowIndex, String cellContents) {
        tableContents.get(rowIndex).add(cellContents);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<table>");
        if (headers.size() > 0) {
            builder.append("<tr>");
            headers.forEach(s -> builder.append("<th>").append(s).append("</th>"));
            builder.append("</tr>");
        }
        tableContents.forEach(list -> {
            builder.append("<tr>");
            list.forEach(s -> builder.append("<td>").append(s).append("</td>"));
            builder.append("</tr>");
        });
        builder.append("</table>");
        return builder.toString();
    }
}
