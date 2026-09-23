package com.kirugoldzzzz.lootrift;

import com.kirugoldzzzz.lootrift.common.text.Messages;
import com.kirugoldzzzz.lootrift.common.text.Mini;
import com.kirugoldzzzz.lootrift.common.text.Numbers;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class CrateOpener {

    private final CrateService service;
    private final CrateAnimator animator;

    private CratePreviewMenu previewMenu;
    private CrateRewardMenu rewardMenu;
    private CrateBulkAnimator bulkAnimator;

    public CrateOpener(CrateService service, CrateAnimator animator) {
        this.service = service;
        this.animator = animator;
    }

    public void bind(CratePreviewMenu menu) {
        this.previewMenu = menu;
    }

    public void bind(CrateRewardMenu menu) {
        this.rewardMenu = menu;
    }

    public void bind(CrateBulkAnimator animator) {
        this.bulkAnimator = animator;
    }

    public boolean open(Player player, Crate crate, Runnable onFinish) {
        return open(player, crate, onFinish, null);
    }

    public boolean open(Player player, Crate crate, Runnable onFinish, Runnable afterReveal) {
        CrateService.Refusal refusal = service.check(player, crate);
        if (refusal != CrateService.Refusal.NONE) {
            refuse(player, crate, refusal);
            return false;
        }
        Optional<CrateService.Session> session = service.begin(player, crate);
        if (session.isEmpty()) {
            refuse(player, crate, CrateService.Refusal.NO_KEY);
            return false;
        }
        animator.play(player, session.get(), onFinish, afterReveal);
        return true;
    }

    public boolean openBulk(Player player, Crate crate, int requested, Runnable onFinish) {
        return openBulk(player, crate, requested, onFinish, null);
    }

    public boolean openBulk(Player player, Crate crate, int requested, Runnable onFinish,
                            Runnable afterReveal) {
        CrateService.Refusal refusal = service.check(player, crate);
        if (refusal != CrateService.Refusal.NONE) {
            refuse(player, crate, refusal);
            return false;
        }
        CrateService.Bulk bulk = service.openBulk(player, crate, requested);
        if (bulk.isEmpty()) {
            refuse(player, crate, CrateService.Refusal.NO_KEY);
            return false;
        }
        Messages.send(player, "crates.bulk-opened",
                Mini.value("amount", String.valueOf(bulk.opened())),
                Mini.styled("crate", crate.displayName()),
                Mini.value("rewards", String.valueOf(bulk.grants().size())));
        if (bulkAnimator != null) {
            bulkAnimator.play(player, crate, bulk, onFinish, afterReveal);
        } else if (rewardMenu != null) {
            if (afterReveal != null) {
                afterReveal.run();
            }
            rewardMenu.openBulk(player, crate, bulk, onFinish);
        }
        return true;
    }

    public void preview(Player player, Crate crate, Runnable back) {
        if (previewMenu == null) {
            return;
        }
        previewMenu.open(player, crate, back);
    }

    private void refuse(Player player, Crate crate, CrateService.Refusal refusal) {
        service.effects().deny(player);
        String key = refusal.messageKey();
        if (key == null) {
            return;
        }
        Messages.send(player, key,
                Mini.styled("crate", crate.displayName()),
                Mini.value("time", Numbers.duration(service.cooldownRemaining(player, crate))));
    }
}
