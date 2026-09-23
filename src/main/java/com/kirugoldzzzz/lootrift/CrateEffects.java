package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.scheduler.Scheduling;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Palette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;

public final class CrateEffects {

    private static final Duration FADE_IN = Duration.ofMillis(150L);
    private static final Duration STAY = Duration.ofMillis(1600L);
    private static final Duration FADE_OUT = Duration.ofMillis(400L);

    private boolean sounds = true;
    private boolean particles = true;
    private boolean fireworks = true;
    private boolean titles = true;

    public void configure(boolean sounds, boolean particles, boolean fireworks, boolean titles) {
        this.sounds = sounds;
        this.particles = particles;
        this.fireworks = fireworks;
        this.titles = titles;
    }

    public boolean soundsEnabled() {
        return sounds;
    }

    public boolean particlesEnabled() {
        return particles;
    }

    public boolean fireworksEnabled() {
        return fireworks;
    }

    public boolean titlesEnabled() {
        return titles;
    }

    public void start(Player player) {
        if (sounds) {
            player.playSound(player, Sound.BLOCK_CHEST_OPEN, 0.8F, 1.2F);
            player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.8F);
        }
    }

    public void step(Player player, float progress) {
        if (!sounds) {
            return;
        }
        float pitch = 0.9F + Math.min(1.0F, Math.max(0.0F, progress)) * 0.9F;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.35F, pitch);
    }

    public void reveal(Player player, CrateReward reward) {
        CrateRarity rarity = reward.rarity();
        if (sounds) {
            player.playSound(player, rarity.sound(), 0.9F, 1.0F);
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.4F);
        }
        if (particles) {
            Location around = player.getLocation().add(0.0D, 1.0D, 0.0D);
            player.getWorld().spawnParticle(rarity.particle(), around,
                    30 + rarity.tier() * 15, 0.6D, 0.8D, 0.6D, 0.05D);
        }
        if (fireworks && rarity.firework()) {
            launch(player.getLocation(), rarity);
        }
    }

    public void title(Player player, Crate crate, CrateReward reward, Component rewardName) {
        if (!titles) {
            return;
        }
        Component main = Mini.label(reward.rarity().heading(reward.rarity().displayName()));
        Component subtitle = Component.empty()
                .append(Mini.label(Palette.TEXT))
                .append(rewardName);
        player.showTitle(Title.title(main, subtitle,
                Title.Times.times(FADE_IN, STAY, FADE_OUT)));
    }

    public void ambient(Location center, CrateRarity accent) {
        if (!particles) {
            return;
        }
        center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0.0D, 1.1D, 0.0D),
                3, 0.25D, 0.15D, 0.25D, 0.005D);
        center.getWorld().spawnParticle(accent.particle(), center.clone().add(0.0D, 1.3D, 0.0D),
                2, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    public void placement(Location center, boolean placed) {
        if (!sounds) {
            return;
        }
        center.getWorld().playSound(center,
                placed ? Sound.BLOCK_BEACON_ACTIVATE : Sound.BLOCK_BEACON_DEACTIVATE, 0.7F, 1.3F);
    }

    public void deny(Player player) {
        if (sounds) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.7F);
        }
    }

    private void launch(Location location, CrateRarity rarity) {
        Scheduling.region(location, () -> {
            Firework firework = location.getWorld().spawn(location.clone().add(0.0D, 1.0D, 0.0D),
                    Firework.class, spawned -> {
                        FireworkMeta meta = spawned.getFireworkMeta();
                        meta.addEffect(FireworkEffect.builder()
                                .with(rarity == CrateRarity.MYTHIQUE
                                        ? FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE)
                                .withColor(color(rarity))
                                .withFade(Color.WHITE)
                                .trail(true)
                                .flicker(rarity == CrateRarity.MYTHIQUE)
                                .build());
                        meta.setPower(0);
                        spawned.setFireworkMeta(meta);
                        spawned.setSilent(false);
                    });
            firework.detonate();
        });
    }

    private static Color color(CrateRarity rarity) {
        return Color.fromRGB(Integer.parseInt(rarity.hex().substring(1), 16));
    }
}
