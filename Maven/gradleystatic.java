package xyz.minetex.quora.maven.bukkit;

import xyz.minetex.quora.maven.bukkit.command.CrackedCommand;
import xyz.minetex.quora.maven.bukkit.command.PremiumCommand;
import xyz.minetex.quora.maven.bukkit.listener.BungeeListener;
import xyz.minetex.quora.maven.bukkit.listener.ConnectionListener;
import xyz.minetex.quora.maven.bukkit.listener.protocollib.ProtocolLibListener;
import xyz.minetex.quora.maven.bukkit.listener.protocollib.SkinApplyListener;
import xyz.minetex.quora.maven.bukkit.listener.protocolsupport.ProtocolSupportListener;
import xyz.minetex.quora.maven.bukkit.task.DelayedAuthHook;
import xyz.minetex.quora.maven.core.bukkit.CommonUtil;
import xyz.minetex.quora.maven.core.bukkit.PremiumStatus;
import xyz.minetex.quora.maven.core.bukkit.message.ChannelMessage;
import xyz.minetex.quora.maven.core.bukkit.message.LoginActionMessage;
import xyz.minetex.quora.maven.core.bukkit.message.NamespaceKey;
import xyz.minetex.quora.maven.core.bukkit.shared.FastLoginCore;
import xyz.minetex.quora.maven.core.bukkit.shared.PlatformPlugin;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageRecipient;
import org.slf4j.Logger;

import static xyz.minetex.quora.maven.core.bukkit.message.ChangePremiumMessage.CHANGE_CHANNEL;
import static xyz.minetex.quora.maven.core.bukkit.message.SuccessMessage.SUCCESS_CHANNEL;

/**
 * This plugin checks if a player has a paid account and if so tries to skip offline mode authentication.
 */
public class FastLoginBukkit extends JavaPlugin implements PlatformPlugin<CommandSender> {

    //1 minutes should be enough as a timeout for bad internet connection (Server, Client and Mojang)
    private final ConcurrentMap<String, BukkitLoginSession> loginSession = CommonUtil.buildCache(1, -1);
    private final Logger logger = CommonUtil.createLoggerFromJDK(getLogger());
    private final Map<UUID, PremiumStatus> premiumPlayers = new ConcurrentHashMap<>();

    private boolean serverStarted;
    private boolean bungeeCord;
    private FastLoginCore<Player, CommandSender, FastLoginBukkit> core;

    @Override
    public void onEnable() {
        core = new FastLoginCore<>(this);
        core.load();
        try {
            bungeeCord = Class.forName("org.spigotmc.SpigotConfig").getDeclaredField("bungee").getBoolean(null);
        } catch (ClassNotFoundException notFoundEx) {
            //ignore server has no bungee support
        } catch (Exception ex) {
            logger.warn("Cannot check bungeecord support. You use a non-Spigot build", ex);
        }

        if (getServer().getOnlineMode()) {
            //we need to require offline to prevent a loginSession request for a offline player
            logger.error("Server have to be in offline mode");
            setEnabled(false);
            return;
        }

        PluginManager pluginManager = getServer().getPluginManager();
        if (bungeeCord) {
            setServerStarted();

            // check for incoming messages from the bungeecord version of this plugin
            String forceChannel = new NamespaceKey(getName(), LoginActionMessage.FORCE_CHANNEL).getCombinedName();
            getServer().getMessenger().registerIncomingPluginChannel(this, forceChannel, new BungeeListener(this));

            // outgoing
            String successChannel = new NamespaceKey(getName(), SUCCESS_CHANNEL).getCombinedName();
            String changeChannel = new NamespaceKey(getName(), CHANGE_CHANNEL).getCombinedName();
            getServer().getMessenger().registerOutgoingPluginChannel(this, successChannel);
            getServer().getMessenger().registerOutgoingPluginChannel(this, changeChannel);
        } else {
            if (!core.setupDatabase()) {
                setEnabled(false);
                return;
            }

            if (pluginManager.isPluginEnabled("ProtocolSupport")) {
                pluginManager.registerEvents(new ProtocolSupportListener(this), this);
            } else if (pluginManager.isPluginEnabled("ProtocolLib")) {
                ProtocolLibListener.register(this);
                pluginManager.registerEvents(new SkinApplyListener(this), this);
            } else {
                logger.warn("Either ProtocolLib or ProtocolSupport have to be installed if you don't use BungeeCord");
            }
        }

        //delay dependency setup because we load the plugin very early where plugins are initialized yet
        getServer().getScheduler().runTaskLater(this, new DelayedAuthHook(this), 5L);

        pluginManager.registerEvents(new ConnectionListener(this), this);

        //register commands using a unique name
        getCommand("premium").setExecutor(new PremiumCommand(this));
        getCommand("cracked").setExecutor(new CrackedCommand(this));

        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            //prevents NoClassDef errors if it's not available
            PremiumPlaceholder.register(this);
        }
    }

    @Override
    public void onDisable() {
        loginSession.clear();
        premiumPlayers.clear();

        if (core != null) {
            core.close();
        }

        //remove old blacklists
        getServer().getOnlinePlayers().forEach(player -> player.removeMetadata(getName(), this));
    }

    public FastLoginCore<Player, CommandSender, FastLoginBukkit> getCore() {
        return core;
    }

    /**
     * Gets a thread-safe map about players which are connecting to the server are being checked to be premium (paid
     * account)
     *
     * @return a thread-safe loginSession map
     */
    public ConcurrentMap<String, BukkitLoginSession> getLoginSessions() {
        return loginSession;
    }

    public Map<UUID, PremiumStatus> getPremiumPlayers() {
        return premiumPlayers;
    }

    public boolean isBungeeEnabled() {
        return bungeeCord;
    }

    /**
     * Fetches the premium status of an online player.
     *
     * @param onlinePlayer
     * @return the online status or unknown if an error happened, the player isn't online or BungeeCord doesn't send
     * us the status message yet (This means you cannot check the login status on the PlayerJoinEvent).
     * @deprecated this method could be removed in future versions and exists only as a temporarily solution
     */
    @Deprecated
    public PremiumStatus getStatus(UUID onlinePlayer) {
        return premiumPlayers.getOrDefault(onlinePlayer, PremiumStatus.UNKNOWN);
    }

    /**
     * Wait before the server is fully started. This is workaround, because connections right on startup are not
     * injected by ProtocolLib
     *
     * @return true if ProtocolLib can now intercept packets
     */
    public boolean isServerFullyStarted() {
        return serverStarted;
    }

    public void setServerStarted() {
        if (!this.serverStarted) {
            this.serverStarted = true;
        }
    }

    public void sendPluginMessage(PluginMessageRecipient player, ChannelMessage message) {
        if (player != null) {
            ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
            message.writeTo(dataOutput);

            NamespaceKey channel = new NamespaceKey(getName(), message.getChannelName());
            player.sendPluginMessage(this, channel.getCombinedName(), dataOutput.toByteArray());
        }
    }

    @Override
    public Path getPluginFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public Logger getLog() {
        return logger;
    }

    @Override
    public void sendMessage(CommandSender receiver, String message) {
        receiver.sendMessage(message);
    }
}