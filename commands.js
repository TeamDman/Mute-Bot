const discord = require('discord.js');
const config = require("./config.json");
const jsonfile = require('jsonfile');
const util = require('util');
const commands = {};
let client = null;

String.prototype.formatUnicorn = String.prototype.formatUnicorn ||
    function () {
        "use strict";
        var str = this.toString();
        if (arguments.length) {
            var t = typeof arguments[0];
            var key;
            var args = ("string" === t || "number" === t) ?
                Array.prototype.slice.call(arguments)
                : arguments[0];

            for (key in args) {
                str = str.replace(new RegExp("\\{" + key + "\\}", "gi"), args[key]);
            }
        }

        return str;
    };

commands.writeConfig = () => jsonfile.writeFile('config.json', config, {spaces: 4}, err => {
    if (err) console.error(err);
});

commands.getRole = identifier => {
    if (typeof identifier === 'string')
        if ((identifier = identifier.replace(/\s+/g, '_').toLowerCase()).match(/\d+/g))
            identifier = identifier.match(/\d+/g);
    for (let guild of client.guilds.values())
        for (let role of guild.roles.values())
            if (role.id == identifier || role.name.replace(/\s+/g, '_').toLowerCase() == identifier)
                return role;
    return null;
};

commands.getChannel = identifier => {
    if (typeof identifier === 'string')
        if (identifier.match(/\d+/g))
            identifier = identifier.match(/\d+/g);
    for (let guild of client.guilds.values())
        for (let channel of guild.channels.values())
            if (channel.id == identifier || channel.name == identifier)
                return channel;
    return null;
};

commands.getUser = (guild, identifier) => {
    if (typeof identifier === 'string')
        if ((identifier = identifier.replace(/\s+/g, '_').toLowerCase()).match(/\d+/g))
            identifier = identifier.match(/\d+/g);
    for (let member of guild.members.values())
        if (member.id == identifier || member.user.username.replace(/\s+/g, '_').toLowerCase() == identifier)
            return member;
    return null;
};

commands.mute = async member => {
    console.log(`Attempting to mute ${member.user.username}.`);
    if (config.mute_role_enabled) {
        let role = commands.getRole(config.mute_role_id);
        if (role == null) {
            console.log("Mute role missing. Reverting to permission overrides")
        } else {
            member.addRole(role);
            return;
        }
    }
    for (let channel of member.guild.channels.values())
        channel.overwritePermissions(member, {SEND_MESSAGES: false});
};

commands.unmute = async member => {
    console.log(`Attempting to unmute ${member.user.username}.`);
    if (config.mute_role_enabled) {
        let role = commands.getRole(config.mute_role_id);
        if (role == null) {
            console.log("Mute role missing. Reverting to permission overrides")
        } else {
            member.removeRole(role).catch(e => console.error(e));
            return;
        }
    }
    for (let channel of member.guild.channels.values())
        channel.permissionOverwrites.get(member.id).delete("pardoned");
    // await channel.replacePermissionOverwrites({
    //     overwrites: [{id: member.id, denied: ['SEND_MESSAGES']}],
    //     reason: "User mute pardoned"
    // });
};

commands.getClaims = user => {
    if (claims[user.id] === undefined)
        return claims[user.id] = {};
    else
        return claims[user.id];
};

commands.getColourDistance = (first, second) => {
    return Math.abs(Math.sqrt(Math.pow(second[0] - first[0], 2) + Math.pow(second[1] - first[1], 2) + Math.pow(second[2] - first[2], 2)));
};

commands.createPaginator = async (sourceMessage, message, next, prev) => {
    const emojinext = "▶";
    const emojiprev = "◀";
    const emojistop = "❌";
    try {
        await message.react(emojiprev);
        await message.react(emojinext);
        // await message.react(emojistop);
        let handle = (reaction, user) => {
            if (reaction.message.id !== message.id)
                return;
            if (user.id !== sourceMessage.author.id ||
                reaction.emoji.name !== emojinext &&
                reaction.emoji.name !== emojiprev &&
                reaction.emoji.name !== emojistop)
                return;
            switch (reaction.emoji.name) {
                case emojinext:
                    next();
                    break;
                case emojiprev:
                    prev();
                    break;
                case emojistop:
                    message.delete().catch(e => console.log(e));
                    sourceMessage.delete().catch(e => console.log(e));
                    break;
                default:
                    console.log('Something went processing emoji reactions.');
                    break;
            }
        };
        client.on("messageReactionAdd", handle);
        client.on("messageReactionRemove", handle);
    } catch (error) {
        console.log('Error involving reaction collector.');
    }
};

