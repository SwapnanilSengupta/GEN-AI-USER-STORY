package gemini.workshop;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
import java.io.IOException;

public class DocumentTrainer {

    private PgVectorEmbeddingStore embeddingStore;
    private VertexAiEmbeddingModel embeddingModel;
    private final String bucketName;
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

        this.bucketName =getProperty("google.storage.bucket");
    }

    public void train() throws Exception {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        Iterable<Blob> blobs = storage.list(bucketName).iterateAll();

        // ======== Ingest PDF Embeddings ========
        EmbeddingStoreIngestor storeIngestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(this.embeddingModel)
                .embeddingStore(this.embeddingStore)
                .build();

        System.out.println("Processing PDF files in Google Cloud Storage bucket: " + bucketName);
        boolean foundPdf = false;
        for (Blob pdfBlob : blobs) {
            if (pdfBlob.getName().toLowerCase().endsWith(".pdf")) {
                foundPdf = true;
                String fileName = pdfBlob.getName();
                System.out.println("Processing: " + fileName);
                try (InputStream inputStream = new java.io.ByteArrayInputStream(pdfBlob.getContent())) {
                    ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
                    Document document = pdfParser.parse(inputStream);
                    System.out.println("Chunking and embedding...");
                    storeIngestor.ingest(document);
                    System.out.println(fileName + " embedding complete.");
                } catch (Exception e) {
                    System.err.println("Error processing " + fileName + ": " + e.getMessage());
                    // Optionally re-throw the exception if processing should stop on error
                    // throw e;
                }
            }
        }

        if (!foundPdf) {
            System.out.println("No PDF files found in Google Cloud Storage bucket: " + bucketName);
        } else {
            System.out.println("All PDF files in the bucket processed.");
        }
    }

    public static void main(String[] args) throws Exception {
        DocumentTrainer trainer = new DocumentTrainer(); // Create an instance of DocumentTrainer
        trainer.train();
    }
}