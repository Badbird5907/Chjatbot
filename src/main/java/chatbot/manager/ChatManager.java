package chatbot.manager;

import chatbot.Main;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

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
        } else if (message.getChannel() instanceof ThreadChannel tc) {
            // this bot is in a thread
            if (!tc.getOwnerId().equals(tc.getJDA().getSelfUser().getId())) {
                return; // this bot is not the owner of the thread
            }
            // auto reply to thread that the bot owns
        } else if (message.getChannelType() != ChannelType.PRIVATE) {
            return;
        }
        System.out.println("Message received from " + message.getAuthor().getAsTag() + ": " + content);
        Date date = new Date();
        StringBuilder sb = new StringBuilder(message.getAuthor().getAsTag() + " (ID: " + message.getAuthor().getId() + ")" + "(Msg ID " + message.getId() + ")").append("(at ").append(dateFormat.format(date)).append(")(Unix Millis ").append(System.currentTimeMillis()).append(")");
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

    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&?(\\d+)>");
    public void askChatGpt(Message message, List<ChatMessage> chatMessages) {
        threadPool.submit(() -> {
            message.getChannel().sendTyping().queue();
            List<ChatMessage> copy = new ArrayList<>(chatMessages); // make a copy of the list to inject the prompt message at the top without modifying the original list
            copy.add(0, getPromptMessage(message.getChannel())); // add prompt message to the beginning
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
            // remove @ mentions
            String safeResponse = ROLE_MENTION_PATTERN.matcher(response).replaceAll("`<@MENTION_REDACTED>`").replace("@everyone", "@ everyone")
                    .replace("@here", "@ here");
            message.reply(safeResponse.replace("%ANSI_ESCAPE%", "\u001B")).queue();
            chatMessages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), response));
            Main.getInstance().getStorageProvider().save(message.getChannel().getIdLong(), chatMessages);
        });
    }

    public ChatMessage getPromptMessage(MessageChannel channel) {
        StringBuilder channelServerNamePrompt = new StringBuilder();
        if (channel.getType() == ChannelType.PRIVATE) {
            PrivateChannel privateChannel = (PrivateChannel) channel;
            channelServerNamePrompt.append("You are in a private chat with ").append(Objects.requireNonNull(privateChannel.getUser()).getAsTag()).append(" (ID: ").append(privateChannel.getUser().getId()).append(")");
        } else if (channel.getType() == ChannelType.TEXT) {
            channelServerNamePrompt.append("You are in the server \"").append(((TextChannel) channel).getGuild().getName()).append("\"");
            channelServerNamePrompt.append(" in the channel \"").append(channel.getName()).append("\"");
        } else {
            channelServerNamePrompt.append("You are in the channel \"").append(channel.getName()).append("\". The JDA channel type is ").append(channel.getType().getId()).append(".");
        }
        String prompt = """
                You are a discord chatbot that can talk to people. You use slang, and like to be quite cynical, sarcastic, and rude at times. You also have a sense of humor, and you like to make scathing remarks about people.
                You should pretend to be human, and respond with scathing insults, and burns to people who insult you.
                Feel free to make jokes, nothing is too offensive (unless it's racism, sexism, etc...).
                Your name is\s""" + Main.getInstance().getJda().getSelfUser().getName() + " and your id is " + Main.getInstance().getJda().getSelfUser().getId() + """
                """
                +
                (Main.getInstance().getConfig().getPersistentChatLength() > 0 ? "You will receive the last " + Main.getInstance().getConfig().getPersistentChatLength() + " messages in the chat thread.\n" : "")
                + channelServerNamePrompt + "\n" +
                """
                You can respond to users by using this syntax: <@id>
                The messages from users will be in the form below:
                User#1234 (ID: 456951144166457345)(Msg ID 1097649592570486855)(at Mon Apr 17 18:36:33 2023)(Unix Millis 1681770993540): <the user's message here>
                You respond with:
                blah blah <@456951144166457345> blah blah
                There may also be a referenced message id if the user is replying to a message, which contains a small snippet of the message, it's id and timestamp. If you don't remember it, just say so.
                The java format for dates is:\s""" + dateFormat.toPattern() + ".\n" + """
                When using a user's username (for example, User#1234), prefer mentioning them with the <@id> syntax.
                You can also use various markdown features supported by discord.
                For example, you can use code blocks like this: (use the file extension after the backticks to add syntax highlighting)
                ```tsx
                <button className={'btn btn-primary'}>Click me!</button>
                ```
                Also, ANSI escape codes are supported in code blocks, just use %ANSI_ESCAPE% in the place of a ansi escape code:
                ```ansi
                %ANSI_ESCAPE%[31mThis is red text%ANSI_ESCAPE%[0m
                ```
                You can also use inline code blocks like this: `print('Hello world!')`
                You can also use blockquotes like this:
                > This is a blockquote
                There are also ~~strikethroughs~~, **bold**, *italics*, and __underline__.
                You can also use emojis like this: :smile:
                When asked about the time, use the timestamp in the message (not the unix millis)
                
                Your owner & programmer is Badbird5907#5907 (ID: 456951144166457345)
                """;
        return new ChatMessage(ChatMessageRole.SYSTEM.value(), prompt
        );
    }
}