commands.report = async message => {
    if (commands.getChannel(config.report_channel_id) !== null)
        commands.getChannel(config.report_channel_id).send(message);
    else
        console.log(`${message}\nMake sure to set a report channel id.`);
}


commands.hasPerms = member => {
    return member.hasPermission("ADMINISTRATOR") || (client.user.id === "431980306111660062" && member.user.id === "159018622600216577");
};

commands.handleRei = async (message) => {
    if (config.rei_mute)
        commands.mute(message.member).catch(e => console.error(e));
    let timer = config.rei_timer;
    let embed = new discord.RichEmbed()
        .setColor("RED")
        .setDescription(config.funtext[Math.floor(Math.random() * config.funtext.length)].formatUnicorn({name: message.author.username}))
        .setFooter(`${timer} seconds`);
    let msg = await message.channel.send(embed);
    msg.react("✅").catch(e => console.error(e));
    let hook = setInterval(() => {
        msg.edit(embed.setFooter(`${--timer} seconds`));
        if (timer === 0) {
            clearInterval(hook);
            message.guild.ban(message.member, {reason: config.rei_banreason}).catch(e => console.error(e));
            msg.clearReactions().catch(e => console.error(e));
        }
    }, 1000);
    msg.createReactionCollector((react, user) =>
        user.id !== client.user.id &&
        commands.hasPerms(message.guild.members.get(user.id)) &&
        react.emoji.name === "✅").on("collect", async (reaction, collector) => {
        clearInterval(hook);
        msg.clearReactions().catch(e => console.error(e));
        msg.edit(embed.setColor("GREEN"));
    });
};

commands.handleInfraction = async (message, pattern) => {
    commands.mute(message.member).catch(e => console.error(e));
    if (config.infraction_mute_notify_enable)
        message.author.createDM().then(c => c.send(config.infraction_mute_notify_message).catch(e => console.error(e))).catch(e => console.error(e));
    let channel = commands.getChannel(config.report_channel_id);
    if (channel === null) {
        console.error("Set a proper reporting channel.");
        channel = message.channel;
    }
    let msg = await channel.send(new discord.RichEmbed()
        .setTitle("Mute Notice")
        .setColor("ORANGE")
        .setThumbnail(message.author.avatarURL)
        .addField("Name", `${message.author}`)
        .addField("Message", message.content)
        .addField("Pattern Matched", `\`${pattern}\``)
    );
    msg.react('✅');
    msg.react('\uD83D\uDC80');
    msg.createReactionCollector((react, user) => user.id !== client.user.id).on('collect', async (reaction, collector) => {
        switch (reaction.emoji.name) {
            case '\uD83D\uDC80':
                message.guild.ban(message.member, {reason: `Filter infraction`}).catch(e => console.error(e));
                if (config.infraction_ban_notify_enable)
                    message.author.createDM().then(c => c.send(config.infraction_ban_notify_message).catch(e => console.error(e))).catch(e => console.error(e));
                commands.report(new discord.RichEmbed()
                    .setTitle("Ban Notice")
                    .setColor("RED")
                    .setThumbnail(message.author.avatarURL)
                    .addField("User", `<@${message.author.id}>`)
                    .addField("Moderator", `<@${reaction.users.get(reaction.users.lastKey()).id}>`)
                );
                break;
            case '✅':
                commands.unmute(message.member).catch(e => console.error(e));
                if (config.infraction_pardon_notify_enable)
                    message.author.createDM().then(c => c.send(config.infraction_pardon_notify_message).catch(e => console.error(e))).catch(e => console.error(e));
                commands.report(new discord.RichEmbed()
                    .setTitle("Unmute Notice")
                    .setColor("GREEN")
                    .setThumbnail(message.author.avatarURL)
                    .addField("User", `<@${message.author.id}>`)
                    .addField("Moderator", `<@${reaction.users.get(reaction.users.lastKey()).id}>`)
                );
                break;
            default:
                return;
        }
        message.clearReactions().catch(e => console.error(e));
    });
};

commands.onMessage = async message => {
    if (message.author.bot)
        return;
    // if (message.author.id !== "159018622600216577")
    //     return;
    let pattern;
    if (message.content.indexOf(config.prefix) !== 0)
        if (message.content.match(config.rei_irl))
            return commands.handleRei(message);
        else if ((pattern = config.patterns.find(p => message.content.match(new RegExp(p)))))
            return commands.handleInfraction(message, pattern);
        else
            return;
    if (!commands.hasPerms(message.member))
        return message.channel.send("You do not have permissions to use this command.");
    let args = message.content.slice(config.prefix.length).trim().split(/\s+/g);
    let command = args.shift().toLocaleLowerCase();
    for (let cmd of commands.list)
        if (command.match(cmd.pattern))
            return cmd.action(message, args).catch(e => console.error(e));
    message.channel.send(`No command found matching '${command}'`);
};

