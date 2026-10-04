package com.campus.reporting.application;

import java.util.List;
import com.campus.shared.application.reporting.ReportRow;

/** Quote every cell and neutralize formulas even after leading whitespace/control/format characters. */
public final class CsvReportWriter {
    private CsvReportWriter() { }
    public static String write(List<String> columns, List<ReportRow> rows) {
        var csv=new StringBuilder(); append(csv,columns);
        for(var row:rows) {
            var cells=row.cells();
            if(cells.size()!=columns.size()) throw new IllegalStateException("CSV projection mismatch");
            append(csv,cells);
        }
        return csv.toString();
    }
    private static void append(StringBuilder csv,List<String> cells) {
        for(int index=0;index<cells.size();index++) {
            if(index>0) csv.append(',');
            String cell=safe(cells.get(index));
            csv.append('"').append(cell.replace("\"","\"\"")).append('"');
        }
        csv.append("\r\n");
    }
    static String safe(String value) {
        if(value==null) return "";
        int index=0;
        while(index<value.length()) {
            int ch=value.codePointAt(index);
            if(!Character.isWhitespace(ch) && !Character.isSpaceChar(ch) && !Character.isISOControl(ch) && Character.getType(ch)!=Character.FORMAT) break;
            index+=Character.charCount(ch);
        }
        boolean formula=index<value.length() && "=+-@".indexOf(value.charAt(index))>=0;
        boolean control=!value.isEmpty() && Character.isISOControl(value.charAt(0));
        return formula||control?"'"+value:value;
    }
}
