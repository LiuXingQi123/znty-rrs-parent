import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
public class GenTpl {
    static void write(String path, String[] headers, String[][] rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet s = wb.createSheet("import");
        Row h = s.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            h.createCell(i).setCellValue(headers[i]);
            s.setColumnWidth(i, 18 * 256);
        }
        for (int r = 0; r < rows.length; r++) {
            Row row = s.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                row.createCell(c).setCellValue(rows[r][c]);
            }
        }
        try (FileOutputStream out = new FileOutputStream(path)) { wb.write(out); }
        wb.close();
    }
    public static void main(String[] a) throws Exception {
        write("src/main/resources/xlsx/security_pool_import.xlsx",
            new String[]{"父池名称","子池名称","证券名称","证券代码"},
            new String[][]{
                {"信用债大库(new)","一级库","24交投MTN001","101901234.IB"},
                {"信用债大库(new)","二级库","24能E1","103003456.SH"}
            });
        write("src/main/resources/xlsx/company_pool_import.xlsx",
            new String[]{"父池名称","子池名称","主体名称","主体代码"},
            new String[][]{
                {"","债券禁止库","交投集团","C10001"},
                {"","观察池","城投公司","C10002"}
            });
        System.out.println("ok");
    }
}
