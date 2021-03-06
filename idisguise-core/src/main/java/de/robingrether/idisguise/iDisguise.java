package de.robingrether.idisguise;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
//import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.bstats.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import de.robingrether.idisguise.api.DisguiseAPI;
import de.robingrether.idisguise.api.DisguiseEvent;
import de.robingrether.idisguise.api.OfflinePlayerDisguiseEvent;
import de.robingrether.idisguise.api.OfflinePlayerUndisguiseEvent;
import de.robingrether.idisguise.api.UndisguiseEvent;
import de.robingrether.idisguise.disguise.AgeableDisguise;
import de.robingrether.idisguise.disguise.CreeperDisguise;
import de.robingrether.idisguise.disguise.Disguise;
import de.robingrether.idisguise.disguise.Disguise.Visibility;
import de.robingrether.idisguise.disguise.DisguiseType;
import de.robingrether.idisguise.disguise.DisguiseType.Type;
import de.robingrether.idisguise.disguise.EndermanDisguise;
import de.robingrether.idisguise.disguise.FallingBlockDisguise;
import de.robingrether.idisguise.disguise.SheepDisguise;
import de.robingrether.idisguise.disguise.OcelotDisguise;
import de.robingrether.idisguise.disguise.OutdatedServerException;
import de.robingrether.idisguise.disguise.PigDisguise;
import de.robingrether.idisguise.disguise.PlayerDisguise;
import de.robingrether.idisguise.disguise.RabbitDisguise;
import de.robingrether.idisguise.disguise.SizedDisguise;
import de.robingrether.idisguise.disguise.Subtypes;
import de.robingrether.idisguise.disguise.VillagerDisguise;
import de.robingrether.idisguise.disguise.WolfDisguise;
import de.robingrether.idisguise.io.Configuration;
import de.robingrether.idisguise.io.Language;
import de.robingrether.idisguise.io.SLAPI;
import de.robingrether.idisguise.io.UpdateCheck;
import de.robingrether.idisguise.management.ChannelInjector;
import de.robingrether.idisguise.management.DisguiseManager;
import de.robingrether.idisguise.management.GhostFactory;
import de.robingrether.idisguise.management.PacketHelper;
import de.robingrether.idisguise.management.PlayerHelper;
import de.robingrether.idisguise.management.Reflection;
import de.robingrether.idisguise.management.Sounds;
import de.robingrether.idisguise.management.VersionHelper;
import de.robingrether.util.ObjectUtil;
import de.robingrether.util.RandomUtil;
import de.robingrether.util.StringUtil;
import de.robingrether.util.Validate;

public class iDisguise extends JavaPlugin {
	
	private static iDisguise instance;
	
	private EventListener listener;
	private Configuration configuration;
	private Language language;
	private Metrics metrics;
	private boolean enabled = false;
	
	public iDisguise() { instance = this; }
	
