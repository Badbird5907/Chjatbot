package chatbot.listener;

import chatbot.Main;
import chatbot.manager.ChatManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.List;

public class MessageListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        System.out.println("Message received from " + event.getAuthor().getAsTag() + ": " + event.getMessage().getContentRaw());
        // ignore itself
        if (Main.getInstance().getConfig().isPreventTheSingularity() && event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) return;
        ChatManager.getInstance().onMessageReceived(event.getMessage());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "shutdown": {
                List<Long> owners = Main.getInstance().getConfig().getOwners();
                Long userId = event.getUser().getIdLong();
                if (!owners.contains(userId)) {
                    event.reply("You are not allowed to use this command!").setEphemeral(true).queue();
                    return;
                }
                event.reply("Shutting down...").setEphemeral(true).queue();
                System.exit(0);
            }
            case "new": {
                event.deferReply().queue();
                event.getChannel().asTextChannel().createThreadChannel(event.getUser().getName() + "'s thread").queue(e -> {
                    event.reply("Created thread: " + e.getAsMention()).setEphemeral(true).queue();
                });
            }
            case "reset": {
                List<Long> owners = Main.getInstance().getConfig().getOwners();
                Long userId = event.getUser().getIdLong();
                boolean isThread = event.getChannel() instanceof ThreadChannel;
                boolean isThreadOwner = isThread &&
                        ((ThreadChannel) event.getChannel())
                                .getOwner().getUser().getName().equals(event.getChannel().getName().substring(0, event.getChannel().getName().length() - "'s thread".length()));
                if (event.getMember().getRoles().stream().anyMatch(role -> role.hasPermission(Permission.MANAGE_SERVER, Permission.MANAGE_CHANNEL))
                        || owners.contains(userId) || isThreadOwner
                ) {
                    Main.getInstance().getStorageProvider().reset(event.getChannel().getIdLong());
                    event.reply("Reset the conversation!").queue();
                } else {
                    event.reply("You don't have permission to run this command!").setEphemeral(true).queue();
                }
            }
            case "gpt4": {
                List<Long> owners = Main.getInstance().getConfig().getOwners();
                Long userId = event.getUser().getIdLong();
                if (!owners.contains(userId)) {
                    event.reply("You are not allowed to use this command!").setEphemeral(true).queue();
                    return;
                }

                long currentChannelId = event.getChannel().getIdLong();
                if (Main.getInstance().getGpt4Channels().contains(currentChannelId)) {
                    Main.getInstance().getGpt4Channels().remove(currentChannelId);
                    event.reply("Disabled GPT-4 for this channel!").queue();
                } else {
                    Main.getInstance().getGpt4Channels().add(currentChannelId);
                    event.reply("Enabled GPT-4 for this channel!").queue();
                }
            }
        }
    }
}