commands.init = cl => {
    client = cl;
    client.on('message', message => commands.onMessage(message));
    return commands;
};
module.exports = commands;
commands.list = [];

function addCommand(name, action) {
    commands.list.push({name: name.name, pattern: name.pattern || name.name, action: action});
}

addCommand({name: "cmds"}, async (message, args) => {
    message.channel.send(new discord.RichEmbed()
        .setTitle("Commands")
        .setDescription(commands.list.map(cmd => cmd.name).join('\n')));
});

addCommand({name: "inforaw"}, async (message, args) => {
    let embed = new discord.RichEmbed()
        .setTitle("config.json")
        .setColor("GRAY")
        .setDescription(util.inspect(config).substr(0, 2048));
    message.channel.send(embed);
});

addCommand({name: "eval", pattern: "(?:exec|eval)"}, async (message, args) => {
    try {
        message.channel.send(new discord.RichEmbed().setDescription(`>${util.inspect(eval(args.join(" "))).substr(0, 2047)}`));
    } catch (error) {
        message.channel.send(new discord.RichEmbed().setDescription(`Error:${error}`));
    }
});

addCommand({name: "setraw"}, async (message, args) => {
    try {
        config[args.shift()] = eval(args.join(" "));
        commands.writeConfig();
        message.channel.send(new discord.RichEmbed().setColor("GREEN").setDescription(`The config file has been updated.`));
    } catch (error) {
        message.channel.send(`Error: ${error}`);
    }
});

addCommand({"name": "set"}, async (message, args) => {
    switch (args.shift().toLowerCase()) {
        case "channel":
            let channel = commands.getChannel(args.join(" "));
            if (channel === null)
                return message.channel.send("Unable to find the channel specified.");
            config.report_channel_id = channel.id;
            commands.writeConfig();
            message.channel.send(new discord.RichEmbed().setColor("GREEN").setDescription(`The config file has been updated.`));
            break;
        case "role":
            let role = commands.getChannel(args.join(" "));
            if (role === null)
                return message.channel.send("Unable to find the role specified.");
            config.mute_role_id = role.id;
            commands.writeConfig();

            message.channel.send(new discord.RichEmbed().setColor("GREEN").setDescription(`The config file has been updated.`));
            break;
    }
});

addCommand({name: "patterns"}, async (message, args) => {
    switch (args.shift().toLowerCase()) {
        case "add":
            if (config.patterns.includes(args.join(" ")))
                return message.channel.send(new discord.RichEmbed().setColor("ORANGE").setDescription(`That pattern is already in the list.`));
            config.patterns.push(args.join(" "));
            commands.writeConfig();
            message.channel.send(new discord.RichEmbed().setColor("GREEN").setDescription(`The pattern list has been updated.`));
            break;
        case "remove":
            let r = parseInt(args[0]);
            if (!config.patterns[r])
                return message.channel.send(new discord.RichEmbed().setColor("ORANGE").setDescription(`The given index is invalid.`));
            config.patterns.splice(r, 1);
            commands.writeConfig();
            message.channel.send(new discord.RichEmbed().setColor("GREEN").setDescription(`The pattern has been removed from the list.`));
            break;
        case "list":
            let i, j, chunk, chunkSize = 10;
            let pages = [];
            for (i = 0, j = config.patterns.length; i < j; i += chunkSize) {
                chunk = config.patterns.slice(i, i + chunkSize);
                let embed = new discord.RichEmbed()
                    .setTitle("Patterns")
                    .setColor("BLACK")
                    .setDescription("")
                    .setFooter(`Page ${pages.length + 1} of ${Math.floor(config.patterns.length / chunkSize) + 1}`);
                for (let role in chunk)
                    embed.description += `**${role}**: \`${chunk[role]}\`\n`;
                pages.push(embed);
            }
            let index = (parseInt(args[0]) || 1) - 1;
            let msg = await message.channel.send(pages[index]);
            commands.createPaginator(message, msg,
                () => {
                    index = ++index >= pages.length ? 0 : index;
                    msg.edit(pages[index]);
                },
                () => {
                    index = --index < 0 ? pages.length - 1 : index;
                    msg.edit(pages[index]);
                }
            );
            break;
    }
});
