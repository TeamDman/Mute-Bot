import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageHandler {
	static final Pattern       cmdPattern = Pattern.compile("^/filter (\\w+)\\s?(.*)");
	static final List<Pattern> patterns   = new ArrayList<>();

	static {
		read();
	}

	static void read() {
		patterns.clear();
		try {
			Scanner in = new Scanner(new File("patterns.txt"));
			while (in.hasNextLine())
				patterns.add(Pattern.compile(in.nextLine()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void write() {
		try (FileWriter writer = new FileWriter(new File("patterns.txt"))) {
			writer.flush();
			for (Pattern pattern : patterns) {
				writer.write(pattern.pattern());
				writer.write('\n');
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@EventSubscriber
	public static void handle(MessageReceivedEvent event) {
		Matcher m = cmdPattern.matcher(event.getMessage().getContent());
		if (m.find()) {
			for (Command c : Command.values()) {
				if (c.name().toLowerCase().equals(m.group(1)))
					c.action.accept(event, m.group((2)));
			}
		} else if (patterns.stream().anyMatch(p -> p.matcher(event.getMessage().getContent()).find())) {
			RequestBuffer.request(() -> event.getGuild().setMuteUser(event.getAuthor(), true));
			RequestBuffer.request(() -> event.getGuild().getChannelByID(Long.valueOf(main.config.get(Config.Property.REPORTCHANNEL))).sendMessage(new EmbedBuilder()
					.withTitle("Mute Notice")
					.withColor(Color.RED)
					.appendField(event.getAuthor().getName(),event.getAuthor().mention(),true)
					.appendField("ID", event.getAuthor().getLongID()+"", true)
					.appendField("Message Content", event.getMessage().getContent(), true)
					.appendField("Pattern Matched", patterns.stream().filter(p -> p.matcher(event.getMessage().getContent()).find()).findFirst().orElseGet(() ->Pattern.compile("<ERROR>")).pattern(), true)
					.build()));
		}
	}

	enum Command {
		ADD((event, arg) -> {
			patterns.add(Pattern.compile(arg));
			RequestBuffer.request(() -> event.getChannel().sendMessage("Successfully appended to the filter list, and updated patterns.txt"));
			write();
		}),
		REMOVE((event, arg) -> {
			if (MessageHandler.patterns.remove((int) Integer.valueOf(arg)) != null)
				RequestBuffer.request(() -> event.getChannel().sendMessage("Successfully removed the pattern"));
			else
				RequestBuffer.request(() -> event.getChannel().sendMessage("Failed to find the specified pattern"));
		}),
		LIST((event, arg) -> {
			EmbedBuilder embed = new EmbedBuilder().withTitle("Filtered Patterns");
			for (int i = 0; i < patterns.size(); i++) {
				embed.appendDesc("**" + i + ":** " + patterns.get(i).pattern() + "\n");
			}
			RequestBuffer.request(() -> event.getChannel().sendMessage(embed.build()));
		}),
		WRITE((event, arg) -> {
			write();
			RequestBuffer.request(() -> event.getChannel().sendMessage("Wrote pattern list to file"));
		}),
		READ((event, arg) -> {
			read();
			RequestBuffer.request(() -> event.getChannel().sendMessage("Replaced pattern list from file"));
		}),
		PRESETS((event, arg) -> {
			patterns.add(Pattern.compile("<@\\!?(\\d{18})>.*?<@\\!?(?!\\1)(\\d{18})>.*?<@\\!?(?!(?:\\1|\\2))(\\d{18})>.*?<@\\!?(?!(?:\\1|\\2))(\\d{18})>.*?<@\\!?(?!(?:\\1|\\2|\\3))(\\d{18})>.*?<@\\!?(?!(?:\\1|\\2|\\3|\\4))(\\d{18})>"));
			RequestBuffer.request(() -> event.getChannel().sendMessage("Filled the pattern list with the presets. This change was not written to file"));
		}),
		CLEAR((event, args) -> {
			patterns.clear();
			RequestBuffer.request(() -> event.getChannel().sendMessage("Cleared the pattern list. This change was not written to file"));
		}),
		HELP((event, args) -> {
			EmbedBuilder embed = new EmbedBuilder().withTitle("Filter Commands");
			for (Command c : Command.values())
				embed.appendDesc("/" + c.name().toLowerCase() + " \n");
			RequestBuffer.request(() -> event.getChannel().sendMessage(embed.build()));
		});


		public BiConsumer<MessageReceivedEvent, String> action;

		Command(BiConsumer<MessageReceivedEvent, String> action) {
			this.action = action;
		}
	}
}
