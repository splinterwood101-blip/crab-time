package com.crabtime.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Third-person arm pose while holding the grow orb out: the claw arm raised straight in front, following where you look. */
public final class ClawPoses {
    public static final EnumProxy<HumanoidModel.ArmPose> GROW_ORB = new EnumProxy<>(HumanoidModel.ArmPose.class, false,
            (IArmPoseTransformer) (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = -(float) Math.PI / 2f + model.head.xRot;
                part.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -0.1f : 0.1f);
                part.zRot = 0;
            });

    private ClawPoses() {}
}
