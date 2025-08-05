package com.autoconcrete.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import com.autoconcrete.addon.Xenon;

public class AutoCityPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    private final Setting<Boolean> mineBedrock = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-bedrock")
        .description("Allows mining bedrock blocks.")
        .defaultValue(false)
        .build()
    );

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

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to block before mining.")
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
        .description("Delay between chat messages in ticks.")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

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

    public AutoCityPlus() {
        super(Xenon.XENON_CATEGORY, "auto-city-plus", "Automatically mine blocks next to someone's feet.");
    }

    @Override
    public void onActivate() {
        target = null;
        targetPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        target = null;
        double closestDistance = targetRange.get() * targetRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isCreative() || player.isSpectator()) continue;
            double distance = player.squaredDistanceTo(mc.player);
            if (distance <= closestDistance) {
                closestDistance = distance;
                target = player;
            }
        }

        if (target == null) return;

        targetPos = findCityBlock(target);
        if (targetPos == null) return;

        if (PlayerUtils.squaredDistanceTo(targetPos) > breakRange.get() * breakRange.get()) return;

        if (support.get() && mc.world.getBlockState(targetPos.down()).isAir()) {
            BlockUtils.place(targetPos.down(), InvUtils.findInHotbar(Items.OBSIDIAN), rotate.get(), 0, true);
        }

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(targetPos), Rotations.getPitch(targetPos));
        }

        // ✅ Actual block mining (simulate "holding left click")
        mc.interactionManager.updateBlockBreakingProgress(targetPos, Direction.UP);

        if (swingHand.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private BlockPos findCityBlock(PlayerEntity target) {
        BlockPos pos = target.getBlockPos();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos offset = pos.offset(dir);
            Block block = mc.world.getBlockState(offset).getBlock();

            if (PlayerUtils.squaredDistanceTo(offset) > breakRange.get() * breakRange.get()) continue;

            if (mineBedrock.get() && block == Blocks.BEDROCK) return offset;
            else if (!mineBedrock.get() && block != Blocks.AIR && block != Blocks.BEDROCK) return offset;
        }
        return null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetPos != null && renderBlock.get()) {
            event.renderer.box(targetPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}
