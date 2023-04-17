package chatbot;

import chatbot.listener.MessageListener;
import chatbot.manager.ChatManager;
import chatbot.objects.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    @Getter
    private static Main instance = new Main();
    private static final File configFile = new File("config.json");
    @Getter
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Getter
    private Config config;
    private JDA jda;
    @Getter
    private OpenAiService openAiService;
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

        System.out.println("Initializing Slash Commands...");
        List<SlashCommandData> slashCommands = new ArrayList<>();
        slashCommands.add(
                Commands.slash(
                        "shutdown",
                        "Shuts down the bot"
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
            System.out.println("Saving chat messages...");
            ChatManager.getInstance().save();
        }));
    }
}