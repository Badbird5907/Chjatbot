package chatbot.storage;

import com.google.gson.JsonObject;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.util.List;

public interface StorageProvider { // TODO maybe like stream this or smth
    void init(JsonObject options);
    void disable(JsonObject options);
    void save(long channelId, List<ChatMessage> messages);
    List<ChatMessage> load(long channelId);
}
