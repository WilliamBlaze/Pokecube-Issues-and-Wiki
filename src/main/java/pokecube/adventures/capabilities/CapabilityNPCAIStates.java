package pokecube.adventures.capabilities;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class CapabilityNPCAIStates
{
    public static class DefaultAIStates implements IHasNPCAIStates, ICapabilitySerializable<INBT>
    {
        int   state = 0;
        float direction;

        private final LazyOptional<IHasNPCAIStates> holder = LazyOptional.of(() -> this);

        public DefaultAIStates()
        {
        }

        @Override
        public void deserializeNBT(final INBT nbt)
        {
            CapabilityNPCAIStates.storage.readNBT(TrainerCaps.AISTATES_CAP, this, null, nbt);
        }

        @Override
        public boolean getAIState(final AIState state)
        {
            return (this.state & state.mask) > 0;
        }

        @Override
        public <T> LazyOptional<T> getCapability(final Capability<T> cap, final Direction side)
        {
            return TrainerCaps.AISTATES_CAP.orEmpty(cap, this.holder);
        }

        @Override
        public float getDirection()
        {
            return this.direction;
        }

        @Override
        public int getTotalState()
        {
            return this.state;
        }

        @Override
        public INBT serializeNBT()
        {
            return CapabilityNPCAIStates.storage.writeNBT(TrainerCaps.AISTATES_CAP, this, null);
        }

        @Override
        public void setAIState(final AIState state, final boolean flag)
        {
            if (flag) this.state = Integer.valueOf(this.state | state.mask);
            else this.state = Integer.valueOf(this.state & -state.mask - 1);
        }

        @Override
        public void setDirection(final float direction)
        {
            this.direction = direction;
        }

        @Override
        public void setTotalState(final int state)
        {
            this.state = state;
        }

    }

    public static interface IHasNPCAIStates
    {
        public static enum AIState
        {
            STATIONARY(1 << 0), INBATTLE(1 << 1, true), THROWING(1 << 2, true), PERMFRIENDLY(1 << 3), FIXEDDIRECTION(
                    1 << 4), MATES(1 << 5), INVULNERABLE(1 << 6), TRADES(1 << 7);

            private final int mask;

            private final boolean temporary;

            private AIState(final int mask)
            {
                this(mask, false);
            }

            private AIState(final int mask, final boolean temporary)
            {
                this.mask = mask;
                this.temporary = temporary;
            }

            public int getMask()
            {
                return this.mask;
            }

            public boolean isTemporary()
            {
                return temporary;
            }
        }

        boolean getAIState(AIState state);

        /** @return Direction to face if FIXEDDIRECTION */
        public float getDirection();

        int getTotalState();

        void setAIState(AIState state, boolean flag);

        /**
         * @param direction
         *            Direction to face if FIXEDDIRECTION
         */
        public void setDirection(float direction);

        void setTotalState(int state);
    }

    public static class Storage implements Capability.IStorage<IHasNPCAIStates>
    {

        @Override
        public void readNBT(final Capability<IHasNPCAIStates> capability, final IHasNPCAIStates instance,
                final Direction side, final INBT nbt)
        {
            if (nbt instanceof IntNBT) instance.setTotalState(((IntNBT) nbt).getInt());
            else if (nbt instanceof CompoundNBT)
            {
                final CompoundNBT tag = (CompoundNBT) nbt;
                instance.setTotalState(tag.getInt("AI"));
                instance.setDirection(tag.getFloat("D"));
            }
        }

        @Override
        public INBT writeNBT(final Capability<IHasNPCAIStates> capability, final IHasNPCAIStates instance,
                final Direction side)
        {
            final CompoundNBT tag = new CompoundNBT();
            tag.putInt("AI", instance.getTotalState());
            tag.putFloat("D", instance.getDirection());
            return tag;
        }

    }

    public static Storage storage;
}
