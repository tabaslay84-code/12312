package pl.hypemakro;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tryb "woda + blok" (domyslnie klawisz ;).
 *
 * Gdy masz blok w rece i klikniesz PPM w blok:
 *   1. POUR    - przelacza na wiaderko z woda i wylewa wode w miejsce stawiania,
 *   2. COLLECT - od razu zbiera wode z powrotem do wiaderka,
 *   3. PLACE   - wraca na slot z blokiem i stawia blok w tym samym miejscu.
 * Wiaderko konczy pelne, a slot wraca na blok.
 */
public final class WaterPlace {
    private enum Phase { IDLE, POUR, COLLECT, PLACE }

    private static Phase phase = Phase.IDLE;
    private static int blockSlot = -1;
    private static int bucketSlot = -1;
    private static BlockHitResult hit = null;
    private static int wait = 0;
    private static int cooldown = 0;
    private static int collectCooldown = 0;
    private static int ownPour = 0; // tickow, w ktorych woda moze byc moja
    private static final int SCAN = 5;
    private static Set<Long> prevSources = new HashSet<>();
    private static BlockPos prevCenter = null;

    private WaterPlace() {}

    /** Wolane z mixina na poczatku doItemUse. true = anuluj zwykle uzycie PPM. */
    public static boolean onUse(MinecraftClient mc) {
        noteUse(mc);
        if (!MakroConfig.waterPlace) return false;
        if (phase != Phase.IDLE || cooldown > 0) return true;

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return false;
        if (mc.world.getDimension().ultrawarm()) return false; // w Netherze woda znika

        if (!(player.getMainHandStack().getItem() instanceof BlockItem)) return false;
        if (!(mc.crosshairTarget instanceof BlockHitResult target) || target.getType() != HitResult.Type.BLOCK) return false;

        if (MakroConfig.switchOnly && !hasSwitchOn(mc.world, target.getBlockPos())) return false;

        int bucket = findWaterBucket(player);
        if (bucket < 0) return false; // brak wiaderka z woda -> zwykle stawianie

        blockSlot = player.getInventory().selectedSlot;
        bucketSlot = bucket;
        hit = target;
        phase = Phase.POUR;
        wait = 0;
        advance(mc);
        return true;
    }

    /** Wolane co tick (koniec ticka klienta). */
    public static void tick(MinecraftClient mc) {
        if (cooldown > 0) cooldown--;
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            abort(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        advance(mc);
    }

    private static void advance(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) {
            abort(mc);
            return;
        }
        int delay = MakroConfig.clampWaterDelay(MakroConfig.waterDelay);

        while (phase != Phase.IDLE) {
            switch (phase) {
                case POUR -> {
                    if (!player.getInventory().getStack(bucketSlot).isOf(Items.WATER_BUCKET)) {
                        phase = Phase.PLACE; // wiaderko zniklo - tylko postaw blok
                    } else {
                        player.getInventory().selectedSlot = bucketSlot;
                        ownPour = 10;
                        use(mc, player);
                        phase = Phase.COLLECT;
                        wait = delay;
                    }
                }
                case COLLECT -> {
                    ItemStack held = player.getInventory().getStack(bucketSlot);
                    // survival: po wylaniu jest puste wiaderko; kreatywa: zostaje z woda -> nie ma czego zbierac
                    if (held.isOf(Items.BUCKET)) {
                        player.getInventory().selectedSlot = bucketSlot;
                        use(mc, player);
                    }
                    phase = Phase.PLACE;
                    wait = delay;
                }
                case PLACE -> {
                    player.getInventory().selectedSlot = blockSlot;
                    ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                    if (result.isAccepted()) player.swingHand(Hand.MAIN_HAND);
                    finish();
                }
                default -> finish();
            }
            if (wait > 0) return; // reszta w kolejnych tickach
        }
    }

    private static void use(MinecraftClient mc, ClientPlayerEntity player) {
        ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (result.isAccepted()) player.swingHand(Hand.MAIN_HAND);
    }

    private static void finish() {
        phase = Phase.IDLE;
        hit = null;
        wait = 0;
        cooldown = 2;
    }

    private static void abort(MinecraftClient mc) {
        if (mc.player != null && blockSlot >= 0) {
            mc.player.getInventory().selectedSlot = blockSlot;
        }
        finish();
    }

    /** true gdy w trakcie krokow wody + blok (nie przerywaj). */
    public static boolean busy() {
        return phase != Phase.IDLE || cooldown > 0;
    }

