package com.phantomstorage.entity.goal;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomPetEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Holds position while ordered to stay (vanilla tameable "sit" flag), within hover bounds. */
public class PhantomStayGoal extends Goal {
    private static final int REFRESH_TICKS = 20;

    private final PhantomPetEntity pet;
    private Vec3 anchor = Vec3.ZERO;
    private int refreshTimer;

    public PhantomStayGoal(PhantomPetEntity pet) {
        this.pet = pet;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.pet.isOrderedToSit();
    }

    @Override
    public boolean canContinueToUse() {
        return this.pet.isOrderedToSit();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.anchor = this.pet.position();
        this.refreshTimer = 0;
        this.pet.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--this.refreshTimer <= 0) {
            this.refreshTimer = REFRESH_TICKS;
            double y = HoverBounds.clampY(this.pet.level(), this.anchor.x, this.anchor.y, this.anchor.z);
            this.anchor = new Vec3(this.anchor.x, y, this.anchor.z);
        }
        this.pet.getMoveControl().setWantedPosition(this.anchor.x, this.anchor.y, this.anchor.z, 0.5);
    }
}
