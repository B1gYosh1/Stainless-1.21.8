package com.autoconcrete.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

// Food detection (1.21.x)
import net.minecraft.component.DataComponentTypes;

import com.autoconcrete.addon.Xenon;

public class AutoMinePlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Targeting & ranges
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range to target players.")
        .defaultValue(5.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> breakRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Maximum block break range.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    // Bedrock options
    private final Setting<Boolean> mineBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-bedrock")
        .description("Allows mining bedrock blocks around the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> prioritizePlayerBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("prioritize-target-standing-bedrock")
        .description("Prioritize mining the bedrock the target is standing in over surrounding blocks.")
        .defaultValue(true)
        .visible(mineBedrock::get)
        .build()
    );

    // Clear your own upper hitbox bedrock (independent of mineBedrock)
    private final Setting<Boolean> clearUpperBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-upper-bedrock")
        .description("If phased, automatically mine the bedrock at your upper hitbox to free AutoMine/AutoCrystal.")
        .defaultValue(true)
        .build()
    );

    // Filters
    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't target players on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreNaked = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-naked")
        .description("Don't target players with no armor equipped.")
        .defaultValue(true)
        .build()
    );

    // Placement/support
    private final Setting<Boolean> support = sgGeneral.add(new BoolSetting.Builder()
        .name("support")
        .description("Places support block under break target if missing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far to place support blocks.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .visible(support::get)
        .build()
    );

    // QoL
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to block before mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-while-eating")
        .description("Temporarily pauses AutoMinePlus while you're eating food.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Sends debug info in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chatDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chat-delay")
        .description("Minimum ticks between messages (2s min enforced).")
        .defaultValue(40) // 2 seconds @ 20 tps
        .min(0)
        .sliderMax(200)
        .build()
    );

    // Render
    private final Setting<Boolean> swingHand = sgRender.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Whether to swing your hand when mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
        .name("render-block")
        .description("Renders the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the render shape looks.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the sides of the block.")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the lines of the block.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private PlayerEntity target;
    private BlockPos targetPos;

    // Chat throttle state (hard min 2s)
    private int chatCooldown = 0;
    private String pendingChat = null;
    private boolean wasEating = false;

    public AutoMinePlus() {
        super(Xenon.XENON_CATEGORY, "AutoMinePlus", "Expanded Automine with bedrock utilities.");
    }

    @Override
    public void onActivate() {
        target = null;
        targetPos = null;
        chatCooldown = 0;
        pendingChat = null;
        wasEating = false;
    }

    // ---- CHAT THROTTLE (2s min) ----
    private void chat(String msg) {
        if (!chatInfo.get()) return;
        int cooldownTicks = Math.max(40, chatDelay.get()); // enforce >= 2s
        if (chatCooldown <= 0) {
            info(msg);
            chatCooldown = cooldownTicks;
        } else {
            // keep only the latest message during cooldown
            pendingChat = msg;
        }
    }
    // --------------------------------

    // Safety: avoid weird rotates when floating with a block above head.
    // We will override this guard if we are explicitly clearing our own upper-bedrock.
    private boolean shouldAllowRotation() {
        BlockPos playerPos = mc.player.getBlockPos();
        Block blockAtFeet = mc.world.getBlockState(playerPos).getBlock();
        Block blockAboveHead = mc.world.getBlockState(playerPos.up(1)).getBlock();
        if (blockAtFeet == Blocks.AIR && blockAboveHead != Blocks.AIR) return false;
        return true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // tick chat cooldown and flush pending when ready
        if (chatCooldown > 0) {
            chatCooldown--;
            if (chatCooldown == 0 && pendingChat != null) {
                int cooldownTicks = Math.max(40, chatDelay.get());
                info(pendingChat);
                pendingChat = null;
                chatCooldown = cooldownTicks;
            }
        }

        // --- Pause-while-eating early-out ---
        if (pauseWhileEating.get() && mc.player != null) {
            boolean eatingNow = mc.player.isUsingItem() && isFood(mc.player.getActiveItem());
            if (eatingNow) {
                if (!wasEating) {
                    chat("Paused: eating.");
                    wasEating = true;
                }
                return;
            } else if (wasEating) {
                chat("Resuming after eating.");
                wasEating = false;
            }
        }
        // ------------------------------------

        // 1) Clear your own upper hitbox bedrock first
        if (clearUpperBedrock.get()) {
            BlockPos headPos = mc.player.getBlockPos().up(1);
            Block headBlock = mc.world.getBlockState(headPos).getBlock();

            if (headBlock == Blocks.BEDROCK) {
                targetPos = headPos;

                if (support.get() && mc.world.getBlockState(targetPos.down()).isAir()) {
                    BlockUtils.place(targetPos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
                }

                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
                }

                mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
                if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);

                chat("Clearing upper-hitbox bedrock.");
                return;
            }
        }

        // 2) Acquire/refresh target
        target = null;
        double closestDistance = targetRange.get() * targetRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isCreative() || player.isSpectator()) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (ignoreNaked.get() && isNaked(player)) continue;

            double distance = player.squaredDistanceTo(mc.player);
            if (distance <= closestDistance) {
                closestDistance = distance;
                target = player;
            }
        }

        if (target == null) {
            targetPos = null;
            return;
        }

        // 3) Prefer bedrock in the target's lower hitbox if enabled and in range
        boolean handledStandingBedrock = false;
        if (mineBedrock.get() && prioritizePlayerBedrock.get()) {
            BlockPos lowerHitboxPos = target.getBlockPos();
            Block lowerHitboxBlock = mc.world.getBlockState(lowerHitboxPos).getBlock();

            if (lowerHitboxBlock == Blocks.BEDROCK
                && PlayerUtils.squaredDistanceTo(lowerHitboxPos) <= breakRange.get() * breakRange.get()) {

                targetPos = lowerHitboxPos;
                handledStandingBedrock = true;
                chat("Breaking bedrock in target's lower hitbox.");
            }
        }

        // 4) Otherwise pick a city block around the target (bedrock first, then any solid)
        if (!handledStandingBedrock) {
            targetPos = findCityBlock(target);
            if (targetPos == null) return;

            Block blk = mc.world.getBlockState(targetPos).getBlock();
            if (blk == Blocks.BEDROCK) chat("Breaking surrounding bedrock.");
            else chat("Breaking surrounding block: " + blk.getName().getString());
        }

        if (PlayerUtils.squaredDistanceTo(targetPos) > breakRange.get() * breakRange.get()) return;

        // Support placement if needed
        if (support.get() && mc.world.getBlockState(targetPos.down()).isAir()
            && PlayerUtils.squaredDistanceTo(targetPos.down()) <= placeRange.get() * placeRange.get()) {
            BlockUtils.place(targetPos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
        }

        boolean allowActions = shouldAllowRotation();
        if (targetPos.equals(mc.player.getBlockPos().up(1))) allowActions = true;

        if (rotate.get() && allowActions) {
            Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
        }

        if (allowActions) {
            mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);
            if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    /**
     * Returns a block next to the target to "city":
     * - If mineBedrock is ON: first look for bedrock (priority).
     * - If none found (or setting OFF): look for any other solid block (non-air, non-liquid).
     */
    private BlockPos findCityBlock(PlayerEntity target) {
        BlockPos pos = target.getBlockPos();

        // Pass 1: bedrock (priority when enabled)
        if (mineBedrock.get()) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos offset = pos.offset(dir);
                if (PlayerUtils.squaredDistanceTo(offset) > breakRange.get() * breakRange.get()) continue;

                Block block = mc.world.getBlockState(offset).getBlock();
                if (block == Blocks.BEDROCK) return offset;
            }
        }

        // Pass 2: any other solid block (non-air, non-liquid)
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(dir);
            if (PlayerUtils.squaredDistanceTo(offset) > breakRange.get() * breakRange.get()) continue;

            if (!mc.world.getFluidState(offset).isEmpty()) continue;
            Block block = mc.world.getBlockState(offset).getBlock();
            if (block == Blocks.AIR) continue;
            if (block == Blocks.BEDROCK) continue;

            return offset;
        }

        return null;
    }

    private boolean isNaked(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    // Detect if an ItemStack is food (1.21.x data components)
    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetPos != null && renderBlock.get()) {
            event.renderer.box(targetPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}
