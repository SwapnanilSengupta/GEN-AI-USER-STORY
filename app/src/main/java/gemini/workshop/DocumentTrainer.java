package gemini.workshop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.io.IOException; // Import IOException

public class DocumentTrainer {

    private PgVectorEmbeddingStore embeddingStore;
    private VertexAiEmbeddingModel embeddingModel;

    private static Properties properties; // Declare properties here

    static {
        properties = new Properties();
        try (InputStream input = DocumentTrainer.class.getClassLoader()
                .getResourceAsStream("configuration.properties")) {
            if (input == null) {
                String errorMessage = "Unable to load configuration.properties";
                System.err.println(errorMessage);
                throw new RuntimeException(errorMessage);
            }
            properties.load(input);
        } catch (IOException ex) {
            System.err.println("Error loading configuration.properties: " + ex.getMessage());
            throw new RuntimeException("Error loading configuration.properties", ex);
        }
    }

    private static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public DocumentTrainer() {
        // ======== Create Embedding Model ========
        this.embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint("us-central1" + "-aiplatform.googleapis.com:443")
                .project("ai-testautomation-production")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .maxRetries(3)
                .build();

        // ======== Create DataSource with HikariCP ========
        HikariConfig config = new HikariConfig();
        String jdbcUrl = getProperty("jdbc.url"); // Get JDBC URL from properties
        config.setJdbcUrl(jdbcUrl);
        DataSource dataSource = new HikariDataSource(config);

        // ======== Use DataSource in PgVectorEmbeddingStore ========
        this.embeddingStore = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(getProperty("pgvector.tablename"))
                .dimension(768)
                .build();
    }

    public PgVectorEmbeddingStore getEmbeddingStore() {
        return embeddingStore;
    }

    public void train(String folderPath) throws Exception {
        File folder = new File(folderPath);
        //File[] pdfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        File[] pdfFiles = folder.listFiles((file) -> file.getName().toLowerCase().endsWith(".pdf"));
        if (pdfFiles != null) {
            // ======== Ingest PDF Embeddings ========
            EmbeddingStoreIngestor storeIngestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(this.embeddingModel) // Use the instance variable
                    .embeddingStore(this.embeddingStore) // Use the instance variable
                    .build();

            System.out.println("Processing PDF files in: " + folderPath);
            for (File pdfFile : pdfFiles) {
                System.out.println("Processing: " + pdfFile.getName());
                try (InputStream inputStream = new FileInputStream(pdfFile)) {
                    ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
                    Document document = pdfParser.parse(inputStream);
                    System.out.println("Chunking and embedding...");
                    storeIngestor.ingest(document);
                    System.out.println(pdfFile.getName() + " embedding complete.");
                } catch (Exception e) {
                    System.err.println("Error processing " + pdfFile.getName() + ": " + e.getMessage());
                    // Optionally re-throw the exception if processing should stop on error
                    // throw e;
                }
            }
            System.out.println("All PDF files in the folder processed.");
        } else {
            System.out.println("No PDF files found in: " + folderPath);
        }
    }

    public static void main(String[] args) throws Exception {
        String pdfFolderPath = getProperty("pdf.folder"); // Replace with your folder path
        DocumentTrainer trainer = new DocumentTrainer(); // Create an instance of DocumentTrainer
        trainer.train(pdfFolderPath); // Call the non-static train method
    }
}