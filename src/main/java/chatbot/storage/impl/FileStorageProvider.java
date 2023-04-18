package chatbot.storage.impl;

import chatbot.Main;
import chatbot.objects.SizedList;
import chatbot.storage.StorageProvider;
import com.google.gson.JsonObject;
import com.theokanning.openai.completion.chat.ChatMessage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileStorageProvider implements StorageProvider {
    private File dataFolder;
    @Override
    public void init(JsonObject options) {
        dataFolder = new File(options.get("dataPath").getAsString());
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    @Override
    public void disable(JsonObject options) {

    }

    @Override
    public void save(long channelId, List<ChatMessage> messages) {
        File file = new File(dataFolder, channelId + ".json");
        if (!file.exists()) try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.write(file.toPath(), Main.getGson().toJson(messages).getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ChatMessage> load(long channelId) {
        File file = new File(dataFolder, channelId + ".json");
        if (!file.exists()) return new ArrayList<>();
        String data;
        try {
            data = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Type type = new com.google.gson.reflect.TypeToken<List<ChatMessage>>() {
        }.getType();
        List<ChatMessage> list = Main.getGson().fromJson(data, type);
        SizedList<ChatMessage> sizedList = new SizedList<>(Main.getInstance().getConfig().getPersistentChatLength());
        sizedList.addAll(list);
        return sizedList;
    }
}
