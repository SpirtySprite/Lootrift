package com.kirugoldzzzz.lootrift.common.integration;

import com.kirugoldzzzz.lootrift.common.text.Tr;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Map;

public final class ModelEngineBridge {

    private static final String PLUGIN = "ModelEngine";
    private static final String API = "com.ticxo.modelengine.api.ModelEngineAPI";
    private static final String MODELED = "com.ticxo.modelengine.api.model.ModeledEntity";
    private static final String ACTIVE = "com.ticxo.modelengine.api.model.ActiveModel";
    private static final String HANDLER = "com.ticxo.modelengine.api.animation.handler.AnimationHandler";

    private static volatile boolean resolved;
    private static volatile boolean present;
    private static volatile String failure;

    private static Method createActiveModel;
    private static Method getOrCreateModeledEntity;
    private static Method getModeledEntity;
    private static Method addModel;
    private static Method setBaseEntityVisible;
    private static Method getModels;
    private static Method destroy;
    private static Method setScale;
    private static Method getAnimationHandler;
    private static Method playAnimation;
    private static Method forceStopAnimation;
    private static Method setYBodyRotImmediately;
    private static Method setYHeadRotImmediately;

    private ModelEngineBridge() {
    }

    public static boolean available() {
        resolve();
        return present;
    }

    public static String failureReason() {
        resolve();
        return failure;
    }

    public static void reset() {
        resolved = false;
        present = false;
        failure = null;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            if (Bukkit.getPluginManager().getPlugin(PLUGIN) == null) {
                failure = Tr.t("greffon absent");
                return;
            }
            Class<?> api = Class.forName(API);
            Class<?> modeled = Class.forName(MODELED);
            Class<?> active = Class.forName(ACTIVE);
            Class<?> handler = Class.forName(HANDLER);

            createActiveModel = api.getMethod("createActiveModel", String.class);
            getOrCreateModeledEntity = api.getMethod("getOrCreateModeledEntity", Entity.class);
            getModeledEntity = api.getMethod("getModeledEntity", Entity.class);
            addModel = modeled.getMethod("addModel", active, boolean.class);
            setBaseEntityVisible = modeled.getMethod("setBaseEntityVisible", boolean.class);
            getModels = modeled.getMethod("getModels");
            destroy = modeled.getMethod("destroy");
            setScale = active.getMethod("setScale", double.class);
            getAnimationHandler = active.getMethod("getAnimationHandler");
            playAnimation = handler.getMethod("playAnimation", String.class,
                    double.class, double.class, double.class, boolean.class);
            forceStopAnimation = handler.getMethod("forceStopAnimation", String.class);
            setYBodyRotImmediately = modeled.getMethod("setYBodyRotImmediately", float.class);
            setYHeadRotImmediately = modeled.getMethod("setYHeadRotImmediately", float.class);
            present = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError blocked) {
            present = false;
            failure = blocked.getClass().getSimpleName() + ": " + blocked.getMessage();
        }
    }

    public static boolean attach(Entity base, String blueprint, double scale) {
        if (!available() || blueprint == null || blueprint.isBlank()) {
            return false;
        }
        try {
            Object model = createActiveModel.invoke(null, blueprint);
            if (model == null) {
                return false;
            }
            Object modeled = getOrCreateModeledEntity.invoke(null, base);
            if (modeled == null) {
                return false;
            }
            addModel.invoke(modeled, model, true);
            setBaseEntityVisible.invoke(modeled, false);
            if (scale > 0.0D && Math.abs(scale - 1.0D) > 0.001D) {
                setScale.invoke(model, scale);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException blocked) {
            return false;
        }
    }

    public static boolean play(Entity base, String animation, double lerpIn, double lerpOut,
                               double speed, boolean force) {
        if (!available() || animation == null || animation.isBlank()) {
            return false;
        }
        try {
            Object modeled = getModeledEntity.invoke(null, base);
            if (modeled == null) {
                return false;
            }
            Map<?, ?> models = (Map<?, ?>) getModels.invoke(modeled);
            boolean played = false;
            for (Object model : models.values()) {
                Object handler = getAnimationHandler.invoke(model);
                if (handler == null) {
                    continue;
                }
                playAnimation.invoke(handler, animation, lerpIn, lerpOut, speed, force);
                played = true;
            }
            return played;
        } catch (ReflectiveOperationException | RuntimeException blocked) {
            return false;
        }
    }

    public static boolean stop(Entity base, String animation) {
        if (!available() || animation == null || animation.isBlank()) {
            return false;
        }
        try {
            Object modeled = getModeledEntity.invoke(null, base);
            if (modeled == null) {
                return false;
            }
            Map<?, ?> models = (Map<?, ?>) getModels.invoke(modeled);
            boolean stopped = false;
            for (Object model : models.values()) {
                Object handler = getAnimationHandler.invoke(model);
                if (handler == null) {
                    continue;
                }
                forceStopAnimation.invoke(handler, animation);
                stopped = true;
            }
            return stopped;
        } catch (ReflectiveOperationException | RuntimeException blocked) {
            return false;
        }
    }

    public static boolean face(Entity base, float yaw) {
        if (!available()) {
            return false;
        }
        try {
            Object modeled = getModeledEntity.invoke(null, base);
            if (modeled == null) {
                return false;
            }
            setYBodyRotImmediately.invoke(modeled, yaw);
            setYHeadRotImmediately.invoke(modeled, yaw);
            return true;
        } catch (ReflectiveOperationException | RuntimeException blocked) {
            return false;
        }
    }

    public static void detach(Entity base) {
        if (!available()) {
            return;
        }
        try {
            Object modeled = getModeledEntity.invoke(null, base);
            if (modeled != null) {
                destroy.invoke(modeled);
            }
        } catch (ReflectiveOperationException | RuntimeException blocked) {
            failure = blocked.getClass().getSimpleName();
        }
    }
}
