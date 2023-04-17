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

    private List<ChatMessage> chatMessages = new ArrayList<>();

    @SneakyThrows
    public void init() {
        File messagesFile = new File("messages.json");
        if (messagesFile.exists()) {
            String data = new String(Files.readAllBytes(messagesFile.toPath()));
            Type type = new com.google.gson.reflect.TypeToken<ArrayList<ChatMessage>>() {
            }.getType();
            chatMessages = Main.getGson().fromJson(data, type);
        }
    }

    public void save() {
        List<ChatMessage> chatMessages = new ArrayList<>(this.chatMessages);
        chatMessages.remove(0);
        String data = Main.getGson().toJson(chatMessages);
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
            List<ChatMessage> chatMessages = new ArrayList<>(this.chatMessages);
            chatMessages.add(getPromptMessage());
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

    public ChatMessage getPromptMessage() {
        return new ChatMessage(ChatMessageRole.SYSTEM.value(),
                """
                        You are a discord chatbot that can talk to people. Feel free to use slang, be rude, etc...
                        Your name is\\s""\" + Main.getInstance().getJda().getSelfUser().getName() + " and your id is " + Main.getInstance().getJda().getSelfUser().getId() + ""\"
                        """
                        +
                        (Main.getInstance().getConfig().getPersistentChatLength() > 0 ? "You will receive the last " + Main.getInstance().getConfig().getPersistentChatLength() + " messages in the chat thread." : "")
                        +
                        """
                        The messages from users will be in the form: 'From User#123 (ID: abc): <message>'.
                        You can respond to users by using this syntax: <@id>
                        Here's a example message:
                        From User#1234 (ID: 456951144166457345): <the user's message here>
                        You respond with:
                        blah blah <@456951144166457345> blah blah
                        When using a user's username, prefer mentioning them with the <@id> syntax.
                        You can also use various markdown features supported by discord.
                        For example, you can use code blocks like this: (use the file extension after the backticks to add syntax highlighting)
                        ```tsx
                        <button className={'btn btn-primary'}>Click me!</button>
                        ```
                        Also, ANSI escape codes are supported in code blocks:
                        ```ansi
                        \u001B[31mThis is red text\u001B[0m
                        ```
                        You can also use inline code blocks like this: `print('Hello world!')`
                        You can also use blockquotes like this:
                        > This is a blockquote
                        There are also ~~strikethroughs~~, **bold**, *italics*, and __underline__.
                        You can also use emojis like this: :smile:
                        """
        );
    }
}
