package com.autoconcrete.addon.modules;

import com.autoconcrete.addon.Xenon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AntiConcrete extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("When to place the button.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("smart-range")
        .description("Enemy range to trigger placing.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 7)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-inventory-swap")
        .description("Temporarily moves a button to hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Which hotbar slot to place the button into.")
        .defaultValue(1)
        .min(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> returnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("return-delay")
        .description("Delay before returning button to inventory (in ticks). 20 ticks = 1 second.")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private int returnTimer = -1;
    private int originalSlot = -1;
    private boolean waitingToReturn = false;

    public AntiConcrete() {
        super(Xenon.XENON_CATEGORY, "anti-concrete", "Places a button under yourself.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (waitingToReturn) {
            if (--returnTimer <= 0) {
                int hotbarSlot = hotbarSlotSetting.get() - 1;
                InvUtils.move().from(hotbarSlot).to(originalSlot);
                waitingToReturn = false;
            }
        }

        if (TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestDistance) != null) {
            if (mode.get() == Mode.Smart) {
                if (isConcreteAbove()) tryPlaceButton();
            } else {
                tryPlaceButton();
            }
        }
    }

    private void tryPlaceButton() {
        BlockPos currentPos = mc.player.getBlockPos();
        if (isButtonBlock(currentPos)) return;

        FindItemResult button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));

        originalSlot = -1;
        boolean swapped = false;

        if (!button.found() && silentSwap.get()) {
            FindItemResult invButton = InvUtils.find(stack -> isButton(stack.getItem()));
            if (invButton.found() && invButton.slot() >= 9) {
                int hotbarSlot = hotbarSlotSetting.get() - 1;
                originalSlot = invButton.slot();

                InvUtils.move().from(invButton.slot()).to(hotbarSlot);
                swapped = true;

                button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));
            }
        }

        if (!button.found()) {
            warning("No button in hotbar or inventory.");
            return;
        }

        BlockUtils.place(currentPos, button, true, 0, false, false);

        if (swapped && originalSlot != -1) {
            returnTimer = returnDelay.get();
            waitingToReturn = true;
        }
    }

    private boolean isConcreteAbove() {
        boolean detected = false;

        for (int i = 1; i <= 3; i++) {
            if (isConcretePowderBlock(mc.player.getBlockPos().up(i))) detected = true;
        }

        Box box = new Box(
            mc.player.getX() - 0.5, mc.player.getY() + 1, mc.player.getZ() - 0.5,
            mc.player.getX() + 0.5, mc.player.getY() + 4, mc.player.getZ() + 0.5
        );

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity instanceof FallingBlockEntity falling) {
                Block block = falling.getBlockState().getBlock();
                if (isConcretePowderBlock(block)) detected = true;
            }
        }

        return detected;
    }

    private boolean isConcretePowderBlock(BlockPos pos) {
        return isConcretePowderBlock(mc.world.getBlockState(pos).getBlock());
    }

    private boolean isConcretePowderBlock(Block block) {
        return block == Blocks.WHITE_CONCRETE_POWDER ||
            block == Blocks.LIGHT_GRAY_CONCRETE_POWDER ||
            block == Blocks.GRAY_CONCRETE_POWDER ||
            block == Blocks.BLACK_CONCRETE_POWDER ||
            block == Blocks.BROWN_CONCRETE_POWDER ||
            block == Blocks.RED_CONCRETE_POWDER ||
            block == Blocks.ORANGE_CONCRETE_POWDER ||
            block == Blocks.YELLOW_CONCRETE_POWDER ||
            block == Blocks.LIME_CONCRETE_POWDER ||
            block == Blocks.GREEN_CONCRETE_POWDER ||
            block == Blocks.CYAN_CONCRETE_POWDER ||
            block == Blocks.LIGHT_BLUE_CONCRETE_POWDER ||
            block == Blocks.BLUE_CONCRETE_POWDER ||
            block == Blocks.PURPLE_CONCRETE_POWDER ||
            block == Blocks.MAGENTA_CONCRETE_POWDER ||
            block == Blocks.PINK_CONCRETE_POWDER ||
            block == Blocks.GRAVEL ||
            block == Blocks.SAND ||
            block == Blocks.RED_SAND ||
            block == Blocks.SUSPICIOUS_SAND ||
            block == Blocks.SUSPICIOUS_GRAVEL;
    }

    private boolean isButtonBlock(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.POLISHED_BLACKSTONE_BUTTON ||
            block == Blocks.STONE_BUTTON ||
            block == Blocks.WARPED_BUTTON ||
            block == Blocks.CRIMSON_BUTTON ||
            block == Blocks.BAMBOO_BUTTON ||
            block == Blocks.CHERRY_BUTTON ||
            block == Blocks.MANGROVE_BUTTON ||
            block == Blocks.DARK_OAK_BUTTON ||
            block == Blocks.ACACIA_BUTTON ||
            block == Blocks.JUNGLE_BUTTON ||
            block == Blocks.BIRCH_BUTTON ||
            block == Blocks.SPRUCE_BUTTON ||
            block == Blocks.OAK_BUTTON;
    }

    private boolean isButton(Item item) {
        return item == Items.POLISHED_BLACKSTONE_BUTTON ||
            item == Items.STONE_BUTTON ||
            item == Items.WARPED_BUTTON ||
            item == Items.CRIMSON_BUTTON ||
            item == Items.BAMBOO_BUTTON ||
            item == Items.CHERRY_BUTTON ||
            item == Items.MANGROVE_BUTTON ||
            item == Items.DARK_OAK_BUTTON ||
            item == Items.ACACIA_BUTTON ||
            item == Items.JUNGLE_BUTTON ||
            item == Items.BIRCH_BUTTON ||
            item == Items.SPRUCE_BUTTON ||
            item == Items.OAK_BUTTON;
    }

    public enum Mode {
        Strict,
        Smart
    }
}
