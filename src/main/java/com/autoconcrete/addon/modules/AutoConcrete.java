package com.autoconcrete.addon.modules;

import com.autoconcrete.addon.Xenon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoConcrete extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far to search for targets.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> pillarDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pillar-delay")
        .description("Delay between obsidian pillar block placements.")
        .defaultValue(30)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> concreteDelay = sgGeneral.add(new IntSetting.Builder()
        .name("concrete-delay")
        .description("Delay after dropping concrete.")
        .defaultValue(50)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward blocks when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-crystals")
        .description("Raise the pillar and concrete if a crystal is on or above the surround.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Place concrete in the air without requiring support blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> placeSupport = sgGeneral.add(new BoolSetting.Builder()
        .name("place-support")
        .description("Place obsidian pillar when air place is disabled.")
        .defaultValue(true)
        .build()
    );

    private PlayerEntity target;
    private BlockPos basePos;
    private BlockPos concretePos;
    private BlockPos lastTargetPos;
    private Direction placedDirection;
    private int currentPillarHeight = 2;
    private int cooldown = 0;
    private long lastWarningTime = 0;

    public AutoConcrete() {
        super(Xenon.XENON_CATEGORY, "auto-concrete", "Places a 2–3 block obsidian pillar on one surround side of a target and drops concrete above them.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    private void reset() {
        basePos = null;
        concretePos = null;
        placedDirection = null;
        lastTargetPos = null;
        currentPillarHeight = 2;
        cooldown = 0;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof AnvilScreen) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestHealth);
            if (TargetUtils.isBadTarget(target, range.get())) return;
            reset();
        }

        BlockPos targetPos = target.getBlockPos();

        if (lastTargetPos != null && !lastTargetPos.equals(targetPos)) {
            info("Target moved. Resetting.");
            reset();
        }
        lastTargetPos = targetPos;

        FindItemResult obsidian = InvUtils.findInHotbar(stack -> Block.getBlockFromItem(stack.getItem()) == Blocks.OBSIDIAN);
        FindItemResult concrete = InvUtils.findInHotbar(stack -> {
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block != null && (block.toString().endsWith("_concrete_powder") || block.toString().endsWith("_concrete"))) return true;
            String itemName = stack.getItem().toString().toLowerCase();
            return itemName.contains("concrete_powder") || itemName.contains("concrete");
        });

        if (!concrete.found() || (!obsidian.found() && !airPlace.get())) {
            long now = System.currentTimeMillis();
            if (now - lastWarningTime >= 1000) {
                warning("Missing concrete or obsidian.");
                lastWarningTime = now;
            }
            return;
        }

        boolean crystalPresent = detectCrystals.get() && isCrystalOnSurround(target);

        if (airPlace.get()) {
            currentPillarHeight = crystalPresent ? 3 : 2;
            concretePos = targetPos.up(currentPillarHeight);
        } else if (placeSupport.get()) {
            if (placedDirection == null || basePos == null || concretePos == null) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos surround = targetPos.offset(dir);
                    BlockPos base = surround.up();

                    boolean is2Tall = mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)
                        && mc.world.getBlockState(base.up()).isOf(Blocks.OBSIDIAN);
                    boolean is3Tall = is2Tall && mc.world.getBlockState(base.up(2)).isOf(Blocks.OBSIDIAN);

                    if (is2Tall || is3Tall) {
                        placedDirection = dir;
                        basePos = base;
                        currentPillarHeight = crystalPresent ? 3 : 2;
                        concretePos = targetPos.up(currentPillarHeight);
                        break;
                    }

                    if (!mc.world.getBlockState(surround).isAir()) {
                        boolean canPlace = true;
                        int height = crystalPresent ? 3 : 2;
                        for (int i = 0; i < height; i++) {
                            if (!mc.world.getBlockState(base.up(i)).isReplaceable()) {
                                canPlace = false;
                                break;
                            }
                        }

                        if (canPlace) {
                            placedDirection = dir;
                            basePos = base;
                            currentPillarHeight = height;
                            concretePos = targetPos.up(currentPillarHeight);
                            break;
                        }
                    }
                }
            }

            if (basePos == null || concretePos == null || placedDirection == null) return;

            for (int i = 0; i < currentPillarHeight; i++) {
                BlockPos pos = basePos.up(i);
                if (!mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) {
                    BlockUtils.place(pos, obsidian, rotate.get(), 0);
                    cooldown = pillarDelay.get();
                    return;
                }
            }

            if (detectCrystals.get() && crystalPresent && currentPillarHeight == 2) {
                BlockPos third = basePos.up(2);
                if (mc.world.getBlockState(third).isReplaceable()) {
                    BlockUtils.place(third, obsidian, rotate.get(), 0);
                    currentPillarHeight = 3;
                    concretePos = target.getBlockPos().up(3);
                    cooldown = pillarDelay.get();
                    return;
                }
            }
        } else {
            return; // No valid placement mode
        }

        if (mc.world.getBlockState(concretePos).isReplaceable()) {
            BlockUtils.place(concretePos, concrete, rotate.get(), 0);
            cooldown = concreteDelay.get();
        }
    }

    private boolean isCrystalOnSurround(PlayerEntity target) {
        BlockPos pos = target.getBlockPos();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos surround = pos.offset(dir);
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity) {
                    if (entity.getBoundingBox().intersects(
                        surround.toCenterPos().add(-0.5, 0, -0.5),
                        surround.toCenterPos().add(0.5, 2.5, 0.5))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String getInfoString() {
        return target != null ? (airPlace.get() ? "AirPlace - " : "Pillar - ") + EntityUtils.getName(target) : null;
    }
}
