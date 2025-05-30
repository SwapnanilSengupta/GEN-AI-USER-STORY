package gemini.workshop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TextDataAppender {

    public static void appendToFile(String filePath, String question, String response) throws IOException {
        // Ensure parent directories exist
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        // Append Q&A pair to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) { // The 'true' argument enables append mode
            writer.write("User Story: " + question);
            writer.newLine();
            writer.write("Test Cases: " + response);
            writer.newLine();
            writer.write("----------------------------------------------------");
            writer.newLine();
        }
    }
}