	public void onEnable() {
		if(!VersionHelper.init(false)) {
			getLogger().log(Level.SEVERE, String.format("%s is not compatible with your server version!", getFullName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		checkDirectory();
		listener = new EventListener(this);
		configuration = new Configuration(this);
		configuration.loadData();
		configuration.saveData();
		language = new Language(this);
		language.loadData();
		language.saveData();
		PacketHelper.getInstance().setAttribute(0, configuration.NAME_TAG_SHOWN);
		PacketHelper.getInstance().setAttribute(1, configuration.MODIFY_PLAYER_LIST_ENTRY);
		Sounds.setEnabled(configuration.REPLACE_SOUND_EFFECTS);
		metrics = new Metrics(this);
		metrics.addCustomChart(new Metrics.SingleLineChart("disguisedPlayers") {
			
			public int getValue() {
				return DisguiseManager.getInstance().getNumberOfDisguisedPlayers();
			}
			
		});
//		# not available so far
//		metrics.addCustomChart(new Metrics.MultiLineChart("usedFeatures") {
//			
//			public HashMap<String, Integer> getValues(HashMap<String, Integer> valueMap) {
//				valueMap.put("update checking", configuration.UPDATE_CHECK ? 1 : 0);
//				valueMap.put("realistic sounds", configuration.REPLACE_SOUND_EFFECTS ? 1 : 0);
//				valueMap.put("undisguise permission", configuration.UNDISGUISE_PERMISSION ? 1 : 0);
//				valueMap.put("ghost disguises", configuration.ENABLE_GHOST_DISGUISE ? 1 : 0);
//				valueMap.put("automatic updates", configuration.UPDATE_DOWNLOAD ? 1 : 0);
//				return valueMap;
//			}
//			
//		});
		metrics.addCustomChart(new Metrics.SimplePie("storageType") {
			
			public String getValue() {
				return configuration.KEEP_DISGUISE_SHUTDOWN ? "file" : "none";
			}
			
		});
		if(configuration.KEEP_DISGUISE_SHUTDOWN) {
			loadData();
		}
		getServer().getPluginManager().registerEvents(listener, this);
		if(configuration.ENABLE_GHOST_DISGUISE) {
			GhostFactory.getInstance().enable(this);
		}
		getServer().getServicesManager().register(DisguiseAPI.class, getAPI(), this, ServicePriority.Normal);
		if(configuration.UPDATE_CHECK) {
			getServer().getScheduler().runTaskLaterAsynchronously(this, new UpdateCheck(this, getServer().getConsoleSender(), configuration.UPDATE_DOWNLOAD), 20L);
		}
		Calendar today = Calendar.getInstance();
		if(today.get(Calendar.MONTH) == Calendar.NOVEMBER && today.get(Calendar.DAY_OF_MONTH) == 6) {
			getLogger().log(Level.INFO, String.format("YAAAY!!! Today is my birthday! I'm %s years old now.",  today.get(Calendar.YEAR) - 2012));
		}
		getLogger().log(Level.INFO, String.format("%s enabled!", getFullName()));
		enabled = true;
		for(Player player : Reflection.getOnlinePlayers()) {
			PlayerHelper.getInstance().addPlayer(player);
			PlayerHelper.getInstance().loadGameProfileAsynchronously(player.getName());
		}
		ChannelInjector.getInstance().injectOnlinePlayers();
		DisguiseManager.getInstance().resendPackets();
	}
	
	public void onDisable() {
		if(!enabled) {
			return;
		}
		if(configuration.ENABLE_GHOST_DISGUISE) {
			GhostFactory.getInstance().disable();
		}
		getServer().getScheduler().cancelTasks(this);
		if(configuration.KEEP_DISGUISE_SHUTDOWN) {
			saveData();
		}
		ChannelInjector.getInstance().removeOnlinePlayers();
		getLogger().log(Level.INFO, String.format("%s disabled!", getFullName()));
		enabled = false;
	}
	
	public void onReload() {
		if(!enabled) {
			return;
		}
		if(configuration.ENABLE_GHOST_DISGUISE) {
			GhostFactory.getInstance().disable();
		}
		if(configuration.KEEP_DISGUISE_SHUTDOWN) {
			saveData();
		}
		enabled = false;
		configuration.loadData();
		configuration.saveData();
		language.loadData();
		language.saveData();
		PacketHelper.getInstance().setAttribute(0, configuration.NAME_TAG_SHOWN);
		PacketHelper.getInstance().setAttribute(1, configuration.MODIFY_PLAYER_LIST_ENTRY);
		Sounds.setEnabled(configuration.REPLACE_SOUND_EFFECTS);
		if(configuration.KEEP_DISGUISE_SHUTDOWN) {
			loadData();
		}
		if(configuration.ENABLE_GHOST_DISGUISE) {
			GhostFactory.getInstance().enable(this);
		}
		enabled = true;
		DisguiseManager.getInstance().resendPackets();
	}
	
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
		if(StringUtil.equalsIgnoreCase(command.getName(), "disguise", "odisguise")) {
			if(args.length == 0) {
				sendHelpMessage(sender, command, alias);
			} else if(args[0].equalsIgnoreCase("reload")) {
				if(sender.hasPermission("iDisguise.reload")) {
					onReload();
					sender.sendMessage(language.RELOAD_COMPLETE);
				} else {
					sender.sendMessage(language.NO_PERMISSION);
				}
			} else {
				OfflinePlayer player = null;
				boolean disguiseSelf;
				if(command.getName().equalsIgnoreCase("disguise")) {
					if(sender instanceof Player) {
						player = (Player)sender;
						disguiseSelf = true;
					} else {
						sender.sendMessage(language.CONSOLE_USE_OTHER_COMMAND);
						return true;
					}
				} else if(args.length > 1) {
					if(sender.hasPermission("iDisguise.others")) {
						if(getServer().getPlayerExact(args[0]) != null) {
							player = getServer().getPlayerExact(args[0]);
						} else if(getServer().matchPlayer(args[0]).size() == 1) {
							player = getServer().matchPlayer(args[0]).get(0);
						} else if(getServer().getOfflinePlayer(args[0]) != null) {
							player = getServer().getOfflinePlayer(args[0]);
						} else {
							sender.sendMessage(language.CANNOT_FIND_PLAYER.replace("%player%", args[0]));
							return true;
						}
						disguiseSelf = false;
						args = Arrays.copyOfRange(args, 1, args.length);
					} else {
						sender.sendMessage(language.NO_PERMISSION);
						return true;
					}
				} else {
					sendHelpMessage(sender, command, alias);
					return true;
				}
				if(args[0].equalsIgnoreCase("help")) {
					sendHelpMessage(sender, command, alias);
				} else if(StringUtil.equalsIgnoreCase(args[0], "player", "p") || (configuration.ENABLE_GHOST_DISGUISE && StringUtil.equalsIgnoreCase(args[0], "ghost", "g"))) {
					if(args.length < 2) {
						sender.sendMessage(language.WRONG_USAGE_NO_NAME);
					} else if(!Validate.minecraftUsername(args[1].replaceAll("&[0-9a-fk-or]", ""))) {
						sender.sendMessage(language.INVALID_NAME);
					} else {
						PlayerDisguise disguise = new PlayerDisguise(args[1].replaceAll("&[0-9a-fk-or]", ""), args[1].replace('&', ChatColor.COLOR_CHAR), StringUtil.equalsIgnoreCase(args[0], "ghost", "g"));
						if(hasPermission(sender, disguise)) {
							if(player.isOnline()) {
								DisguiseEvent event = new DisguiseEvent(player.getPlayer(), disguise);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().disguise(player, disguise);
									sender.sendMessage((disguiseSelf ? language.DISGUISE_PLAYER_SUCCESS_SELF : language.DISGUISE_PLAYER_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()).replace("%name%", disguise.getDisplayName()));
								}
							} else {
								OfflinePlayerDisguiseEvent event = new OfflinePlayerDisguiseEvent(player, disguise);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().disguise(player, disguise);
									sender.sendMessage((disguiseSelf ? language.DISGUISE_PLAYER_SUCCESS_SELF : language.DISGUISE_PLAYER_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()).replace("%name%", disguise.getDisplayName()));
								}
							}
						} else {
							sender.sendMessage(language.NO_PERMISSION);
						}
					}
				} else if(args[0].equalsIgnoreCase("random")) {
					if(sender.hasPermission("iDisguise.random")) {
						Disguise disguise = (RandomUtil.nextBoolean() ? DisguiseType.random(Type.MOB) : DisguiseType.random(Type.OBJECT)).newInstance();
						if(player.isOnline()) {
							DisguiseEvent event = new DisguiseEvent(player.getPlayer(), disguise);
							getServer().getPluginManager().callEvent(event);
							if(event.isCancelled()) {
								sender.sendMessage(language.EVENT_CANCELLED);
							} else {
								DisguiseManager.getInstance().disguise(player, disguise);
								sender.sendMessage((disguiseSelf ? language.DISGUISE_SUCCESS_SELF : language.DISGUISE_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()));
							}
						} else {
							OfflinePlayerDisguiseEvent event = new OfflinePlayerDisguiseEvent(player, disguise);
							getServer().getPluginManager().callEvent(event);
							if(event.isCancelled()) {
								sender.sendMessage(language.EVENT_CANCELLED);
							} else {
								DisguiseManager.getInstance().disguise(player, disguise);
								sender.sendMessage((disguiseSelf ? language.DISGUISE_SUCCESS_SELF : language.DISGUISE_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()));
							}
						}
					} else {
						sender.sendMessage(language.NO_PERMISSION);
					}
				} else if(StringUtil.equalsIgnoreCase(args[0], "status", "state", "stats")) {
					if(DisguiseManager.getInstance().isDisguised(player)) {
						if(DisguiseManager.getInstance().getDisguise(player) instanceof PlayerDisguise) {
							PlayerDisguise disguise = (PlayerDisguise)DisguiseManager.getInstance().getDisguise(player);
							sender.sendMessage((disguiseSelf ? language.STATUS_PLAYER_SELF : language.STATUS_PLAYER_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()).replace("%name%", disguise.getDisplayName()));
							sender.sendMessage(language.STATUS_SUBTYPES.replace("%subtypes%", disguise.toString()));
						} else {
							Disguise disguise = DisguiseManager.getInstance().getDisguise(player);
							sender.sendMessage((disguiseSelf ? language.STATUS_SELF : language.STATUS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()));
							sender.sendMessage(language.STATUS_SUBTYPES.replace("%subtypes%", disguise.toString()));
						}
					} else {
						sender.sendMessage((disguiseSelf ? language.STATUS_NOT_DISGUISED_SELF : language.STATUS_NOT_DISGUISED_OTHER).replace("%player%", player.getName()));
					}
				} else {
					Disguise disguise = DisguiseManager.getInstance().isDisguised(player) ? DisguiseManager.getInstance().getDisguise(player).clone() : null;
					boolean match = false;
					for(String argument : args) {
						DisguiseType type = DisguiseType.Matcher.match(argument.toLowerCase(Locale.ENGLISH));
						if(type != null) {
							try {
								disguise = type.newInstance();
								match = true;
								break;
							} catch(OutdatedServerException e) {
								sender.sendMessage(language.OUTDATED_SERVER);
								return true;
							} catch(UnsupportedOperationException e) {
								sendHelpMessage(sender, command, alias);
								return true;
							}
						}
					}
					if(disguise != null) {
						for(String argument : args) {
							match |= Subtypes.applySubtype(disguise, argument);
						}
					}
					if(match) {
						if(hasPermission(sender, disguise)) {
							if(player.isOnline()) {
								DisguiseEvent event = new DisguiseEvent(player.getPlayer(), disguise);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().disguise(player, disguise);
									sender.sendMessage((disguiseSelf ? language.DISGUISE_SUCCESS_SELF : language.DISGUISE_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()));
								}
							} else {
								OfflinePlayerDisguiseEvent event = new OfflinePlayerDisguiseEvent(player, disguise);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().disguise(player, disguise);
									sender.sendMessage((disguiseSelf ? language.DISGUISE_SUCCESS_SELF : language.DISGUISE_SUCCESS_OTHER).replace("%player%", player.getName()).replace("%type%", disguise.getType().toString()));
								}
							}
						} else {
							sender.sendMessage(language.NO_PERMISSION);
						}
					} else {
						sendHelpMessage(sender, command, alias);
					}
				}
			}
		} else if(command.getName().equalsIgnoreCase("undisguise")) {
			if(args.length == 0) {
				if(sender instanceof Player) {
					if(DisguiseManager.getInstance().isDisguised((Player)sender)) {
						if(!configuration.UNDISGUISE_PERMISSION || sender.hasPermission("iDisguise.undisguise")) {
							UndisguiseEvent event = new UndisguiseEvent((Player)sender, DisguiseManager.getInstance().getDisguise((Player)sender), false);
							getServer().getPluginManager().callEvent(event);
							if(event.isCancelled()) {
								sender.sendMessage(language.EVENT_CANCELLED);
							} else {
								DisguiseManager.getInstance().undisguise((Player)sender);
								sender.sendMessage(language.UNDISGUISE_SUCCESS_SELF);
							}
						} else {
							sender.sendMessage(language.NO_PERMISSION);
						}
					} else {
						sender.sendMessage(language.UNDISGUISE_NOT_DISGUISED_SELF);
					}
				} else {
					sender.sendMessage(language.UNDISGUISE_CONSOLE);
				}
			} else if(args[0].equals("*")) {
				if(sender.hasPermission("iDisguise.undisguise.all")) {	
					if(args.length > 1 && args[1].equalsIgnoreCase("ignore")) {
						DisguiseManager.getInstance().undisguiseAll();
						sender.sendMessage(language.UNDISGUISE_SUCCESS_ALL_IGNORE);
					} else {
						Set<OfflinePlayer> disguisedPlayers = DisguiseManager.getInstance().getDisguisedPlayers();
						int share = 0, total = disguisedPlayers.size();
						for(OfflinePlayer player : disguisedPlayers) {
							if(player.isOnline()) {
								UndisguiseEvent event = new UndisguiseEvent(player.getPlayer(), DisguiseManager.getInstance().getDisguise(player), true);
								getServer().getPluginManager().callEvent(event);
								if(!event.isCancelled()) {
									DisguiseManager.getInstance().undisguise(player);
									share++;
								}
							} else {
								OfflinePlayerUndisguiseEvent event = new OfflinePlayerUndisguiseEvent(player, DisguiseManager.getInstance().getDisguise(player), true);
								getServer().getPluginManager().callEvent(event);
								if(!event.isCancelled()) {
									DisguiseManager.getInstance().undisguise(player);
									share++;
								}
							}
						}
						sender.sendMessage(language.UNDISGUISE_SUCCESS_ALL.replace("%share%", Integer.toString(share)).replace("%total%", Integer.toString(total)));
					}
				} else {
					sender.sendMessage(language.NO_PERMISSION);
				}
			} else {
				if(sender.hasPermission("iDisguise.undisguise.others")) {
					OfflinePlayer player = null;
					if(getServer().getPlayerExact(args[0]) != null) {
						player = getServer().getPlayerExact(args[0]);
					} else if(getServer().matchPlayer(args[0]).size() == 1) {
						player = getServer().matchPlayer(args[0]).get(0);
					} else if(getServer().getOfflinePlayer(args[0]) != null) {
						player = getServer().getOfflinePlayer(args[0]);
					} else {
						sender.sendMessage(language.CANNOT_FIND_PLAYER.replace("%player%", args[0]));
						return true;
					}
					if(DisguiseManager.getInstance().isDisguised(player)) {
						if(args.length > 1 && args[1].equalsIgnoreCase("ignore")) {
							DisguiseManager.getInstance().undisguise(player);
							sender.sendMessage(language.UNDISGUISE_SUCCESS_OTHER.replace("%player%", player.getName()));
						} else {
							if(player.isOnline()) {
								UndisguiseEvent event = new UndisguiseEvent(player.getPlayer(), DisguiseManager.getInstance().getDisguise(player), false);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().undisguise(player);
									sender.sendMessage(language.UNDISGUISE_SUCCESS_OTHER.replace("%player%", player.getName()));
								}
							} else {
								OfflinePlayerUndisguiseEvent event = new OfflinePlayerUndisguiseEvent(player, DisguiseManager.getInstance().getDisguise(player), false);
								getServer().getPluginManager().callEvent(event);
								if(event.isCancelled()) {
									sender.sendMessage(language.EVENT_CANCELLED);
								} else {
									DisguiseManager.getInstance().undisguise(player);
									sender.sendMessage(language.UNDISGUISE_SUCCESS_OTHER.replace("%player%", player.getName()));
								}
							}
						}
					} else {
						sender.sendMessage(language.UNDISGUISE_NOT_DISGUISED_OTHER.replace("%player%", player.getName()));
					}
				} else {
					sender.sendMessage(language.NO_PERMISSION);
				}
			}
		}
		return true;
	}
	
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<String>();
		if(command.getName().equalsIgnoreCase("disguise")) {
			if(sender instanceof Player) {
				Player player = (Player)sender;
				if(args.length < 2) {
					completions.addAll(Arrays.asList("help", "player", "status"));
					if(configuration.ENABLE_GHOST_DISGUISE) {
						completions.add("ghost");
					}
					if(sender.hasPermission("iDisguise.random")) {
						completions.add("random");
					}
					if(sender.hasPermission("iDisguise.reload")) {
						completions.add("reload");
					}
					for(DisguiseType type : DisguiseType.values()) {
						if(type.isAvailable() && !type.isPlayer()) {
							completions.add(type.getDefaultCommandArgument());
						}
					}
				}
				Disguise disguise = DisguiseManager.getInstance().isDisguised(player) ? DisguiseManager.getInstance().getDisguise(player).clone() : null;
				for(String argument : args) {
					DisguiseType type = DisguiseType.Matcher.match(argument.toLowerCase(Locale.ENGLISH));
					if(type != null) {
						try {
							disguise = type.newInstance();
							break;
						} catch(OutdatedServerException e) {
						} catch(UnsupportedOperationException e) {
						}
					}
				}
				if(disguise != null) {
					completions.addAll(Subtypes.listSubtypeArguments(disguise));
				}
			} else {
				completions.add("reload");
			}
		} else if(command.getName().equalsIgnoreCase("odisguise")) {
			if(args.length < 2) {
				if(sender.hasPermission("iDisguise.reload")) {
					completions.add("reload");
				}
				if(sender.hasPermission("iDisguise.others")) {
					for(Player player : Reflection.getOnlinePlayers()) {
						completions.add(player.getName());
					}
				}
			} else if(sender.hasPermission("iDisguise.others")) {
				OfflinePlayer player = null;
				if(getServer().getPlayerExact(args[0]) != null) {
					player = getServer().getPlayerExact(args[0]);
				} else if(getServer().matchPlayer(args[0]).size() == 1) {
					player = getServer().matchPlayer(args[0]).get(0);
				} else if(getServer().getOfflinePlayer(args[0]) != null) {
					player = getServer().getOfflinePlayer(args[0]);
				}
				if(player != null) {
					if(args.length < 3) {
						completions.addAll(Arrays.asList("help", "player", "status"));
						if(configuration.ENABLE_GHOST_DISGUISE) {
							completions.add("ghost");
						}
						if(sender.hasPermission("iDisguise.random")) {
							completions.add("random");
						}
						for(DisguiseType type : DisguiseType.values()) {
							if(!type.isPlayer() && type.isAvailable() && hasPermission(sender, type.newInstance())) {
								completions.add(type.getDefaultCommandArgument());
							}
						}
					}
					Disguise disguise = DisguiseManager.getInstance().isDisguised(player) ? DisguiseManager.getInstance().getDisguise(player).clone() : null;
					for(String argument : args) {
						DisguiseType type = DisguiseType.Matcher.match(argument.toLowerCase(Locale.ENGLISH));
						if(type != null) {
							try {
								disguise = type.newInstance();
								break;
							} catch(OutdatedServerException e) {
							} catch(UnsupportedOperationException e) {
							}
						}
					}
					if(disguise != null) {
						completions.addAll(Subtypes.listSubtypeArguments(disguise));
					}
				}
			}
		} else if(command.getName().equalsIgnoreCase("undisguise")) {
			if(args.length < 2) {
				if(sender.hasPermission("iDisguise.undisguise.all")) {
					completions.add("*");
				}
				if(sender.hasPermission("iDisguise.undisguise.others")) {
					for(Player player : Reflection.getOnlinePlayers()) {
						if(DisguiseManager.getInstance().isDisguised(player)) {
							completions.add(player.getName());
						}
					}
				}
			} else {
				completions.add("ignore");
			}
		}
		if(args.length > 0) {
			for(int i = 0; i < completions.size(); i++) {
				if(!StringUtil.startsWithIgnoreCase(completions.get(i), args[args.length - 1])) {
					completions.remove(i);
					i--;
				}
			}
		}
		return completions;
	}
	
	private void sendHelpMessage(CommandSender sender, Command command, String alias) {
		if(!sender.hasPermission("iDisguise.help")) {
			sender.sendMessage(language.NO_PERMISSION);
			return;
		}
		alias = alias.toLowerCase(Locale.ENGLISH);
		boolean self = command.getName().equalsIgnoreCase("disguise");
		String disguiseCommand = "/" + (self ? alias : alias + " <player>");
		String undisguiseCommand = "/" + alias.replaceAll("o?disguise$", "undisguise").replaceAll("o?dis$", "undis").replaceAll("o?d$", "ud");
		sender.sendMessage(language.HELP_INFO.replace("%name%", "iDisguise").replace("%version%", getVersion()));
		
		Calendar today = Calendar.getInstance();
		if(today.get(Calendar.MONTH) == Calendar.NOVEMBER && today.get(Calendar.DAY_OF_MONTH) == 6) {
			sender.sendMessage(language.EASTER_EGG_BIRTHDAY.replace("%age%", Integer.toString(today.get(Calendar.YEAR) - 2012)));
		}
		
		sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " help").replace("%description%", language.HELP_HELP));		
		sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " player <name>").replace("%description%", self ? language.HELP_PLAYER_SELF : language.HELP_PLAYER_OTHER));
		if(configuration.ENABLE_GHOST_DISGUISE) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " ghost <name>").replace("%description%", self ? language.HELP_GHOST_SELF : language.HELP_GHOST_OTHER));
		}
		if(sender.hasPermission("iDisguise.random")) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " random").replace("%description%", self ? language.HELP_RANDOM_SELF : language.HELP_RANDOM_OTHER));
		}
		if(sender.hasPermission("iDisguise.reload")) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", "/" + alias + " reload").replace("%description%", language.HELP_RELOAD));
		}
		sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " status").replace("%description%", self ? language.HELP_STATUS_SELF : language.HELP_STATUS_OTHER));
		if(self) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", undisguiseCommand).replace("%description%", language.HELP_UNDISGUISE_SELF));
		}
		if(sender.hasPermission("iDisguise.undisguise.all")) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", undisguiseCommand + " * [ignore]").replace("%description%", language.HELP_UNDISGUISE_ALL));
		}
		if(sender.hasPermission("iDisguise.undisguise.others")) {
			sender.sendMessage(language.HELP_BASE.replace("%command%", undisguiseCommand + " <player> [ignore]").replace("%description%", language.HELP_UNDISGUISE_OTHER));
		}
		sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " [subtype] <type> [subtype]").replace("%description%", self ? language.HELP_DISGUISE_SELF : language.HELP_DISGUISE_OTHER));
		sender.sendMessage(language.HELP_BASE.replace("%command%", disguiseCommand + " <subtype>").replace("%description%", language.HELP_SUBTYPE));
		StringBuilder builder = new StringBuilder();
		String color = ChatColor.getLastColors(language.HELP_TYPES);
		for(DisguiseType type : DisguiseType.values()) {
			if(!type.isPlayer()) {
				String format = !type.isAvailable() ? language.HELP_TYPES_NOT_SUPPORTED : hasPermission(sender, type) ? language.HELP_TYPES_AVAILABLE : language.HELP_TYPES_NO_PERMISSION;
				if(format.contains("%type%")) {	
					builder.append(format.replace("%type%", type.getDefaultCommandArgument()));
					builder.append(color + ", ");
				}
			}
		}
		sender.sendMessage(language.HELP_TYPES.replace("%types%", builder.substring(0, builder.length() - 2)));
	}
	
	private boolean hasPermission(CommandSender sender, DisguiseType type) {
		switch(type) {
			case BAT:
				return sender.hasPermission("iDisguise.mob.bat");
			case BLAZE:
				return sender.hasPermission("iDisguise.mob.blaze");
			case CAVE_SPIDER:
				return sender.hasPermission("iDisguise.mob.cave_spider");
			case CHICKEN:
				return sender.hasPermission("iDisguise.mob.chicken");
			case COW:
				return sender.hasPermission("iDisguise.mob.cow");
			case CREEPER:
				return sender.hasPermission("iDisguise.mob.creeper");
			case DONKEY:
				return sender.hasPermission("iDisguise.mob.donkey");
			case ELDER_GUARDIAN:
				return sender.hasPermission("iDisguise.mob.elder_guardian");
			case ENDER_DRAGON:
				return sender.hasPermission("iDisguise.mob.ender_dragon");
			case ENDERMAN:
				return sender.hasPermission("iDisguise.mob.enderman");
			case ENDERMITE:
				return sender.hasPermission("iDisguise.mob.endermite");
			case EVOKER:
				return sender.hasPermission("iDisguise.mob.evoker");
			case GHAST:
				return sender.hasPermission("iDisguise.mob.ghast");
			case GIANT:
				return sender.hasPermission("iDisguise.mob.giant");
			case GUARDIAN:
				return sender.hasPermission("iDisguise.mob.guardian");
			case HORSE:
				return sender.hasPermission("iDisguise.mob.horse");
			case HUSK:
				return sender.hasPermission("iDisguise.mob.husk");
			case IRON_GOLEM:
				return sender.hasPermission("iDisguise.mob.iron_golem");
			case LLAMA:
				return sender.hasPermission("iDisguise.mob.llama");
			case MAGMA_CUBE:
				return sender.hasPermission("iDisguise.mob.magma_cube");
			case MULE:
				return sender.hasPermission("iDisguise.mob.mule");
			case MUSHROOM_COW:
				return sender.hasPermission("iDisguise.mob.mushroom_cow");
			case OCELOT:
				return sender.hasPermission("iDisguise.mob.ocelot");
			case PIG:
				return sender.hasPermission("iDisguise.mob.pig");
			case PIG_ZOMBIE:
				return sender.hasPermission("iDisguise.mob.pig_zombie");
			case POLAR_BEAR:
				return sender.hasPermission("iDisguise.mob.polar_bear");
			case RABBIT:
				return sender.hasPermission("iDisguise.mob.rabbit");
			case SHEEP:
				return sender.hasPermission("iDisguise.mob.sheep");
			case SHULKER:
				return sender.hasPermission("iDisguise.mob.shulker");
			case SILVERFISH:
				return sender.hasPermission("iDisguise.mob.silverfish");
			case SKELETAL_HORSE:
				return sender.hasPermission("iDisguise.mob.skeletal_horse");
			case SKELETON:
				return sender.hasPermission("iDisguise.mob.skeleton");
			case SLIME:
				return sender.hasPermission("iDisguise.mob.slime");
			case SNOWMAN:
				return sender.hasPermission("iDisguise.mob.snowman");
			case SPIDER:
				return sender.hasPermission("iDisguise.mob.spider");
			case SQUID:
				return sender.hasPermission("iDisguise.mob.squid");
			case STRAY:
				return sender.hasPermission("iDisguise.mob.stray");
			case UNDEAD_HORSE:
				return sender.hasPermission("iDisguise.mob.undead_horse");
			case VEX:
				return sender.hasPermission("iDisguise.mob.vex");
			case VILLAGER:
				return sender.hasPermission("iDisguise.mob.villager");
			case VINDICATOR:
				return sender.hasPermission("iDisguise.mob.vindicator");
			case WITCH:
				return sender.hasPermission("iDisguise.mob.witch");
			case WITHER:
				return sender.hasPermission("iDisguise.mob.wither");
			case WITHER_SKELETON:
				return sender.hasPermission("iDisguise.mob.wither_skeleton");
			case WOLF:
				return sender.hasPermission("iDisguise.mob.wolf");
			case ZOMBIE:
				return sender.hasPermission("iDisguise.mob.zombie");
			case ZOMBIE_VILLAGER:
				return sender.hasPermission("iDisguise.mob.zombie_villager");
			case ARMOR_STAND:
				return sender.hasPermission("iDisguise.object.armor_stand");
			case BOAT:
				return sender.hasPermission("iDisguise.object.boat");
			case ENDER_CRYSTAL:
				return sender.hasPermission("iDisguise.object.ender_crystal");
			case FALLING_BLOCK:
				return sender.hasPermission("iDisguise.object.falling_block");
			case ITEM:
				return sender.hasPermission("iDisguise.object.item");
			case MINECART:
				return sender.hasPermission("iDisguise.object.minecart");
			default:
				return false;
		}
	}
	
	private boolean hasPermission(CommandSender sender, Disguise disguise) {
		if(ObjectUtil.equals(disguise.getVisibility(), Visibility.ONLY_LIST, Visibility.NOT_LIST) && !sender.hasPermission("iDisguise.visibility.list")) return false;
		if(ObjectUtil.equals(disguise.getVisibility(), Visibility.ONLY_PERMISSION, Visibility.NOT_PERMISSION) && !sender.hasPermission("iDisguise.visibility.permission")) return false;
		switch(disguise.getType()) {
			case BAT:
				return sender.hasPermission("iDisguise.mob.bat");
			case BLAZE:
				return sender.hasPermission("iDisguise.mob.blaze");
			case CAVE_SPIDER:
				return sender.hasPermission("iDisguise.mob.cave_spider");
			case CHICKEN:
				return sender.hasPermission("iDisguise.mob.chicken") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case COW:
				return sender.hasPermission("iDisguise.mob.cow") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case CREEPER:
				return sender.hasPermission("iDisguise.mob.creeper") && (!((CreeperDisguise)disguise).isPowered() || sender.hasPermission("iDisguise.mob.creeper.powered"));
			case DONKEY:
				return sender.hasPermission("iDisguise.mob.donkey") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case ELDER_GUARDIAN:
				return sender.hasPermission("iDisguise.mob.elder_guardian");
			case ENDER_DRAGON:
				return sender.hasPermission("iDisguise.mob.ender_dragon");
			case ENDERMAN:
				return sender.hasPermission("iDisguise.mob.enderman") && (((EndermanDisguise)disguise).getBlockInHand().equals(Material.AIR) || sender.hasPermission("iDisguise.mob.enderman.block"));
			case ENDERMITE:
				return sender.hasPermission("iDisguise.mob.endermite");
			case EVOKER:
				return sender.hasPermission("iDisguise.mob.evoker");
			case GHAST:
				return sender.hasPermission("iDisguise.mob.ghast");
			case GHOST:
				return sender.hasPermission("iDisguise.ghost") && (sender.hasPermission("iDisguise.player.name.*") || sender.hasPermission("iDisguise.player.name." + ((PlayerDisguise)disguise).getSkinName())) && (isPlayerDisguisePermitted(((PlayerDisguise)disguise).getSkinName()) || sender.hasPermission("iDisguise.player.prohibited"));
			case GIANT:
				return sender.hasPermission("iDisguise.mob.giant");
			case GUARDIAN:
				return sender.hasPermission("iDisguise.mob.guardian");
			case HORSE:
				return sender.hasPermission("iDisguise.mob.horse") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case HUSK:
				return sender.hasPermission("iDisguise.mob.husk") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case IRON_GOLEM:
				return sender.hasPermission("iDisguise.mob.iron_golem");
			case LLAMA:
				return sender.hasPermission("iDisguise.mob.llama") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case MAGMA_CUBE:
				return sender.hasPermission("iDisguise.mob.magma_cube") && (((SizedDisguise)disguise).getSize() < 5 || sender.hasPermission("iDisguise.mob.magma_cube.giant"));
			case MULE:
				return sender.hasPermission("iDisguise.mob.mule") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case MUSHROOM_COW:
				return sender.hasPermission("iDisguise.mob.mushroom_cow") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case OCELOT:
				return sender.hasPermission("iDisguise.mob.ocelot") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && sender.hasPermission("iDisguise.mob.ocelot.type." + ((OcelotDisguise)disguise).getCatType().name().toLowerCase(Locale.ENGLISH).replaceAll("_.*", ""));
			case PIG:
				return sender.hasPermission("iDisguise.mob.pig") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && (!((PigDisguise)disguise).isSaddled() || sender.hasPermission("iDisguise.mob.pig.saddled"));
			case PIG_ZOMBIE:
				return sender.hasPermission("iDisguise.mob.pig_zombie") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case PLAYER:
				return (sender.hasPermission("iDisguise.player.name.*") || sender.hasPermission("iDisguise.player.name." + ((PlayerDisguise)disguise).getSkinName())) && (isPlayerDisguisePermitted(((PlayerDisguise)disguise).getSkinName()) || sender.hasPermission("iDisguise.player.prohibited"));
			case POLAR_BEAR:
				return sender.hasPermission("iDisguise.mob.polar_bear") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case RABBIT:
				return sender.hasPermission("iDisguise.mob.rabbit") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && sender.hasPermission("iDisguise.mob.rabbit.type." + ((RabbitDisguise)disguise).getRabbitType().name().toLowerCase(Locale.ENGLISH).replace("_and_", "-").replace("the_killer_bunny", "killer"));
			case SHEEP:
				return sender.hasPermission("iDisguise.mob.sheep") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && sender.hasPermission("iDisguise.mob.sheep.color." + ((SheepDisguise)disguise).getColor().name().toLowerCase(Locale.ENGLISH).replace('_', '-'));
			case SHULKER:
				return sender.hasPermission("iDisguise.mob.shulker");
			case SILVERFISH:
				return sender.hasPermission("iDisguise.mob.silverfish");
			case SKELETAL_HORSE:
				return sender.hasPermission("iDisguise.mob.skeletal_horse") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case SKELETON:
				return sender.hasPermission("iDisguise.mob.skeleton");
			case SLIME:
				return sender.hasPermission("iDisguise.mob.slime") && (((SizedDisguise)disguise).getSize() < 5 || sender.hasPermission("iDisguise.mob.slime.giant"));
			case SNOWMAN:
				return sender.hasPermission("iDisguise.mob.snowman");
			case SPIDER:
				return sender.hasPermission("iDisguise.mob.spider");
			case SQUID:
				return sender.hasPermission("iDisguise.mob.squid");
			case STRAY:
				return sender.hasPermission("iDisguise.mob.stray");
			case UNDEAD_HORSE:
				return sender.hasPermission("iDisguise.mob.undead_horse") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case VEX:
				return sender.hasPermission("iDisguise.mob.vex");
			case VILLAGER:
				return sender.hasPermission("iDisguise.mob.villager") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && sender.hasPermission("iDisguise.mob.villager.profession." + ((VillagerDisguise)disguise).getProfession().name().toLowerCase(Locale.ENGLISH));
			case VINDICATOR:
				return sender.hasPermission("iDisguise.mob.vindicator");
			case WITCH:
				return sender.hasPermission("iDisguise.mob.witch");
			case WITHER:
				return sender.hasPermission("iDisguise.mob.wither");
			case WITHER_SKELETON:
				return sender.hasPermission("iDisguise.mob.wither_skeleton");
			case WOLF:
				return sender.hasPermission("iDisguise.mob.wolf") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby")) && sender.hasPermission("iDisguise.mob.wolf.collar." + ((WolfDisguise)disguise).getCollarColor().name().toLowerCase(Locale.ENGLISH).replace('_', '-')) && (!((WolfDisguise)disguise).isTamed() || sender.hasPermission("iDisguise.mob.wolf.tamed")) && (!((WolfDisguise)disguise).isAngry() || sender.hasPermission("iDisguise.mob.wolf.angry"));
			case ZOMBIE:
				return sender.hasPermission("iDisguise.mob.zombie") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case ZOMBIE_VILLAGER:
				return sender.hasPermission("iDisguise.mob.zombie_villager") && (((AgeableDisguise)disguise).isAdult() || sender.hasPermission("iDisguise.mob.baby"));
			case ARMOR_STAND:
				return sender.hasPermission("iDisguise.object.armor_stand");
			case BOAT:
				return sender.hasPermission("iDisguise.object.boat");
			case ENDER_CRYSTAL:
				return sender.hasPermission("iDisguise.object.ender_crystal");
			case FALLING_BLOCK:
				return sender.hasPermission("iDisguise.object.falling_block") && (sender.hasPermission("iDisguise.object.falling_block.material.*") || sender.hasPermission("iDisguise.object.falling_block.material." + ((FallingBlockDisguise)disguise).getMaterial().name().toLowerCase(Locale.ENGLISH).replace('_', '-')));
			case ITEM:
				return sender.hasPermission("iDisguise.object.item");
			case MINECART:
				return sender.hasPermission("iDisguise.object.minecart");
			default:
				return false;
		}
	}
	
	private void checkDirectory() {
		if(!getDataFolder().exists()) {
			getDataFolder().mkdir();
		}
	}
	
	private void loadData() {
		File dataFile = new File(getDataFolder(), "disguises.dat");
		File oldDataFile = new File(getDataFolder(), "data.bin");
		if(dataFile.exists()) {
			DisguiseManager.getInstance().updateDisguises(SLAPI.loadMap(dataFile));
		} else if(oldDataFile.exists()) {
			Object map = SLAPI.load(oldDataFile);
			if(map instanceof Map) {
				DisguiseManager.getInstance().updateDisguises((Map<?, Disguise>)map);
			}
			oldDataFile.delete();
		}
	}
	
	private void saveData() {
		File dataFile = new File(getDataFolder(), "disguises.dat");
		SLAPI.saveMap(DisguiseManager.getInstance().getDisguises(), dataFile);
	}
	
	public DisguiseAPI getAPI() {
		return new DisguiseAPI() {
			
			@Deprecated
			public void disguiseToAll(Player player, Disguise disguise) {
				disguise(player, disguise, false);
			}
			
			@Deprecated
			public void undisguiseToAll(Player player) {
				undisguise(player, false);
			}
			
			public boolean disguise(OfflinePlayer player, Disguise disguise) {
				return disguise(player, disguise, true);
			}
			
			public boolean disguise(OfflinePlayer player, Disguise disguise, boolean fireEvent) {
				if(fireEvent) {
					if(player.isOnline()) {
						DisguiseEvent event = new DisguiseEvent(player.getPlayer(), disguise);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							return false;
						} else {
							DisguiseManager.getInstance().disguise(player, disguise);
							return true;
						}
					} else {
						OfflinePlayerDisguiseEvent event = new OfflinePlayerDisguiseEvent(player, disguise);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							return false;
						} else {
							DisguiseManager.getInstance().disguise(player, disguise);
							return true;
						}
					}
				} else {
					DisguiseManager.getInstance().disguise(player, disguise);
					return true;
				}
			}
			
			public boolean undisguise(OfflinePlayer player) {
				return undisguise(player, true);
			}
			
			public boolean undisguise(OfflinePlayer player, boolean fireEvent) {
				if(!isDisguised(player)) {
					return false;
				}
				if(fireEvent) {
					if(player.isOnline()) {
						UndisguiseEvent event = new UndisguiseEvent(player.getPlayer(), getDisguise(player), false);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							return false;
						} else {
							DisguiseManager.getInstance().undisguise(player);
							return true;
						}
					} else {
						OfflinePlayerUndisguiseEvent event = new OfflinePlayerUndisguiseEvent(player, getDisguise(player), false);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							return false;
						} else {
							DisguiseManager.getInstance().undisguise(player);
							return true;
						}
					}
				} else {
					DisguiseManager.getInstance().undisguise(player);
					return true;
				}
			}
			
			public void undisguiseAll() {
				DisguiseManager.getInstance().undisguiseAll();
			}
			
			@Deprecated
			public boolean isDisguised(Player player) {
				return isDisguised((OfflinePlayer)player);
			}
			
			public boolean isDisguised(OfflinePlayer player) {
				return DisguiseManager.getInstance().isDisguised(player);
			}
			
			public boolean isDisguisedTo(OfflinePlayer player, Player observer) {
				return DisguiseManager.getInstance().isDisguisedTo(player, observer);
			}
			
			@Deprecated
			public Disguise getDisguise(Player player) {
				return getDisguise((OfflinePlayer)player);
			}
			
			public Disguise getDisguise(OfflinePlayer player) {
				return DisguiseManager.getInstance().getDisguise(player).clone();
			}
			
			@Deprecated
			public int getOnlineDisguiseCount() {
				return getNumberOfDisguisedPlayers();
			}
			
			public int getNumberOfDisguisedPlayers() {
				return DisguiseManager.getInstance().getNumberOfDisguisedPlayers();
			}
			
			public Sounds getSoundsForEntity(DisguiseType type) {
				return Sounds.getSoundsForEntity(type);
			}
			
			public boolean setSoundsForEntity(DisguiseType type, Sounds sounds) {
				return Sounds.setSoundsForEntity(type, sounds);
			}
			
			public boolean isSoundsEnabled() {
				return Sounds.isEnabled();
			}
			
			public void setSoundsEnabled(boolean enabled) {
				Sounds.setEnabled(enabled);
			}
			
			public boolean hasPermission(Player player, DisguiseType type) {
				return hasPermission(player, type);
			}
			
			public boolean hasPermission(Player player, Disguise disguise) {
				return iDisguise.this.hasPermission(player, disguise);
			}
			
		};
	}
	
	public String getVersion() {
		return getDescription().getVersion();
	}
	
	public String getFullName() {
		return "iDisguise " + getVersion();
	}
	
	public File getPluginFile() {
		return getFile();
	}
	
	public Configuration getConfiguration() {
		return configuration;
	}
	
	public Language getLanguage() {
		return language;
	}
	
	public boolean isPlayerDisguisePermitted(String name) {
		return !getConfiguration().RESTRICTED_PLAYER_NAMES.contains(name);
	}
	
	public boolean enabled() {
		return enabled;
	}
	
	public static iDisguise getInstance() {
		return instance;
	}
	
}