# Using PlaceholderAPI[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#using-placeholderapi "Permanent link")

This page is about using PlaceholderAPI in your own plugin, to either let other plugins use your plugin, or just use placeholders from other plugins in your own.

Please note, that the examples in this page are only available for **PlaceholderAPI 2.10.0 (1.0.0 for Hytale version) or newer**!

## First steps[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#first-steps "Permanent link")

### Add PlaceholderAPI to your Project[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#add-placeholderapi-to-your-project "Permanent link")

Before you can actually make use of PlaceholderAPI, you first have to import it into your project.  
Use the below code example matching your project type and dependency manager.

[Minecraft (Spigot, Paper, ...)](https://wiki.placeholderapi.com/developers/using-placeholderapi/#minecraft-spigot-paper-)[Hytale](https://wiki.placeholderapi.com/developers/using-placeholderapi/#hytale)

 [Maven](https://wiki.placeholderapi.com/developers/using-placeholderapi/#maven) [Gradle](https://wiki.placeholderapi.com/developers/using-placeholderapi/#gradle)

build.gradle

`repositories {     maven {         url = 'https://repo.extendedclip.com/releases/'     } }  dependencies {     compileOnly 'me.clip:placeholderapi:2.12.2' }`

What is `{papiVersion}`/`{papiHytaleVersion}`?

Using Javascript, `{papiVersion}` and `{papiHytaleVersion}` is replaced with the latest available API version of PlaceholderAPI for Minecraft and Hytale respectively.  
Should you see the placeholder as-is does it mean that you either block Javascript, or that the version couldn't be obtained in time during page load.

You can always find the latest version matching the API version on the [releases tab](https://github.com/PlaceholderAPI/PlaceholderAPI/releases) of the GitHub Repository.

### Set PlaceholderAPI as (soft)depend[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#set-placeholderapi-as-softdepend "Permanent link")

Next step is to go to your plugin.yml or paper-plugin.yml and add PlaceholderAPI as a depend or softdepend, depending (no pun intended) on if it is optional or not.

 [plugin.yml](https://wiki.placeholderapi.com/developers/using-placeholderapi/#pluginyml) [paper-plugin.yml](https://wiki.placeholderapi.com/developers/using-placeholderapi/#paper-pluginyml)[manifest.json (Hytale)](https://wiki.placeholderapi.com/developers/using-placeholderapi/#manifestjson-hytale)

[Optional dependency](https://wiki.placeholderapi.com/developers/using-placeholderapi/#optional-dependency)[Required dependency](https://wiki.placeholderapi.com/developers/using-placeholderapi/#required-dependency)

Tab the  icons in the code block below for additional information.

plugin.yml

`name: ExamplePlugin version: 1.0 author: author main: com.example.plugin.ExamplePlugin  softdepend: ["PlaceholderAPI"] #` 

## Adding placeholders to PlaceholderAPI[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#adding-placeholders-to-placeholderapi "Permanent link")

A full guide on how to create expansions can be found on the [Creating a PlaceholderExpansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/) page.

## Setting placeholders in your plugin[¶](https://wiki.placeholderapi.com/developers/using-placeholderapi/#setting-placeholders-in-your-plugin "Permanent link")

PlaceholderAPI offers the ability, to automatically parse placeholders from other plugins within your own plugin, giving the ability for your plugin to support thousands of other placeholders without depending on each plugin individually.  
To use placeholders from other plugins in your own plugin, you simply have to [(soft)depend on PlaceholderAPI](https://wiki.placeholderapi.com/developers/using-placeholderapi/#set-placeholderapi-as-softdepend) and use the `setPlaceholders` method.

It is also important to point out, that any required plugin/dependency for an expansion has to be on the server and enabled, or the `setPlaceholders` method will just return the placeholder itself (do nothing).

New since 2.12.0

Starting with version 2.12.0 is it now possible to provide Components from the Adventure library to have placeholders parsed in.

In order to use this new feature are the following things required to be true:

- Your plugin runs on a Paper-based Server. Spigot-based servers will not work!
- You use `PAPIComponent` instead of `PlaceholderAPI` to parse Components.

[Spigot, Paper, ...](https://wiki.placeholderapi.com/developers/using-placeholderapi/#spigot-paper-)[Hytale](https://wiki.placeholderapi.com/developers/using-placeholderapi/#hytale_1)

The following is an example plugin that sends `%player_name% joined the server! They are rank %vault_rank%` as the Join message, having the placeholders be replaced by PlaceholderAPI.

The below example assumes a **soft dependency** on PlaceholderAPI to handle PlaceholderAPI not being present more decently.

Tab the  icons in the code block below for additional information.

JoinExample.java

`package com.example.plugin;  import me.clip.placeholderapi.PlaceholderAPI;  import org.bukkit.Bukkit; import org.bukkit.event.EventHandler; import org.bukkit.event.EventPriority; import org.bukkit.event.Listener; import org.bukkit.event.player.PlayerJoinEvent; import org.bukkit.plugin.java.JavaPlugin; import me.clip.placeholderapi.PlaceholderAPI;  public class JoinExample extends JavaPlugin implements Listener {      @Override     public void onEnable() {          if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {             Bukkit.getPluginManager().registerEvents(this, this); //          } else {             getLogger().warn("Could not find PlaceholderAPI! This plugin is required."); //              Bukkit.getPluginManager().disablePlugin(this);         }     }      @EventHandler(priority = EventPriority.HIGHEST)     public void onJoin(PlayerJoinEvent event) {         String joinText = "%player_name% joined the server! They are rank %vault_rank%";          joinText = PlaceholderAPI.setPlaceholders(event.getPlayer(), joinText); //           event.setJoinMessage(joinText);     } }`


# Creating a PlaceholderExpansion[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#creating-a-placeholderexpansion "Permanent link")

Important

These pages cover the creation of a PlaceholderExpansion for both Spigot/Paper-based and Hytale Servers!

Unless mentioned otherwise the provided code examples function for both platform types.  
Please always check code blocks for  Icons with additional info!

This page will cover how you can create your own [`PlaceholderExpansion`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/PlaceholderExpansion.java) which you can either integrate into your own plugin (Recommended) or [upload to the eCloud](https://wiki.placeholderapi.com/developers/expansion-cloud/).

It's worth noting that PlaceholderAPI relies on expansions being installed. PlaceholderAPI only acts as the core replacing utility while the expansions allow other plugins to use any installed placeholder in their own messages.  
You can download expansions either directly from the eCloud yourself, or download them through the [download command of PlaceholderAPI](https://wiki.placeholderapi.com/users/commands/#papi-ecloud-download).

## Table of contents[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#table-of-contents "Permanent link")

- [Getting started](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#getting-started)
    - [Common Expansion Parts](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#common-expansion-parts)
- [Making an Internal Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-internal-expansion)
    - [Full Example](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#full-example-internal)
    - [Register your Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#register-your-expansion)
- [Making an External Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-external-expansion)
    - [Full Example (Without Dependency)](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#full-example-external-no-dependency)
    - [Full Example (With Dependency)](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#full-example-external-dependency)
- [Making a relational Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-a-relational-expansion)
    - [Full Example](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#full-example-relational)

## Getting started[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#getting-started "Permanent link")

For starters, you need to decide what type of [`PlaceholderExpansion`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/PlaceholderExpansion.java) you want to create. There are various ways to create an expansion. This page will cover the most common ones.

### Common Expansion Parts[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#common-expansion-parts "Permanent link")

All shown examples will share the same common parts that belong the the [`PlaceholderExpansion`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/PlaceholderExpansion.java) class.  
In order to not repeat the same basic info for each method throughout this page, and to greatly reduce the overall length, we will cover the most basic/necessary ones here.

#### Basic PlaceholderExpansion Structure[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#basic-placeholderexpansion-structure "Permanent link")

Tab the  icons in the code block below for additional information.

SomeExpansion.java

`package at.helpch.placeholderapi.example.expansion;  import me.clip.placeholderapi.expansion.PlaceholderExpansion;  public class SomeExpansion extends PlaceholderExpansion {      @Override     @NotNull     public String getAuthor() {         return "Author"; //      }      @Override     @NotNull     public String getIdentifier() {         return "example"; //      }      @Override     @NotNull     public String getVersion() {         return "1.0.0"; //      }      // These methods aren't overriden by default.     // You have to override one of them.      @Override     public String onRequest(OfflinePlayer player, @NotNull String params) {         //      }      @Override     public String onPlaceholderRequest(Player player, @NotNull String params) {         //      }      @Override     public String onPlaceholderRequest(PlayerRef player, @NotNull String params) {         //      } }`

Note

Overriding `onRequest(OfflinePlayer, String)` or `onPlaceholderRequest(Player, String)` is not required if you [create relational placeholders](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-a-relational-expansion).

---

## Making an Internal Expansion[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-internal-expansion "Permanent link")

Internal PlaceholderExpansions are classes directly integrated in the plugin they depend on.  
This method of creating a PlaceholderExpansion is recommended as it has the following benefits:

- No `canRegister()` method override required. Since your expansion is part of the plugin it depends on is this override not required.
- Easier access to plugin data. Using dependency injection, you can more easily access data of your plugin such as config values.

Important!

Internal PlaceholderExpansions are not automatically registered by PlaceholderAPI, due to them not being a separate jar file located in the expansion folder.  
Please see the [Regsister your Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#register-your-expansion) section for more details.

You are also required to override and set `persist()` to `true`. This tells PlaceholderAPI to not unload your expansion during plugin reload, as it would otherwise unregister your expansion, making it no longer work.

Full Example

Important Notes

- Please see the [Basic PlaceholderExpansion Structure](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#basic-placeholderexpansion-structure) section for an explanation of all common methods in this example.
- The below example is for a Spigot/Paper-based setup.  
    For a Hytale server, replace `me.clip` imports with `at.helpch` and replace `OfflinePlayer` with `PlayerRef` (Including the import).

Tab the  icons in the code block below for additional information.

SomeExpansion.java

`package com.example.plugin.expansion;  import com.example.plugin.SomePlugin; import me.clip.placeholderapi.expansion.PlaceholderExpansion; import org.bukkit.OfflinePlayer; import org.jetbrains.annotations.NotNull;  public class SomeExpansion extends PlaceholderExpansion {      private final SomePlugin plugin; //       public SomeExpansion(SomePlugin plugin) {         this.plugin = plugin;     }      @Override     @NotNull     public String getAuthor() {         return String.join(", ", plugin.getDescription().getAuthors()); //      }      @Override     @NotNull     public String getIdentifier() {         return "example";     }      @Override     @NotNull     public String getVersion() {         return plugin.getDescription().getVersion(); //      }      @Override     public boolean persist() {         return true; //      }      @Override     public String onRequest(OfflinePlayer player, @NotNull String params) {         if (params.equalsIgnoreCase("placeholder1")) {             return plugin.getConfig().getString("placeholders.placeholder1", "default1"); //          }          if (params.equalsIgnoreCase("placeholder2")) {             return plugin.getConfig().getString("placeholders.placeholder1", "default1"); //          }          return null; //      } }`

### Register your Expansion[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#register-your-expansion "Permanent link")

Due to the PlaceholderExpansion being internal, PlaceholderAPI does not load it automatically, we'll need to do it manually.  
This is being done by creating a new instance of your PlaceholderExpansion class and calling the `register()` method of it:

[Spigot, Paper, ...](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#spigot-paper-)[Hytale](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#hytale)

SomePlugin.java

`package com.example.plugin;  import com.example.plugin.expansion.SomeExpansion; import org.bukkit.Bukkit; import org.bukkit.plugin.java.JavaPlugin;  public class SomePlugin extends JavaPlugin {      @Override     public void onEnable() {         if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) { //              new SomeExpansion(this).register(); //          }     } }`

---

## Making an External Expansion[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-external-expansion "Permanent link")

External Expansions are separate Jar files located inside PlaceholderAPI's `expansions` folder, that contain the [`PlaceholderExpansion`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/PlaceholderExpansion.java) extending class.  
It is recommended to only make external Expansions for the following situations.

- Your expansion does not rely on a plugin.
- Your expansion depends on a plugin and you can't directly include it (Plugin is not your own).

Should the above cases not match your situation, meaning your expansion is for a plugin you maintain, is the creation of an [internal Expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-internal-expansion) recommended.

Some benefits of an external expansion include automatic (re)loading of your expansion by PlaceholderAPI and having the option to [upload it to the eCloud](https://wiki.placeholderapi.com/developers/expansion-cloud/) allowing the download of it through the [`/papi ecloud download` command](https://wiki.placeholderapi.com/users/commands/#papi-ecloud-download).  
Downsides include a more tedious setup in terms of checking for a required plugin being present.

Full Example (Without Dependency)

Important Notes

- Please see the [Basic PlaceholderExpansion Structure](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#basic-placeholderexpansion-structure) section for an explanation of all common methods in this example.
- The below example is for a Spigot/Paper-based setup.  
    For a Hytale server, replace `me.clip` imports with `at.helpch` and replace `OfflinePlayer` with `PlayerRef` (Including the import).

Tab the  icons in the code block below for additional information.

This is an example expansion without any plugin dependency.

SomeExpansion.java

`package com.example.expansion;  import me.clip.placeholderapi.expansion.PlaceholderExpansion; import org.bukkit.OfflinePlayer; import org.jetbrains.annotations.NotNull;  public class SomeExpansion extends PlaceholderExpansion {      @Override     @NotNull     public String getAuthor() {         return "Author";     }      @Override     @NotNull     public String getIdentifier() {         return "example";     }      @Override     @NotNull     public String getVersion() {         return "1.0.0";     }      @Override     public String onRequest(OfflinePlayer player, @NotNull String params) {         if (params.equalsIgnoreCase("placeholder1")) {             return "text1";         }          if (params.equalsIgnoreCase("placeholder2")) {             return "text2";         }          return null; //      } }`
Full Example (With Dependency)

Important Notes

- Please see the [Basic PlaceholderExpansion Structure](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#basic-placeholderexpansion-structure) section for an explanation of all common methods in this example.
- The below example is for a Spigot/Paper-based setup.  
    For a Hytale server, replace `me.clip` imports with `at.helpch` and replace `OfflinePlayer` with `PlayerRef` (Including the import).

Tab the  icons in the code block below for additional information.

This is an example expansion with a plugin dependency.

SomeExpansion.java

`package com.example.expansion;  import com.example.plugin.SomePlugin; import me.clip.placeholderapi.expansion.PlaceholderExpansion; import org.bukkit.Bukkit; import org.bukkit.OfflinePlayer; import org.jetbrains.annotations.NotNull;  public class SomeExpansion extends PlaceholderExpansion {      private SomePlugin plugin; //       @Override     @NotNull     public String getAuthor() {         return "Author";     }      @Override     @NotNull     public String getIdentifier() {         return "example";     }      @Override     @NotNull     public String getVersion() {         return "1.0.0"     }      @Override     public String getRequiredPlugin() {         return "SomePlugin"; //      }      @Override     public boolean canRegister() { //          return (plugin = (SomePlugin) Bukkit.getPluginManager().getPlugin(getRequiredPlugin())) != null;     }      @Override     public String onRequest(OfflinePlayer player, @NotNull String params) {         if (params.equalsIgnoreCase("placeholder1")) {             return plugin.getConfig().getString("placeholders.placeholder1", "default1"); //          }          if (params.equalsIgnoreCase("placeholder2")) {             return plugin.getConfig().getString("placeholders.placeholder1", "default1"); //          }          return null; //      } }`

---

## Making a relational Expansion[¶](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-a-relational-expansion "Permanent link")

Notes

- Relational Placeholders always start with `rel_` to properly identify them. This means that if you make a relational placeholder called `friends_is_friend` would the full placeholder be `%rel_friends_is_friend%`.
- For Hytale, replace any mention of `Player` with `PlayerRef` and update any Imports in the code to `at.helpch` and related Hytale ones.

Relational PlaceholderExpansions are special in that they take two players as input, allowing you to give outputs based on their relation to each other.

To create a relational expansion you will need to implement the [`Relational`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/Relational.java) interface into your expansion. You also still need to extend the [`PlaceholderExpansion`](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/src/main/java/me/clip/placeholderapi/expansion/PlaceholderExpansion.java) class.  
Implementing this interface will add the `onPlaceholderRequest(Player, Player, String)` with the first two arguments being the first and second player to use and the third argument being the content after the second `_` and before the final `%` (Or `}` if bracket placeholders are used) in the placeholder.

Full Example

Please see the [Basic PlaceholderExpansion Structure](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#basic-placeholderexpansion-structure) section for an explanation of all common methods in this example.

Tab the  icons in the code block below for additional information.

This is a complete example of using relational placeholders.  
For the sake of simplicity are we using the [internal Expansion setup](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#making-an-internal-expansion) here and assume that `SomePlugin` offers a `areFriends(Player, Player)` method that returns true or false based on if the players are friends or not.

SomeExpansion.java

`package at.helpch.placeholderapi.example.expansion;  import at.helpch.placeholderapi.example.SomePlugin; import me.clip.placeholderapi.expansion.PlaceholderExpansion; import me.clip.placeholderapi.expansion.Relational import org.bukkit.ChatColor; import org.bukkit.Player; import org.jetbrains.annotations.NotNull;  public class SomeExpansion extends PlaceholderExpansion implements Relational {      private final SomePlugin plugin; //       public SomeExpansion(SomePlugin plugin) {         this.plugin = plugin;     }      @Override     @NotNull     public String getAuthor() {         return String.join(", ", plugin.getDescription().getAuthors()); //      }      @Override     @NotNull     public String getIdentifier() {         return "example";     }      @Override     @NotNull     public String getVersion() {         return plugin.getDescription().getVersion(); //      }      @Override     public boolean persist() {         return true; //      }      @Override     public String onPlaceholderRequest(Player one, Player two, String identifier) {         if (one == null || two == null) {             return null; //          }          if (identifier.equalsIgnoreCase("friends")) { //              if (plugin.areFriends(one, two)) {                 return ChatColor.GREEN + one.getName() + " and " + two.getName() + " are friends!";             } else {                 return ChatColor.RED + one.getName() + " and " + two.getName() + " are not friends!";             }         }          return null; //      } }`

Don't forget to [register your expansion](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#register-your-expansion).

 Back to top

© 2015 - 2025 PlaceholderAPI Team and Contributors.  
All content is licensed under  [GNU GPL v3.0](https://github.com/PlaceholderAPI/PlaceholderAPI/blob/master/LICENSE) unless stated otherwise.  
  
 [Manage Cookies](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/#__consent)

Made with [MkDocs](https://www.mkdocs.org/) , [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) and [PyMdown Extensions](https://facelessuser.github.io/pymdown-extensions/)

[](https://modrinth.com/plugin/placeholderapi "modrinth.com")[](https://hangar.papermc.io/HelpChat/PlaceholderAPI "hangar.papermc.io")[](https://www.spigotmc.org/resources/6245/ "www.spigotmc.org")[](https://discord.gg/helpchat "discord.gg")
