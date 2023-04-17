package chatbot.manager;

import chatbot.Main;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import lombok.Getter;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.Message;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ChatManager {
    @Getter
    private static final ChatManager instance = new ChatManager();
    @Getter
    private static final ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor();

    private static final ChatMessage PROMPT_MESSAGE = new ChatMessage(ChatMessageRole.SYSTEM.value(),
            "You are a discord chatbot that can talk to people. You will receive messages from users in the form: 'From User#123 (ID: abc): <message>'." +
                    "You can respond to users by using this: <@id>"
    );
    private List<ChatMessage> chatMessages = new ArrayList<>();

    @SneakyThrows
    public void init() {
        File messagesFile = new File("messages.json");
        if (messagesFile.exists()) {
            String data = new String(Files.readAllBytes(messagesFile.toPath()));
            Type type = new com.google.gson.reflect.TypeToken<ArrayList<ChatMessage>>(){}.getType();
            chatMessages = Main.getInstance().getGson().fromJson(data, type);
        }
    }

    public void save() {
        String data = Main.getInstance().getGson().toJson(chatMessages);
        File messagesFile = new File("messages.json");
        try {
            Files.write(messagesFile.toPath(), data.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onMessageReceived(Message message) {
        String content = message.getContentDisplay().trim();
        // remove the @ mention
        String botName = "@" + message.getJDA().getSelfUser().getName();
        // substring the message to remove the @ mention
        content = content.substring(content.indexOf(botName) + botName.length()).trim();
        System.out.println("Message received from " + message.getAuthor().getAsTag() + ": " + content);
        String chatgptMessage = "From " + message.getAuthor().getAsTag() + " (ID: " + message.getAuthor().getId() + ")" + ": " + content;
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), chatgptMessage);
        chatMessages.add(chatMessage);
        askChatGpt(message);
    }
    public void askChatGpt(Message message) {
        threadPool.submit(() -> {
            message.getChannel().sendTyping().queue();
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(chatMessages)
                    .model("gpt-3.5-turbo")
                    .build();
            ChatCompletionResult completion = Main.getInstance().getOpenAiService().createChatCompletion(completionRequest);
            completion.getChoices().forEach(System.out::println);
            String response = completion.getChoices().get(0).getMessage().getContent();
            message.reply(response).queue();
            chatMessages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), response));
        });
    }
}
