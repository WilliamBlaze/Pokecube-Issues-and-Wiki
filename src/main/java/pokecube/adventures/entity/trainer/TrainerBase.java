package pokecube.adventures.entity.trainer;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.MerchantContainer;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MerchantOffer;
import net.minecraft.item.MerchantOffers;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import pokecube.adventures.PokecubeAdv;
import pokecube.adventures.capabilities.CapabilityHasPokemobs.DefaultPokemobs;
import pokecube.adventures.capabilities.CapabilityHasRewards.IHasRewards;
import pokecube.adventures.capabilities.CapabilityHasTrades.IHasTrades;
import pokecube.adventures.capabilities.CapabilityNPCAIStates.IHasNPCAIStates;
import pokecube.adventures.capabilities.CapabilityNPCAIStates.IHasNPCAIStates.AIState;
import pokecube.adventures.capabilities.CapabilityNPCMessages.IHasMessages;
import pokecube.adventures.capabilities.TrainerCaps;
import pokecube.adventures.capabilities.utils.TypeTrainer;
import pokecube.adventures.utils.TrainerTracker;
import pokecube.core.entity.npc.NpcMob;
import pokecube.core.entity.npc.NpcType;
import pokecube.core.handlers.events.EventsHandler;
import pokecube.core.handlers.events.SpawnHandler;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.utils.Tools;
import thut.api.item.ItemList;
import thut.api.maths.Vector3;

public abstract class TrainerBase extends NpcMob
{
    public static final ResourceLocation BRIBE = new ResourceLocation(PokecubeAdv.MODID, "trainer_bribe");

    public List<IPokemob>  currentPokemobs = new ArrayList<>();
    public DefaultPokemobs pokemobsCap;
    public IHasMessages    messages;
    public IHasRewards     rewardsCap;
    public IHasNPCAIStates aiStates;
    public IHasTrades      trades;

    int     despawncounter = 0;
    boolean fixedMobs      = false;

    protected TrainerBase(final EntityType<? extends TrainerBase> type, final World worldIn)
    {
        super(type, worldIn);
        this.pokemobsCap = (DefaultPokemobs) this.getCapability(TrainerCaps.HASPOKEMOBS_CAP).orElse(null);
        this.rewardsCap = this.getCapability(TrainerCaps.REWARDS_CAP).orElse(null);
        this.messages = this.getCapability(TrainerCaps.MESSAGES_CAP).orElse(null);
        this.aiStates = this.getCapability(TrainerCaps.AISTATES_CAP).orElse(null);
        this.trades = this.getCapability(TrainerCaps.TRADES_CAP).orElse(null);

        this.aiStates.setAIState(AIState.TRADES, PokecubeAdv.config.trainersTradeItems
                || PokecubeAdv.config.trainersTradeMobs);
    }

    protected boolean canTrade(final PlayerEntity player)
    {
        final boolean friend = this.pokemobsCap.friendlyCooldown >= 0;
        final boolean pity = this.pokemobsCap.defeated(player);
        final boolean lost = this.pokemobsCap.defeatedBy(player);
        return this.aiStates.getAIState(AIState.TRADES) && (friend || pity || lost);
    }

    @Override
    public ResourceLocation getBaseTex()
    {
        return PokecubeAdv.proxy.getTrainerSkin(this, (TypeTrainer) this.getNpcType(), (byte) (this.isMale() ? 1 : 2));
    }

    @Override
    public ActionResultType func_230254_b_(final PlayerEntity player, final Hand hand)
    {
        final ItemStack stack = player.getHeldItem(hand);
        if (player.abilities.isCreativeMode && player.isCrouching())
        {
            if (this.pokemobsCap.getType() != null && !this.getEntityWorld().isRemote && stack.isEmpty())
            {
                String message = this.getName() + " " + this.aiStates.getAIState(AIState.STATIONARY) + " "
                        + this.pokemobsCap.countPokemon() + " ";
                for (int ind = 0; ind < this.pokemobsCap.getMaxPokemobCount(); ind++)
                {
                    final ItemStack i = this.pokemobsCap.getPokemob(ind);
                    if (!i.isEmpty()) message += i.getDisplayName() + " ";
                }
                player.sendMessage(new StringTextComponent(message), Util.DUMMY_UUID);
            }
            else if (!this.getEntityWorld().isRemote && player.isCrouching() && player.getHeldItemMainhand()
                    .getItem() == Items.STICK) this.pokemobsCap.throwCubeAt(player);
            return ActionResultType.func_233537_a_(this.world.isRemote);
        }
        else if (ItemList.is(TrainerBase.BRIBE, stack) && this.pokemobsCap.friendlyCooldown <= 0 && !this.getOffers()
                .isEmpty())
        {
            stack.split(1);
            player.setHeldItem(hand, stack);
            this.pokemobsCap.onSetTarget(null);
            for (final IPokemob pokemob : this.currentPokemobs)
                pokemob.onRecall(false);
            this.pokemobsCap.friendlyCooldown = 2400;
            this.playCelebrateSound();
            return ActionResultType.func_233537_a_(this.world.isRemote);
        }
        else if (this.canTrade(player))
        {
            final boolean customer = player == this.getCustomer();
            if (customer) return ActionResultType.func_233537_a_(this.world.isRemote);
            this.setCustomer(player);
            if (!this.fixedTrades)
            {
                this.resetTrades();
                // This re-fills the default trades
                this.getOffers();
                // This adds in pokemobs to trade.
                this.addMobTrades(player, stack);
            }
            if (!this.getOffers().isEmpty()) this.openMerchantContainer(player, this.getDisplayName(), 0);
            else this.setCustomer(null);
            return ActionResultType.func_233537_a_(this.world.isRemote);
        }
        else if (this.pokemobsCap.getCooldown() <= 0 && stack.getItem() == Items.STICK) this.pokemobsCap.onSetTarget(
                player);

        return ActionResultType.PASS;
    }

