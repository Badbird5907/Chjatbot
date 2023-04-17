package chatbot.objects;

import lombok.Data;

import java.util.List;

@Data
public class Config {
    private String token, openAiKey;
    private List<Long> owners;
    private int persistentChatLength; // max number of messages to remember, set to 0 to disable
}
