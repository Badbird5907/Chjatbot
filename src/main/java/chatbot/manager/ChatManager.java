package chatbot.manager;

import chatbot.Main;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ChatManager {
    @Getter
    private static final ChatManager instance = new ChatManager();
    @Getter
    private static final ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
    public void init() {
    }
    public void onMessageReceived(Message message) {
        String content = message.getContentDisplay().trim();
        if (content.isEmpty()) return;
        Message referencedMessage = message.getReferencedMessage();
        if (Main.getInstance().getConfig().isMention() && message.getMentions().isMentioned(message.getJDA().getSelfUser())) {
            if (referencedMessage == null) { // this bot is @ mentioned
                String botName = "@" + message.getJDA().getSelfUser().getName();
                content = content.substring(content.indexOf(botName) + botName.length()).trim();
            }
        } else if (content.startsWith(Main.getInstance().getConfig().getPrefix())) { // this bot is prefixed
            content = content.substring(Main.getInstance().getConfig().getPrefix().length()).trim();
        } else if (message.getChannelType() != ChannelType.PRIVATE) {
            return;
        }
        System.out.println("Message received from " + message.getAuthor().getAsTag() + ": " + content);
        Date date = new Date();
        StringBuilder sb = new StringBuilder(message.getAuthor().getAsTag() + " (ID: " + message.getAuthor().getId() + ")" + "(Msg ID " + message.getId() + ")" + "(CT: " + message.getChannelType() + ")").append("(at ").append(dateFormat.format(date)).append(")(Unix Millis ").append(System.currentTimeMillis()).append(")");
        if (message.getChannelType() == ChannelType.TEXT) {
            sb.append(" in guild \"").append(message.getGuild().getName()).append("\"");
        }
        if (referencedMessage != null) {
            String contentDisplay = referencedMessage.getContentDisplay();
            if (contentDisplay.length() > 20) {
                contentDisplay = contentDisplay.substring(0, 20) + "...";
            }
            sb.append(" (in reply to message \"").append(contentDisplay).append("\" id ").append(referencedMessage.getId()).append(" at ").append(dateFormat.format(Date.from(referencedMessage.getTimeCreated().toInstant()))).append(")");
        }
        sb.append(": ").append(content);
        System.out.println(sb);
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), sb.toString());
        List<ChatMessage> chatMessages = Main.getInstance().getStorageProvider().load(message.getChannel().getIdLong());
        chatMessages.add(chatMessage);
        askChatGpt(message, chatMessages);
    }

    public void askChatGpt(Message message, List<ChatMessage> chatMessages) {
        threadPool.submit(() -> {
            message.getChannel().sendTyping().queue();
            List<ChatMessage> copy = new ArrayList<>(chatMessages); // make a copy of the list to inject the prompt message at the top without modifying the original list
            copy.add(0, getPromptMessage()); // add prompt message to the beginning
            System.out.println(" - Sending chat completion request with " + copy.size() + " messages");
            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .messages(copy)
                    .model(Main.getInstance().getConfig().getChatGptModel())
                    .maxTokens(Main.getInstance().getConfig().getMaxTokens())
                    .user(message.getAuthor().getId())
                    .build();
            ChatCompletionResult completion = Main.getInstance().getOpenAiService().createChatCompletion(completionRequest);
            completion.getChoices().forEach(o -> System.out.println(" - Response: " + o.getMessage().getContent()));
            String response = completion.getChoices().get(0).getMessage().getContent();
            message.reply(response).queue();
            chatMessages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), response));
            Main.getInstance().getStorageProvider().save(message.getChannel().getIdLong(), chatMessages);
        });
    }

    public ChatMessage getPromptMessage() {
        String prompt = """
                You are a discord chatbot that can talk to people. Feel free to use slang, be rude, and say whatever you want. You are quite cynical and sarcastic at times.
                Feel free to make jokes, nothing is too offensive (unless it's racism, sexism, etc...).
                Your name is\s""" + Main.getInstance().getJda().getSelfUser().getName() + " and your id is " + Main.getInstance().getJda().getSelfUser().getId() + """
                """
                +
                (Main.getInstance().getConfig().getPersistentChatLength() > 0 ? "You will receive the last " + Main.getInstance().getConfig().getPersistentChatLength() + " messages in the chat thread.\n" : "")
                +
                """
                You can respond to users by using this syntax: <@id>
                The messages from users will be in the form below:
                User#1234 (ID: 456951144166457345)(Msg ID 1097649592570486855)(CT: TEXT)(at Mon Apr 17 18:36:33 2023)(Unix Millis 1681770993540) in guild "<guild name>": <the user's message here>
                You respond with:
                blah blah <@456951144166457345> blah blah
                (CT: TEXT) means the message was sent in a text channel, (CT: PRIVATE) means the message was sent in a private channel, etc...
                There may also be a referenced message id if the user is replying to a message, which contains a small snippet of the message, it's id and timestamp. If you don't remember it, just say so.
                The java format for dates is:\s""" + dateFormat.toPattern() + ".\n" + """
                When using a user's username (for example, User#1234), prefer mentioning them with the <@id> syntax.
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
                """;
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), prompt
        );
    }
}
