package moldy.bread;

import net.fabricmc.api.ModInitializer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * * @author Billyham Gista
 * @version 1.N0.N0
 * I pretty much just put all my below methods in the header for this because I am lazy.
 * that is exactly what I am going to do here
 *
 * Initializes commands that players and ops can use. they are:
 * --------------------------------
 * `setwarp <name>`
 * --------------------------------
 * 
 * --------------------------------
 * `/warp <name>`
 * --------------------------------
 * 
 * --------------------------------
 * `/bounty <player> <item> <amount>
 * --------------------------------
 *
 * Players now drop their heads, I assume that will not cause any perverse incentives
 * @param player the victim
 */
public class Bestoffenseisagooddefenseamiright implements ModInitializer {
	public static final String MOD_ID = "bestoffenseisagooddefenseamiright";
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Map<String, Vec3d> _warps = new HashMap<>();
    private static final Map<String, BountyData> _bounties = new HashMap<>();

	private record BountyData(String item, int amount) {}

	/**
     * Initializes commands that players and ops can use. they are:
	 * --------------------------------
	 * `setwarp <name>`
	 * --------------------------------
	 * 
	 * --------------------------------
	 * `/warp <name>`
	 * --------------------------------
	 * 
	 * --------------------------------
	 * `/bounty <player> <item> <amount>
	 * --------------------------------
     */
	@Override
	public void onInitialize() {
        LOGGER.info("Initializing BestOffense Mod for Minecraft 1.21.11...");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			/**
			* Set warp, well sets a warp to whereever an op wants, it takes away 5 heart when you tp but more below
			* Usage:
			* --------------------------------
			* `setwarp <name>`
			* --------------------------------
			*
			*/
            dispatcher.register(CommandManager.literal("setwarp")
                .requires(source -> source.getServer().getPermissionLevel(source.getPlayer().getGameProfile()) >= 2) // OP level 2
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            String warpName = StringArgumentType.getString(context, "name").toLowerCase();
                            Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
                            _warps.put(warpName, pos);
                            context.getSource().sendFeedback(() -> Text.literal("§aWarp '" + warpName + "' set at your location!"), false);
                        }
                        return 1;
                    })
                )
            );

			/**
			* Warps to the specified named warp point, pretty good thing for the bozos that play on my server, right?
			* well turns out it hurts you, and pretty bad, so better have food handy boo :3
			* Usage:
			* --------------------------------
			* `/warp <name>`
			* --------------------------------
			*
			*/
            dispatcher.register(CommandManager.literal("warp")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null) {
                            String warpName = StringArgumentType.getString(context, "name").toLowerCase();
                            if (_warps.containsKey(warpName)) {
                                Vec3d targetPos = _warps.get(warpName);
                                net.minecraft.server.world.ServerWorld serverWorld = player.getServerWorld();
                                player.teleport(serverWorld, targetPos.getX(), targetPos.getY(), targetPos.getZ(), java.util.Collections.emptySet(), player.getYaw(), player.getPitch(), true);
                                context.getSource().sendFeedback(() -> Text.literal("§aTeleported to warp: " + warpName), false);

                                player.damage(serverWorld, player.getDamageSources().generic(), 10.0f); // 5 hearts of damage upon teleporting
                            } else {
                                context.getSource().sendError(Text.literal("§cWarp '" + warpName + "' does not exist."));
                            }
                        }
                        return 1;
                    })
                )
            );

			/**
			* Sets a bounty on a specified player for a number of a certain item. If you dont have the item you cannot put out the contract.
			* This seems like a good idea for a fun mechanic.....
			* Usage:
			* --------------------------------
			* `/bounty <player> <item> <amount>
			* --------------------------------
			*
			*/
            dispatcher.register(CommandManager.literal("bounty")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("item", StringArgumentType.word())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player != null) {
                                    String target = StringArgumentType.getString(context, "target");
                                    String itemString = StringArgumentType.getString(context, "item");
                                    int amountNeeded = IntegerArgumentType.getInteger(context, "amount");

                                    Identifier itemId = Identifier.of(itemString.contains(":") ? itemString : "minecraft:" + itemString);
                                    if (!Registries.ITEM.containsId(itemId)) {
                                        context.getSource().sendError(Text.literal("§cInvalid item name: " + itemString));
                                        return 0;
                                    }
                                    Item targetItem = Registries.ITEM.get(itemId);

                                    // Verify player's inventory contents
                                    int totalCount = 0;
                                    for (int i = 0; i < player.getInventory().size(); i++) {
                                        ItemStack stack = player.getInventory().getStack(i);
                                        if (stack.getItem() == targetItem) {
                                            totalCount += stack.getCount();
                                        }
                                    }

                                    if (totalCount < amountNeeded) {
                                        context.getSource().sendError(Text.literal("§c[bountyerror] you do not have those items"));
                                        return 0;
                                    }

                                    // Remove items from player's inventory
                                    int remainingToRemove = amountNeeded;
                                    for (int i = 0; i < player.getInventory().size(); i++) {
                                        ItemStack stack = player.getInventory().getStack(i);
                                        if (stack.getItem() == targetItem) {
                                            int deduct = Math.min(stack.getCount(), remainingToRemove);
                                            stack.decrement(deduct);
                                            remainingToRemove -= deduct;
                                            if (remainingToRemove <= 0) break;
                                        }
                                    }

                                    _bounties.put(target.toLowerCase(), new BountyData(itemString, amountNeeded));
                                    
                                    context.getSource().getServer().getPlayerManager().broadcast(
                                            Text.literal("§c[Bounty] §f" + player.getName().getString() + " placed a bounty of " + amountNeeded + " " + itemString + " on " + target + "!"), 
                                            false
                                    );
                                }
                                return 1;
                            })
                        )
                    )
                )
            );
        });
    }

	/**
     * Players now drop their heads, I assume that will not cause any perverse incentives
     * @param player the victim
     */
    private void dropPlayerHead(ServerPlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(java.util.Optional.of(player.getGameProfile())));

        net.minecraft.server.world.ServerWorld serverWorld = player.getServerWorld();

        ItemEntity itemEntity = new ItemEntity(
            serverWorld, 
            player.getX(), 
            player.getY(), 
            player.getZ(), 
            head
        );
        serverWorld.spawnEntity(itemEntity);
    }
}