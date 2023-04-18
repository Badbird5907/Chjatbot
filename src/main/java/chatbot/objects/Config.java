package chatbot.objects;

import com.google.gson.JsonObject;
import lombok.Data;

import java.util.List;

@Data
public class Config {
    private String token, openAiKey, chatGptModel, prefix, storageProvider;
    private JsonObject storageProviderOptions;
    private boolean mention;
    private List<Long> owners;
    private int persistentChatLength; // max number of messages to remember, set to 0 to disable
}
