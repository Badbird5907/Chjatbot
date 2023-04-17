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
        // check if the message starts with a @ mention of the bot
        if (event.getMessage().getContentRaw().startsWith("<@" + event.getJDA().getSelfUser().getId() + ">")) {
            ChatManager.getInstance().onMessageReceived(event.getMessage());
        }
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
        }
    }
}
