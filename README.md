# Mute-Bot
A Java-based discord bot for muting based on pattern matching

# Setup
Running `java -jar mute-bot-version-all.jar` will load the bot.
On the first run, it will generate a `bot.properties` file with 4 fields:
 - REPORTCHANNEL: The ID of the channel where the bot will report when mutes are distributed
 - MUTETYPE: Determines whether the bot will use the given role to mute users, or permission overrides. Either 'perms' or 'role' are valid values
 - MUTEROLE: The ID of the role to be used when a user is muted. If this is not defined, MUTETYPE is ignored and permission overrides are used.
 - DISCORD_TOKEN: The token of the bot. Make sure this is the **TOKEN**, not the *secret*, otherwise a 401 error will be thrown when loading.

## Commands

#### /filter list
Displays the current list of filter patterns including list indices.

#### /filter add <pattern>
Adds a pattern to the filter list and updates the `patterns.txt` file.

#### /filter remove <index>
Removes the pattern at the given index from the filter list and updates the `patterns.txt` file.

#### /filter write
Overwrites `patterns.txt` with the current contents of the filter list.

#### /filter read
Overwrites the current filter list with the contents of `patterns.txt`.

#### /filter presets
Adds default patterns to the filter list, but does not update `patterns.txt`.

#### /filter clear
Clears the filter list. This does *not* update `patterns.txt`.

#### /filter help
Displays the list of supported commands. Not as informative as this readme.

#### /filter mute <@mention>
Manually applies the mute operation to the mentioned user.

#### /filter unmute <@mention>
Manually unmutes the mentioned user.

#### /filter setrole <@role>
Sets the 'muted user' role and writes it to `bot.properties`.

#### /filter getrole
Displays the current role used for muting.

#### /filter setmutetype <role|perms>
Sets whether the bot will override permissions or give roles to mute people, and writes the change to `bot.properties`.


##Notes
Even if the bot is set to use a role to mute people, if the role is not defined, it will use permission overrides instead.
The channel where the notifications appear when the bot mutes people is defined in `bot.properties`, and is the id of the desired channel.
Currently, the channel is retrieved via id from the guild where the infraction is performed, but if the bot were to be ran on two servers, this behaviour will need to be updated.

Occasionally the bot will throw an exception involving webhooks when the jar is loaded, I'm not sure why but it doesn't seem to impact the operation of the bot.