    /** Guzik lub dzwignia przyczepiona do tego bloku. */
    private static boolean hasSwitchOn(World world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos p = pos.offset(d);
            BlockState s = world.getBlockState(p);
            if (!(s.getBlock() instanceof ButtonBlock) && !(s.getBlock() instanceof LeverBlock)) continue;
            if (!s.contains(Properties.BLOCK_FACE) || !s.contains(Properties.HORIZONTAL_FACING)) continue;
            Direction attached = switch (s.get(Properties.BLOCK_FACE)) {
                case FLOOR -> Direction.DOWN;
                case CEILING -> Direction.UP;
                default -> s.get(Properties.HORIZONTAL_FACING).getOpposite();
            };
            if (p.offset(attached).equals(pos)) return true;
        }
        return false;
    }

    /** Gdy trzymam wiaderko z woda i klikam - woda, ktora sie pojawi, jest moja. */
    private static void noteUse(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (p.getMainHandStack().isOf(Items.WATER_BUCKET) || p.getOffHandStack().isOf(Items.WATER_BUCKET)) {
            ownPour = 10;
        }
    }

    /**
     * Klawisz J: gdy KTOS INNY wyleje wode obok Ciebie (nowe zrodlo wody w zasiegu),
     * od razu stawia na niej dzwignie z paska - dzwignia zastepuje blok wody i ja usuwa.
     * Wody wylanej przez Ciebie mod nie rusza.
     */
    public static void collectTick(MinecraftClient mc) {
        if (ownPour > 0) ownPour--;
        if (collectCooldown > 0) collectCooldown--;

        ClientPlayerEntity player = mc.player;
        if (!MakroConfig.autoCollect || player == null || mc.world == null || mc.interactionManager == null) {
            prevCenter = null;
            prevSources.clear();
            return;
        }
        World world = mc.world;
        BlockPos center = player.getBlockPos();
        boolean comparable = prevCenter != null
            && Math.abs(center.getX() - prevCenter.getX()) <= 2
            && Math.abs(center.getY() - prevCenter.getY()) <= 2
            && Math.abs(center.getZ() - prevCenter.getZ()) <= 2;

        Set<Long> now = new HashSet<>();
        List<BlockPos> fresh = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dy = -SCAN; dy <= SCAN; dy++) {
                for (int dz = -SCAN; dz <= SCAN; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(m);
                    if (!state.isOf(Blocks.WATER) || !state.getFluidState().isStill()) continue;
                    long key = m.asLong();
                    now.add(key);
                    if (comparable && !prevSources.contains(key) && inRegion(m, prevCenter)) {
                        fresh.add(m.toImmutable());
                    }
                }
            }
        }
        prevSources = now;
        prevCenter = center;

        if (fresh.isEmpty() || ownPour > 0 || collectCooldown > 0 || busy() || mc.currentScreen != null) return;

        int slot = findLever(player);
        if (slot < 0) return;

        Vec3d eye = player.getEyePos();
        BlockPos best = null;
        double bestDist = 4.5 * 4.5;
        for (BlockPos p : fresh) {
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(p));
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null) return;

        BlockHitResult placeHit = attachFace(world, best);
        if (placeHit == null) return;

        int previous = player.getInventory().selectedSlot;
        player.getInventory().selectedSlot = slot;
        ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, placeHit);
        if (result.isAccepted()) player.swingHand(Hand.MAIN_HAND);
        player.getInventory().selectedSlot = previous;
        collectCooldown = 2;
    }

    private static boolean inRegion(BlockPos p, BlockPos c) {
        return Math.abs(p.getX() - c.getX()) <= SCAN
            && Math.abs(p.getY() - c.getY()) <= SCAN
            && Math.abs(p.getZ() - c.getZ()) <= SCAN;
    }

    /** Szuka pelnego bloku obok wody, do ktorego mozna przykleic dzwignie. */
    private static BlockHitResult attachFace(World world, BlockPos water) {
        for (Direction d : Direction.values()) {
            BlockPos neighbor = water.offset(d);
            Direction side = d.getOpposite();
            BlockState ns = world.getBlockState(neighbor);
            if (!ns.isSideSolidFullSquare(world, neighbor, side)) continue;
            Vec3d v = Vec3d.ofCenter(neighbor).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            return new BlockHitResult(v, side, neighbor, false);
        }
        return null;
    }

    private static int findLever(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.LEVER)) return i;
        }
        return -1;
    }

    private static int findWaterBucket(ClientPlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) return i;
        }
        return -1;
    }
}
