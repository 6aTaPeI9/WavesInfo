package waveinfo;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.Pathfinder.Flowfield;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

@SuppressWarnings("unchecked")
public class PathLineDraw {
    /** movement type indices, used with settings keys and checkboxes */
    public static final int ground = 0, naval = 1, legs = 2, hover = 3, flying = 4;

    /** how often (in frames) cached paths are recalculated */
    private static final int recalcInterval = 30;

    /** random attack targets, same set as Pathfinder.EnemyCoreField */
    private static final BlockFlag[] randomTargets = {
        BlockFlag.storage, BlockFlag.generator, BlockFlag.launchPad, BlockFlag.factory,
        BlockFlag.repair, BlockFlag.battery, BlockFlag.reactor, BlockFlag.drill
    };

    private static final int[] costTypes = {
        Pathfinder.costGround,
        Pathfinder.costNaval,
        Pathfinder.costLegs,
        Pathfinder.costHover,
        Pathfinder.costNone
    };

    private static final Color[] colors = {
        Color.scarlet,
        Color.sky,
        Color.lime,
        Color.orange,
        Color.magenta
    };

    private static final String[] settingsKeys = {
        "waveinfo-path-ground",
        "waveinfo-path-naval",
        "waveinfo-path-legs",
        "waveinfo-path-hover",
        "waveinfo-path-flying"
    };

    private static final boolean[] enabled = new boolean[5];
    private static final Seq<Seq<Vec2>>[] paths = new Seq[5];
    private static int counter;

    public static void init() {
        for (int type = 0; type < 5; type++) {
            enabled[type] = Core.settings.getBool(settingsKeys[type]);
            paths[type] = new Seq<>();
        }

        Events.run(Trigger.draw, () -> {
            if (state == null || !state.isGame() || state.rules.spawns.isEmpty() || spawner.getSpawns().isEmpty())
                return;

            if (++counter % recalcInterval == 0) recalc();

            draw();
        });
    }

    public static void setEnabled(int type, boolean value) {
        enabled[type] = value;
    }

    private static void recalc() {
        //ground, naval, legs and hover follow the flow field gradient
        for (int type = ground; type <= hover; type++) {
            recalcFlowfield(type);
        }
        //flyers move in straight lines from their actual spawn points
        recalcFlying();
    }

    /** Traces a path along the gradient of the movement type's flow field. */
    private static void recalcFlowfield(int type) {
        paths[type].clear();
        if (!enabled[type]) return;

        Flowfield field = pathfinder.getField(state.rules.waveTeam, costTypes[type], Pathfinder.fieldCore);
        //only trace once the field has a stable, complete weight map
        if (!field.hasCompleteWeights()) return;

        int[] weights = field.completeWeights;
        int maxSteps = (world.width() + world.height()) * 2;

        for (Tile spawn : spawner.getSpawns()) {
            Seq<Vec2> path = new Seq<>();
            Tile current = spawn;
            path.add(new Vec2(current.worldx(), current.worldy()));

            for (int i = 0; i < maxSteps; i++) {
                Tile next = pathfinder.getTargetTile(current, field, true);
                if (next == null || next == current) break;

                //weights strictly decrease towards a target; a non-decreasing step means a loop or finished path
                if (weights[next.x + next.y * field.width] >= weights[current.x + current.y * field.width]) break;

                path.add(new Vec2(next.worldx(), next.worldy()));
                current = next;
            }

            if (path.size > 1) paths[type].add(path);
        }
    }

    /**
     * Flyers spawn off the map edge (unless airUseSpawns) and fly straight to their target,
     * so each spawn group gets its own line from its real spawn point to its target.
     */
    private static void recalcFlying() {
        paths[flying].clear();
        if (!enabled[flying]) return;

        boolean airUseSpawns = state.rules.airUseSpawns;

        for (SpawnGroup group : state.rules.spawns) {
            if (group.type == null || !group.type.flying) continue;

            for (Tile tile : spawner.getSpawns()) {
                if (group.spawn != -1 && group.spawn != tile.pos()) continue;

                //same spawn positioning as WaveSpawner.eachFlyerSpawn
                float sx, sy;
                if (!airUseSpawns) {
                    float angle = Angles.angle(world.width() / 2f, world.height() / 2f, tile.x, tile.y);
                    float trns = Math.max(world.width(), world.height()) * Mathf.sqrt2 * tilesize;
                    sx = Mathf.clamp(world.width() * tilesize / 2f + Angles.trnsx(angle, trns), 0f, world.width() * tilesize);
                    sy = Mathf.clamp(world.height() * tilesize / 2f + Angles.trnsy(angle, trns), 0f, world.height() * tilesize);
                } else {
                    sx = tile.worldx();
                    sy = tile.worldy();
                }

                Vec2 target = flyingTarget(group, sx, sy);
                if (target == null) continue;

                paths[flying].add(Seq.with(new Vec2(sx, sy), target));
            }
        }
    }

    /** Approximates the destination of a flyer of this spawn group. */
    private static @Nullable Vec2 flyingTarget(SpawnGroup group, float sx, float sy) {
        Team wave = state.rules.waveTeam;
        UnitType type = group.type;

        if (state.rules.randomWaveAI) {
            //random flag per wave/type, mirroring Pathfinder.EnemyCoreField behavior
            Rand rand = new Rand();
            rand.setSeed(state.rules.waves ? state.wave : (int)(state.tick / 5400) + type.id);

            for (int attempt = 0; attempt < 5; attempt++) {
                Building nearest = nearestEnemy(wave, randomTargets[rand.random(randomTargets.length - 1)], sx, sy);
                if (nearest != null) return new Vec2(nearest.x, nearest.y);
            }
        } else {
            for (BlockFlag flag : type.targetFlags) {
                if (flag == null) continue;
                Building nearest = nearestEnemy(wave, flag, sx, sy);
                if (nearest != null) return new Vec2(nearest.x, nearest.y);
            }
        }

        //fallback: fly at the enemy core
        Building core = nearestEnemy(wave, BlockFlag.core, sx, sy);
        return core == null ? null : new Vec2(core.x, core.y);
    }

    /** Nearest targetable enemy building with the given flag. */
    private static @Nullable Building nearestEnemy(Team team, BlockFlag flag, float x, float y) {
        Building nearest = null;
        float best = Float.MAX_VALUE;
        for (Building b : indexer.getEnemy(team, flag)) {
            if (!b.block.targetable) continue;
            float dist = Mathf.dst2(b.x, b.y, x, y);
            if (dist < best) {
                best = dist;
                nearest = b;
            }
        }
        return nearest;
    }

    private static void draw() {
        for (int type = 0; type < 5; type++) {
            if (!enabled[type] || paths[type].isEmpty()) continue;

            Draw.z(Layer.overlayUI);
            Lines.stroke(1.5f, colors[type]);

            for (Seq<Vec2> path : paths[type]) {
                for (int i = 0; i < path.size - 1; i++) {
                    Vec2 from = path.get(i);
                    Vec2 to = path.get(i + 1);
                    if (from == null || to == null) continue;
                    Lines.line(from.x, from.y, to.x, to.y);
                }
            }
        }
        Draw.reset();
    }
}
