package gemini.workshop;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExcelDataAppender {

    public static void appendToFile(String filePath, String question, String response) throws IOException {
        Workbook workbook;
        File file = new File(filePath);

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = new XSSFWorkbook(fis);
            }
        } else {
            // Ensure parent directories exist for a new file
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }
            workbook = new XSSFWorkbook();
        }

        Sheet sheet = workbook.getSheet("QandA");
        if (sheet == null) {
            sheet = workbook.createSheet("QandA");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("UserStory");
            headerRow.createCell(1).setCellValue("TestCase");
        }

        int nextRowNum = sheet.getLastRowNum() + 1;
        if (sheet.getPhysicalNumberOfRows() == 1) { // Only header exists
            nextRowNum = 1;
        }

        // Create wrap style
        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

        Row dataRow = sheet.createRow(nextRowNum);
        Cell questionCell = dataRow.createCell(0);
        questionCell.setCellValue(question);
        questionCell.setCellStyle(wrapStyle);

        Cell answerCell = dataRow.createCell(1);
        answerCell.setCellValue(response);
        answerCell.setCellStyle(wrapStyle);

        // Auto-size columns
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);

        // Estimate and set row height based on content
        int estimatedLines = Math.max(question.length(), response.length()) / 50 + 1;
        dataRow.setHeight((short) (estimatedLines * sheet.getDefaultRowHeight()));

        // Write to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }

        workbook.close();
    }
}

