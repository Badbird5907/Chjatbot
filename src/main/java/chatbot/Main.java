package chatbot;

import chatbot.listener.MessageListener;
import chatbot.manager.ChatManager;
import chatbot.objects.Config;
import chatbot.storage.StorageProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.theokanning.openai.service.OpenAiService;
import lombok.Getter;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;

public class Main {
    @Getter
    private static Main instance = new Main();
    private static final File configFile = new File("config.json");
    @Getter
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Getter
    private Config config;
    @Getter
    private JDA jda;
    @Getter
    private OpenAiService openAiService;
    @Getter
    private StorageProvider storageProvider;
    @Getter
    private Set<Long> gpt4Channels = new HashSet<>();

    public static void main(String[] args) {
        instance.init();
    }

    @SneakyThrows
    public void init() {
        if (!configFile.exists()) {
            config = new Config();
            String data = gson.toJson(config);
            Files.write(configFile.toPath(), data.getBytes());
            System.err.println("Please fill out the config file and restart the bot.");
            return;
        }
        String data = new String(Files.readAllBytes(configFile.toPath()));
        config = gson.fromJson(data, Config.class);

        jda = JDABuilder.createDefault(config.getToken())
                .enableIntents(Arrays.asList(GatewayIntent.values()))
                .setActivity(Activity.watching("you in your walls"))
                .addEventListeners(new MessageListener())
                .build();
        jda.awaitReady();
        System.out.println("Bot is ready!");
        System.out.println("Logged in as " + jda.getSelfUser().getAsTag() + " (ID: " + jda.getSelfUser().getId() + ")");

        System.out.println("Initializing storage provider...");
        String packageName = StorageProvider.class.getPackage().getName() + ".impl.";
        storageProvider = Class.forName(packageName + config.getStorageProvider()).asSubclass(StorageProvider.class).newInstance();
        storageProvider.init(config.getStorageProviderOptions());

        File gpt4File = new File("gpt4.json");
        if (!gpt4File.exists()) {
            try {
                gpt4File.createNewFile();
                String gpt4Data;
                try {
                    gpt4Data = new String(Files.readAllBytes(gpt4File.toPath()));
                    // Type type = new TypeToken<HashSet<Long>>() {
                    // }.getType();
                    // gpt4Channels = Main.getGson().fromJson(gpt4Data, type);
                    gpt4Channels.clear();
                    JsonArray array = Main.getGson().fromJson(gpt4Data, JsonArray.class);
                    for (int i = 0; i < array.size(); i++) {
                        gpt4Channels.add(array.get(i).getAsLong());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        System.out.println("Initializing Slash Commands...");
        List<SlashCommandData> slashCommands = new ArrayList<>();
        slashCommands.add(
                Commands.slash(
                        "shutdown",
                        "Shuts down the bot"
                )
        );
        slashCommands.add(
                Commands.slash(
                        "new",
                        "Creates a new thread"
                )
        );
        slashCommands.add(
                Commands.slash(
                        "reset",
                        "Reset chat history, useful if the bot becomes too nice"
                )
        );
        slashCommands.add(
                Commands.slash(
                        "gpt4",
                        "Toggle GPT-4 for a channel - Bot owner only due to cost"
                )
        );
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(slashCommands).queue();
        }

        System.out.println("Initializing OpenAI...");
        openAiService = new OpenAiService(config.getOpenAiKey());

        System.out.println("Initializing chat manager...");
        ChatManager.getInstance().init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving...");
            storageProvider.disable(config.getStorageProviderOptions());
            System.out.println("Saving GPT-4 channels...");
            String gpt4Data = gson.toJson(gpt4Channels);
            try {
                Files.write(gpt4File.toPath(), gpt4Data.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        System.out.println("Done!");
    }
}