    @Override
    public void tick()
    {
        this.invuln = true;
        if (PokecubeAdv.config.trainerAIPause)
        {
            final PlayerEntity near = this.getEntityWorld().getClosestPlayer(this, -1);
            if (near != null)
            {
                final float dist = near.getDistance(this);
                if (dist > PokecubeAdv.config.aiPauseDistance) return;
            }
        }
        this.invuln = false;
        super.tick();
    }

    private boolean checkedMobs = false;

    @Override
    public void onAddedToWorld()
    {
        TrainerTracker.add(this);
        super.onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld()
    {
        TrainerTracker.removeTrainer(this);
        super.onRemovedFromWorld();
    }

    @Override
    public void livingTick()
    {
        super.livingTick();
        if (!this.isServerWorld()) return;

        ItemStack cube = this.pokemobsCap.getNextPokemob();
        ItemStack reward = this.rewardsCap.getRewards().isEmpty() ? ItemStack.EMPTY
                : this.rewardsCap.getRewards().get(0).stack;
        if (this.pokemobsCap.getCooldown() > this.world.getGameTime())
        {
            cube = ItemStack.EMPTY;
            reward = ItemStack.EMPTY;
        }
        this.setHeldItem(Hand.MAIN_HAND, cube);
        this.setHeldItem(Hand.OFF_HAND, reward);

        if (this.pokemobsCap.countPokemon() == 0 && !this.fixedMobs)
        {
            final TypeTrainer type = this.pokemobsCap.getType();
            if (type != null && !type.pokemon.isEmpty() && !this.checkedMobs)
            {
                this.checkedMobs = true;
                final int level = SpawnHandler.getSpawnLevel(this.getEntityWorld(), Vector3.getNewVector().set(this),
                        type.pokemon.get(0));
                this.initTeam(level);
                type.initTrainerItems(this);
            }
            if (PokecubeAdv.config.cullNoMobs)
            {
                // Do not despawn if there is a player nearby.
                if (Tools.isAnyPlayerInRange(10, this)) return;
                this.despawncounter++;
                if (this.despawncounter > 200) this.remove();
                return;
            }
        }
        this.despawncounter = 0;
    }

    @Override
    public void remove()
    {
        EventsHandler.recallAllPokemobs(this);
        super.remove();
    }

    @Override
    public void resetTrades()
    {
        super.resetTrades();
        this.trades.setOffers(this.offers = null);
    }

    @Override
    protected void onVillagerTrade(final MerchantOffer offer)
    {
        this.trades.applyTrade(offer);
        super.onVillagerTrade(offer);
    }

    @Override
    public void setCustomer(final PlayerEntity player)
    {
        this.trades.setCustomer(player);
        super.setCustomer(player);
    }

    @Override
    public void setNpcType(final NpcType type)
    {
        super.setNpcType(type);
        if (this.pokemobsCap != null && type instanceof TypeTrainer) this.pokemobsCap.setType((TypeTrainer) type);
    }

    public abstract void initTeam(int level);

    protected abstract void addMobTrades(final PlayerEntity player, final ItemStack stack);

    @Override
    protected void onSetOffers()
    {
        this.trades.setOffers(this.offers);
    }

    @Override
    public boolean hasXPBar()
    {
        // Not sure what this does, wandering is false, village is true?
        return super.hasXPBar();
    }

    @Override
    public void verifySellingItem(final ItemStack stack)
    {
        this.trades.verify(stack);
        super.verifySellingItem(stack);
    }

    @Override
    public void openMerchantContainer(final PlayerEntity player, final ITextComponent tittle, final int level)
    {
        // This is the player specific get recipes and open inventory thing
        final OptionalInt optionalint = player.openContainer(new SimpleNamedContainerProvider((int_unk_2,
                player_inventory, unk) ->
        {
            return new MerchantContainer(int_unk_2, player_inventory, this);
        }, tittle));
        if (optionalint.isPresent())
        {
            final MerchantOffers merchantoffers = this.getOffers();
            // TODO here we add in a hook to see if we want to trade pokemobs.
            if (!merchantoffers.isEmpty()) player.openMerchantContainer(optionalint.getAsInt(), merchantoffers, level,
                    this.getXp(), this.hasXPBar(), true);
        }

    }

    /** @return the male */
    @Override
    public boolean isMale()
    {
        return this.pokemobsCap.getGender() == 1;
    }

    /**
     * @param male
     *            the male to set
     */
    @Override
    public void setMale(final boolean male)
    {
        super.setMale(male);
        this.pokemobsCap.setGender((byte) (male ? 1 : 2));
    }

    @Override
    public NpcType getNpcType()
    {
        if (this.pokemobsCap == null) return super.getNpcType();
        return this.pokemobsCap.getType();
    }
}
