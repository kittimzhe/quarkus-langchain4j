package io.quarkiverse.langchain4j.test.faulttolerance;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that a build-time warning is produced when {@code @Retry} is applied
 * to a {@code @RegisterAiService} method that registers tools. The warning alerts
 * developers that retrying the whole ReAct loop can silently re-execute tool side
 * effects that already completed in the failed attempt.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-langchain4j/issues/2744">issue #2744</a>
 */
public class RetryWithToolsWarningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AiServiceWithRetryAndTools.class, MyTools.class, MyChatModel.class,
                            MyModelSupplier.class));

    @Test
    void shouldWarnWhenRetryUsedWithTools() {
        unitTest.assertLogRecords(logRecords -> {
            List<LogRecord> warnings = logRecords.stream()
                    .filter(l -> l.getLevel() == Level.WARNING)
                    .filter(l -> l.getMessage().contains("AiServiceWithRetryAndTools#chat"))
                    .filter(l -> l.getMessage().contains("Retry"))
                    .toList();

            org.assertj.core.api.Assertions.assertThat(warnings)
                    .as("A warning should be logged when @Retry is used on an AI service method that registers tools")
                    .isNotEmpty();
        });
    }

    @RegisterAiService(tools = MyTools.class, chatLanguageModelSupplier = MyModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface AiServiceWithRetryAndTools {

        @Retry(maxRetries = 2)
        @UserMessage("charge the customer")
        String chat(String message);
    }

    @ApplicationScoped
    public static class MyTools {
        @Tool
        public String chargeCustomer(String message) {
            return "charged $10";
        }
    }

    public static class MyModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("done")).build();
        }
    }
}
