package gemini.workshop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.Properties;
import javax.sql.DataSource;
import java.io.InputStream;
import java.io.IOException; // Ensure IOException is imported

public class DocumentQnA {

    private final CarExpert expert;

    private static Properties properties;

    static {
        properties = new Properties();
        try (InputStream input = DocumentQnA.class.getClassLoader().getResourceAsStream("configuration.properties")) {
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

    public DocumentQnA(PgVectorEmbeddingStore embeddingStore, VertexAiEmbeddingModel embeddingModel) {
        // ======== Create Chat Model ========
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project("ai-testautomation-production")
                .location("us-central1")
                .modelName("gemini-1.5-flash-002")
                .maxOutputTokens(2500)
                .build();

        // ======== Content Retriever from EmbeddingStore ========
        EmbeddingStoreContentRetriever retriever = new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel);

        // ======== Define AI Interface and build ========
        this.expert = AiServices.builder(CarExpert.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(retriever)
                .build();

        System.out.println("QnA system ready!");
    }

    public String askQuestionsToModel(String queries) {
        Result<String> response = expert.ask(queries);
        System.out.printf("%n=== %s === %n%n %s %n%n", queries, response.content());
        if (!response.sources().isEmpty()) {
            System.out.println("SOURCE: " + response.sources().getFirst().textSegment().text());
        }
        return response.content();
    }

    private interface CarExpert {
        Result<String> ask(String question);
    }

    public static String askQuestion(String question) throws Exception {
        VertexAiEmbeddingModel embeddingModel = VertexAiEmbeddingModel.builder()
                .endpoint("us-central1" + "-aiplatform.googleapis.com:443")
                .project("ai-testautomation-production")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .maxRetries(3)
                .build();

        HikariConfig config = new HikariConfig();

        String jdbcUrl = getProperty("jdbc.url"); // Get JDBC URL from properties
        config.setJdbcUrl(jdbcUrl);
        DataSource dataSource = new HikariDataSource(config);

        // ======== Use DataSource in PgVectorEmbeddingStore ========
        PgVectorEmbeddingStore embeddingStore = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(getProperty("pgvector.tablename"))
                .dimension(768)
                .build();

        DocumentQnA qna = new DocumentQnA(embeddingStore, embeddingModel);
        String result = qna.askQuestionsToModel(question);
        QADataInserter.insertQAData(question, result);
        String excelFilePath = getProperty("output.excel.file");
        ExcelDataAppender.appendToFile(excelFilePath, question, result);
        String textFilePath = getProperty("output.text.file");
        TextDataAppender.appendToFile(textFilePath, question, result);
        return result;
    }
}