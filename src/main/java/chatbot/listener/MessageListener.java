package chatbot.listener;

import chatbot.Main;
import chatbot.manager.ChatManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
                Main.getInstance().getStorageProvider().reset(event.getChannel().getIdLong());
                event.reply("Reset the conversation!").queue();
            }
        }
    }
}
