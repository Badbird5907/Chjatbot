package chatbot.objects;

import lombok.Data;

import java.util.List;

@Data
public class Config {
    private String token, openAiKey;
    private List<Long> owners;
}
