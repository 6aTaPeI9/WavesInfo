package waveinfo;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.Element;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class WaveInfoDialog {
    public boolean shown;
    private Table main;
    private ScrollPane wavePane;

    public WaveInfoDialog() {
        main = new Table(Styles.black6);
        main.visible(() -> shown && state != null && state.isGame());
        main.update(() -> {
            if (shown && state != null && state.isGame()) {
                main.setPosition(
                    Mathf.clamp(main.x, 0, Core.graphics.getWidth() - main.getWidth()),
                    Mathf.clamp(main.y, 0, Core.graphics.getHeight() - main.getHeight())
                );
            }
        });
        main.touchable = Touchable.enabled;

        main.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                scene.setScrollFocus(main);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                scene.setScrollFocus(null);
            }
        });

        buildUI();

        Events.on(EventType.WorldLoadEvent.class, e -> {
            shown = false;
            rebuildWaveList();
        });
        Events.on(EventType.WaveEvent.class, e -> rebuildWaveList());

        main.pack();
        scene.add(main);
    }

    public void toggle() {
        shown = !shown;
        if (shown) {
            showAtButton();
            rebuildWaveList();
        }
    }

    /** Positions the panel right below the mod button and animates it dropping out. */
    private void showAtButton() {
        Element button = Core.scene.find("waveinfo-button");
        if (button == null) return;

        Vec2 pos = button.localToStageCoordinates(Tmp.v1.set(0, 0));

        main.pack();
        float px = Mathf.clamp(pos.x, 0f, Core.graphics.getWidth() - main.getWidth());
        float py = Mathf.clamp(pos.y - main.getHeight() - 4f, 0f, Core.graphics.getHeight() - main.getHeight());
        main.setPosition(px, py + 14f);

        main.clearActions();
        main.color.a = 0f;
        main.toFront();
        main.actions(
            Actions.translateBy(0f, -14f, 0.15f, Interp.smooth),
            Actions.fadeIn(0.15f)
        );
    }

    private void buildUI() {
        Table header = new Table(Tex.buttonEdge1, t -> {
            t.left();
            t.image(Icon.waves.getRegion()).scaling(Scaling.fill).size(20f);
            t.add("Wave Info").padLeft(10);
        });
        header.addListener(new DragHandleListener());
        main.add(header).growX().height(36f);

        main.row();
        wavePane = new ScrollPane(new Table(), Styles.smallPane);
        wavePane.setFadeScrollBars(false);
        wavePane.setScrollingDisabled(true, false);
        //cap the wave list height and width so the panel stays on screen; taller content scrolls
        main.table(Styles.black8, t -> t.add(wavePane).grow().pad(6f))
            .grow()
            .minWidth(240f)
            .minHeight(150f)
            .maxWidth(360f)
            .maxHeight(Math.max(150f, Core.graphics.getHeight() * 0.3f));

        main.row();
        main.table(Styles.black5, t -> {
            t.left();
            addPathCheck(t, "Ground Path", "waveinfo-path-ground", PathLineDraw.ground, Color.scarlet);
            addPathCheck(t, "Naval Path", "waveinfo-path-naval", PathLineDraw.naval, Color.sky);
            addPathCheck(t, "Legs Path", "waveinfo-path-legs", PathLineDraw.legs, Color.lime);
            t.row();
            addPathCheck(t, "Hover Path", "waveinfo-path-hover", PathLineDraw.hover, Color.orange);
            addPathCheck(t, "Flying Path", "waveinfo-path-flying", PathLineDraw.flying, Color.magenta);
        }).growX().height(64f);
    }

    private void addPathCheck(Table t, String text, String key, int type, Color color) {
        CheckBox box = t.check(text, settings.getBool(key), b -> {
            settings.put(key, b);
            PathLineDraw.setEnabled(type, b);
        }).margin(2f).get();
        box.getLabel().setColor(color);
        styleCheck(box);
    }

    private void styleCheck(CheckBox box) {
        box.getLabel().setFontScale(0.7f);
        box.getImageCell().size(14f);
    }

    private void rebuildWaveList() {
        if (state == null || state.rules.spawns.isEmpty()) {
            wavePane.setWidget(new Table(t -> t.add("[gray]No waves loaded").pad(20f)));
            return;
        }

        wavePane.setWidget(new Table(table -> {
            table.center().defaults().growX();

            int maxWave = state.isCampaign() && state.rules.winWave > 0
                ? state.rules.winWave
                : state.wave + 10;

            for (int i = 1; i <= maxWave; i++) {
                final int index = i;

                if (state.rules.spawns.find(g -> g.getSpawned(index - 1) > 0) == null) continue;

                table.table(waveRow -> {
                    waveRow.left();
                    waveRow.add(String.valueOf(index)).padRight(6f).update(label -> {
                        Color color = Pal.accent;
                        if (state.wave == index) color = Color.red;
                        else if (state.wave - 1 == index && state.enemies > 0)
                            color = Color.red.cpy().shiftHue(Time.time);
                        label.setColor(color);
                    });

                    waveRow.table(groupsTable -> {
                        groupsTable.left().top();

                        ObjectIntMap<SpawnGroup> groups = getWaveGroup(index - 1);
                        Seq<SpawnGroup> sorted = new Seq<>();
                        for (SpawnGroup key : groups.keys()) sorted.add(key);
                        sorted.sort((g1, g2) -> {
                            int boss = Boolean.compare(g1.effect != StatusEffects.boss, g2.effect != StatusEffects.boss);
                            if (boss != 0) return boss;
                            int hitSize = Float.compare(-g1.type.hitSize, -g2.type.hitSize);
                            if (hitSize != 0) return hitSize;
                            return Integer.compare(-g1.type.id, -g2.type.id);
                        });

                        int max = Math.max(1, Math.round((main.getWidth() - 70) / 26));

                        for (int g = 0; g < 4; g++) {
                            final int group = g;
                            Seq<SpawnGroup> groupUnits = sorted.select(sg -> movementGroup(sg.type) == group);
                            if (groupUnits.isEmpty()) continue;

                            groupsTable.table(groupRow -> {
                                groupRow.left();
                                groupRow.add("[gray]" + groupNames[group] + "[]").fontScale(0.55f).width(50f).left().padRight(5f).padTop(3f).top();
                                groupRow.table(unitTable -> {
                                    unitTable.left();
                                    int col = 0;
                                    for (SpawnGroup sg : groupUnits) {
                                        int amount = groups.get(sg);
                                        int spawners = state.rules.waveTeam.cores().size
                                            + (sg.type.flying ? spawner.countFlyerSpawns() : spawner.countGroundSpawns());

                                        Element icon = unitTable.stack(
                                            new Table(ttt -> {
                                                ttt.center();
                                                ttt.image(sg.type.uiIcon).size(iconSmall);
                                                ttt.pack();
                                            }),
                                            new Table(ttt -> {
                                                ttt.bottom().left();
                                                ttt.add(amount + "").fontScale(0.7f);
                                                ttt.pack();
                                            })
                                        ).size(iconSmall + 8).get();

                                        icon.addListener(new Tooltip(to -> {
                                            to.background(Styles.black6);
                                            to.margin(4f).left();
                                            to.add("[stat]" + sg.type.localizedName + "[]");
                                            to.row();
                                            to.add(amount + " [lightgray]x" + spawners + "[]");
                                            float shield = sg.getShield(index - 1);
                                            if (shield > 0) {
                                                to.row();
                                                to.add("[stat]Shield: []" + (int)Math.round(shield));
                                            }
                                            if (sg.effect != null && sg.effect != StatusEffects.none) {
                                                to.row();
                                                to.add(sg.effect.emoji() + "[stat]" + sg.effect.localizedName);
                                            }
                                        }));

                                        if (++col % max == 0) {
                                            unitTable.row();
                                        }
                                    }
                                });
                            }).growX();
                            groupsTable.row();
                        }
                    }).growX().padLeft(8f).padTop(2f).padBottom(2f);
                }).pad(2f);

                table.row();
                table.image().height(3f).color(Pal.gray);
                table.row();
            }
        }));
    }

    private static final String[] groupNames = {"Ground", "Air", "Naval", "Legs"};

    /** Movement category of a unit type: 0 ground, 1 air, 2 naval, 3 legs. */
    private static int movementGroup(UnitType type) {
        if (type.flying) return 1;
        if (type.naval) return 2;
        if (type.allowLegStep) return 3;
        return 0;
    }

    private ObjectIntMap<SpawnGroup> getWaveGroup(int waveIndex) {
        ObjectIntMap<SpawnGroup> groups = new ObjectIntMap<>();
        for (SpawnGroup group : state.rules.spawns) {
            if (group.getSpawned(waveIndex) <= 0) continue;
            SpawnGroup sameTypeKey = null;
            for (SpawnGroup key : groups.keys()) {
                if (key.type == group.type && key.effect != StatusEffects.boss) {
                    sameTypeKey = key;
                    break;
                }
            }
            if (sameTypeKey != null)
                groups.increment(sameTypeKey, sameTypeKey.getSpawned(waveIndex));
            else
                groups.put(group, group.getSpawned(waveIndex));
        }
        return groups;
    }

    private class DragHandleListener extends InputListener {
        float lastX, lastY;

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            Vec2 v = event.listenerActor.localToStageCoordinates(Tmp.v1.set(x, y));
            lastX = v.x;
            lastY = v.y;
            main.toFront();
            return true;
        }

        @Override
        public void touchDragged(InputEvent event, float dx, float dy, int pointer) {
            Vec2 v = event.listenerActor.localToStageCoordinates(Tmp.v1.set(dx, dy));
            main.setPosition(main.x + (v.x - lastX), main.y + (v.y - lastY));
            lastX = v.x;
            lastY = v.y;
        }
    }
}
