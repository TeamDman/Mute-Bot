import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageHandler {
	static final Pattern       cmdPattern = Pattern.compile("^/filter (\\w+)\\s?(.*)");
	static final List<Pattern> patterns   = new ArrayList<>();
	static       IRole         muteRole   = null;
	static       boolean       useRole    = main.config.get(Config.Property.MUTETYPE).toLowerCase().equals("role");

	static void read() {
		patterns.clear();
		try {
			Scanner in = new Scanner(new File("patterns.txt"));
			while (in.hasNextLine())
				patterns.add(Pattern.compile(in.nextLine()));
		} catch (IOException e) {
			System.out.println("There was an exception loading the patterns.txt file. If this is the first time run, then this can be ignored.");
			e.printStackTrace();
			write();
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
					if (!event.getAuthor().getPermissionsForGuild(event.getGuild()).contains(Permissions.ADMINISTRATOR))
						RequestBuffer.request(() -> event.getChannel().sendMessage("You do not have permission for that."));
					else
						c.action.accept(event, m.group((2)));
			}
		} else if (patterns.stream().anyMatch(p -> p.matcher(event.getMessage().getContent().toLowerCase()).find())) {
			mute(event.getAuthor(), event.getGuild());
			RequestBuffer.request(() -> event.getGuild().getChannelByID(Long.valueOf(main.config.get(Config.Property.REPORTCHANNEL))).sendMessage(new EmbedBuilder()
					.withTitle("Mute Notice")
					.withColor(Color.RED)
					.appendField(event.getAuthor().getName(), event.getAuthor().mention(), true)
					.appendField("ID", event.getAuthor().getLongID() + "", true)
					.appendField("Message Content", event.getMessage().getContent(), true)
					.appendField("Pattern Matched", patterns.stream().filter(p -> p.matcher(event.getMessage().getContent().toLowerCase()).find()).findFirst().orElseGet(() -> Pattern.compile("<ERROR>")).pattern(), true)
					.build()));
			RequestBuffer.request(() -> event.getAuthor().getOrCreatePMChannel().sendMessage(new EmbedBuilder()
					.withTitle("Mute Notice")
					.withColor(Color.RED)
					.appendDesc(main.config.get(Config.Property.MUTEMESSAGE))
					.build())
			);
		}
	}

	private static void mute(IUser user, IGuild guild) {
		if (useRole && muteRole != null) {
			AtomicReference<List<IRole>> roles = new AtomicReference<>(null);
			new RequestBuilder(main.client).doAction(() -> {
				roles.set(guild.getRolesForUser(user));
				roles.get().add(muteRole);
				return true;
			}).andThen(() -> {
				guild.editUserRoles(user, roles.get().toArray(new IRole[0]));
				return true;
			}).execute();
		} else {
			guild.getChannels().forEach(c ->
					RequestBuffer.request(() -> c.overrideUserPermissions(
							user,
							EnumSet.noneOf(Permissions.class),
							EnumSet.of(Permissions.SEND_MESSAGES, Permissions.SEND_TTS_MESSAGES, Permissions.ADD_REACTIONS))));
		}
	}


	private static void unmute(IUser user, IGuild guild) {
		if (useRole && muteRole != null) {
			AtomicReference<List<IRole>> roles = new AtomicReference<>(null);
			new RequestBuilder(main.client).doAction(() -> {
				roles.set(guild.getRolesForUser(user));
				roles.get().remove(muteRole);
				return true;
			}).andThen(() -> {
				guild.editUserRoles(user, roles.get().toArray(new IRole[0]));
				return true;
			}).execute();
		} else {
			guild.getChannels().forEach(c -> RequestBuffer.request(() -> c.removePermissionsOverride(user)));
		}

	}

	private static IUser getSingleUser(IChannel channel, String arg) {
		Pattern p    = Pattern.compile("<@!?(\\d+)>");
		Matcher m    = p.matcher(arg);
		IUser   user = null;
		if (!m.find() || (user = channel.getGuild().getUserByID(Long.valueOf(m.group(1)))) == null)
			RequestBuffer.request(() -> channel.sendMessage("No users matched the provided selector."));
		return user;
	}

	enum Command {
		ADD((event, arg) -> {
			patterns.add(Pattern.compile(arg));
			RequestBuffer.request(() -> event.getChannel().sendMessage("Successfully appended to the filter list, and updated patterns.txt"));
			write();
		}),
		REMOVE((event, arg) -> {
			if (MessageHandler.patterns.remove((int) Integer.valueOf(arg)) != null) {
				RequestBuffer.request(() -> event.getChannel().sendMessage("Successfully removed the pattern, and updated patterns.txt"));
				write();
			} else
				RequestBuffer.request(() -> event.getChannel().sendMessage("Failed to find the specified pattern"));
		}),
		LIST((event, arg) -> {
			EmbedBuilder embed = new EmbedBuilder().withTitle("Filtered Patterns");
			embed.appendDesc("Currently using " + (useRole && muteRole != null ? muteRole.mention() : "permissions") + " for muting.\n\n");
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
		}),
		MUTE((event, args) -> {
			IUser user = getSingleUser(event.getChannel(), args);
			if (user != null) {
				mute(user, event.getGuild());
				RequestBuffer.request(() -> event.getChannel().sendMessage("Muted " + user.getName()));
			}
		}),
		UNMUTE((event, args) -> {
			IUser user = getSingleUser(event.getChannel(), args);
			if (user != null) {
				unmute(user, event.getGuild());
				RequestBuffer.request(() -> event.getChannel().sendMessage("Unmuted " + user.getName()));
			}
		}),
		SETROLE((event, args) -> {
			final Pattern p = Pattern.compile("(\\d+)");
			Matcher       m = p.matcher(args);
			if (m.find()) {
				long  roleId = Long.valueOf(m.group(1));
				IRole role   = event.getGuild().getRoles().stream().filter(r -> r.getLongID() == roleId).findFirst().orElse(null);
				if (role != null) {
					muteRole = role;
					main.config.set(Config.Property.MUTEROLE, Long.toString(muteRole.getLongID()));
					RequestBuffer.request(() -> event.getChannel().sendMessage("The role has been updated and saved to config."));
					return;
				}
			}
			RequestBuffer.request(() -> event.getChannel().sendMessage("No role with given ID found."));
		}),
		GETROLE((event, args) -> {
			if (muteRole != null) {
				RequestBuffer.request(() -> event.getChannel().sendMessage("Currently using " + muteRole.mention()));
			} else {
				RequestBuffer.request(() -> event.getChannel().sendMessage("No role set."));
			}
		}),
		SETMUTETYPE((event, args) -> {
			useRole = args.toLowerCase().equals("role");
			if (useRole)
				RequestBuffer.request(() -> event.getChannel().sendMessage("The role will now be used for mutes. This change has been written to config."));
			else
				RequestBuffer.request(() -> event.getChannel().sendMessage("The role will no longer be used for mutes. This change has been written to config."));
			main.config.set(Config.Property.MUTETYPE, useRole ? "role" : "perms");
		});

		public BiConsumer<MessageReceivedEvent, String> action;

		Command(BiConsumer<MessageReceivedEvent, String> action) {
			this.action = action;
		}
	}
}
