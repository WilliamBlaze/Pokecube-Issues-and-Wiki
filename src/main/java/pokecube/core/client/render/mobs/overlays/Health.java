package pokecube.core.client.render.mobs.overlays;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.Quaternion;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.ITextComponent;
import pokecube.core.PokecubeCore;
import pokecube.core.database.PokedexEntry;
import pokecube.core.database.stats.StatsCollector;
import pokecube.core.handlers.Config;
import pokecube.core.handlers.playerdata.PokecubePlayerStats;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.interfaces.capabilities.CapabilityPokemob;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import pokecube.core.interfaces.pokemob.ai.GeneralStates;
import pokecube.core.utils.Tools;
import thut.core.common.handlers.PlayerDataHandler;

/**
 * This health renderer is directly based on Neat vy Vaziki, which can be found
 * here: https://github.com/Vazkii/Neat This version has been modified to only
 * apply to pokemobs, as well as to show level, gender and exp. I have also
 * modified the nametags to indicate ownership
 */
public class Health
{
    static List<LivingEntity> renderedEntities = new ArrayList<>();

    private static final RenderType TYPE;
    private static final RenderType BACKGROUND;
    static
    {
        final RenderType.State.Builder builder = RenderType.State.builder();
        builder.depthTest(new RenderState.DepthTestState(515));
        builder.cull(new RenderState.CullState(true));
        final RenderType.State fg_st = builder.build(false);
        TYPE = RenderType.get("pokecube:mob_health_tag", DefaultVertexFormats.POSITION_COLOR, GL11.GL_QUADS, 256, true,
                false, fg_st);
        final RenderType.State.Builder builder_bg = RenderType.State.builder();

        builder_bg.transparency(new RenderState.TransparencyState("translucent_transparency", () ->
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }, () ->
        {
            RenderSystem.disableBlend();
        }));
        builder_bg.alpha(new RenderState.AlphaState(0.003921569f));
        builder_bg.depthTest(new RenderState.DepthTestState(519));

        final RenderType.State bg_st = builder_bg.build(false);
        BACKGROUND = RenderType.get("pokecube:mob_health_tag_bg", DefaultVertexFormats.POSITION_COLOR, GL11.GL_QUADS,
                256, false, false, bg_st);
    }

    static boolean blend;
    static boolean normalize;
    static boolean lighting;
    static int     src;
    static int     dst;

    public static boolean enabled = true;

    public static Entity getEntityLookedAt(final Entity e)
    {
        return Tools.getPointedEntity(e, 32);
    }

    private static void blit(final IVertexBuilder buffer, final Matrix4f pos, final float x1, final float y1,
            final float x2, final float y2, final float z, final int r, final int g, final int b, final int a,
            final int id)
    {
        if (buffer instanceof BufferBuilder)
        {
            final BufferBuilder buf = (BufferBuilder) buffer;
            if (buf.getVertexFormat().getSize() != 16) return;
        }
        try
        {
            buffer.pos(pos, x1, y1, z).color(r, g, b, a).endVertex();
            buffer.pos(pos, x1, y2, z).color(r, g, b, a).endVertex();
            buffer.pos(pos, x2, y2, z).color(r, g, b, a).endVertex();
            buffer.pos(pos, x2, y1, z).color(r, g, b, a).endVertex();
        }
        catch (final Exception e)
        {
            PokecubeCore.LOGGER.debug("Error drawing a box for healthbar! {} {} {}", buffer, id, e.toString());

        }
    }

    public static void renderHealthBar(final LivingEntity passedEntity, final MatrixStack mat,
            final IRenderTypeBuffer buf, final float partialTicks, final Entity viewPoint)
    {
        if (!Health.enabled) return;

        final Stack<LivingEntity> ridingStack = new Stack<>();

        LivingEntity entity = passedEntity;

        final IPokemob pokemob = CapabilityPokemob.getPokemobFor(entity);
        if (pokemob == null || !entity.addedToChunk) return;
        final Config config = PokecubeCore.getConfig();
        final Minecraft mc = Minecraft.getInstance();
        final EntityRendererManager renderManager = Minecraft.getInstance().getRenderManager();
        if (renderManager == null || renderManager.info == null) return;
        final ActiveRenderInfo viewer = renderManager.info;

        if (!entity.canEntityBeSeen(viewer.getRenderViewEntity())) return;
        if (entity.getPassengers().contains(viewer.getRenderViewEntity())) return;

        final UUID viewerID = viewer.getRenderViewEntity().getUniqueID();

        ridingStack.push(entity);

        while (entity.getRidingEntity() != null && entity.getRidingEntity() instanceof LivingEntity)
        {
            entity = (LivingEntity) entity.getRidingEntity();
            ridingStack.push(entity);
        }

        IVertexBuilder buffer;
        Matrix4f pos;

        mat.push();
        processing:
        {

            final float scale = .02f;
            final float maxHealth = entity.getMaxHealth();
            final float health = Math.min(maxHealth, entity.getHealth());

            if (maxHealth <= 0) break processing;

            final double dy = pokemob.getCombatState(CombatStates.DYNAMAX) ? config.dynamax_scale
                    : pokemob.getPokedexEntry().height * pokemob.getSize();
            mat.translate(0, dy + config.heightAbove, 0);
            Quaternion quaternion;
            quaternion = viewer.getRotation();
            mat.rotate(quaternion);
            mat.scale(scale, scale, scale);

            final float padding = config.backgroundPadding;
            final int bgHeight = config.backgroundHeight;
            final int barHeight1 = config.barHeight;
            float size = config.plateSize;

            final float zshift = -0.001f;
            float zlevel = 0.1f;
            int r = 0;
            int g = 220;
            int b = 0;
            ItemStack stack = ItemStack.EMPTY;
            if (pokemob.getOwner() == viewer.getRenderViewEntity()) stack = entity.getHeldItemMainhand();
            final int armor = entity.getTotalArmorValue();
            final float hue = Math.max(0F, health / maxHealth / 3F - 0.07F);
            final Color color = Color.getHSBColor(hue, 0.8F, 0.8F);
            r = color.getRed();
            g = color.getGreen();
            b = color.getBlue();
            ITextComponent nameComp = pokemob.getDisplayName();
            boolean nametag = pokemob.getGeneralState(GeneralStates.TAMED);
            final PokecubePlayerStats stats = PlayerDataHandler.getInstance().getPlayerData(Minecraft
                    .getInstance().player).getData(PokecubePlayerStats.class);
            PokedexEntry name_entry = pokemob.getPokedexEntry();
            if (name_entry.isMega || name_entry.isGenderForme) name_entry = name_entry.getBaseForme();
            final boolean captureOrHatch = StatsCollector.getCaptured(name_entry, Minecraft.getInstance().player) > 0
                    || StatsCollector.getHatched(name_entry, Minecraft.getInstance().player) > 0;
            boolean scanned = false;
            nametag = nametag || captureOrHatch || (scanned = stats.hasInspected(pokemob.getPokedexEntry()));
            if (!nametag) nameComp.getStyle().setObfuscated(true);
            if (entity instanceof MobEntity && ((MobEntity) entity).hasCustomName()) nameComp = ((MobEntity) entity)
                    .getCustomName();
            final float s = 0.5F;
            final String name = I18n.format(nameComp.getFormattedText());
            final float namel = mc.fontRenderer.getStringWidth(name) * s;
            if (namel + 20 > size * 2) size = namel / 2F + 10F;
            final float healthSize = size * (health / maxHealth);
            mat.rotate(Vector3f.YP.rotationDegrees(180));
            mat.rotate(Vector3f.XP.rotationDegrees(180));

            pos = mat.getLast().getPositionMatrix();
            // Background
            if (config.drawBackground)
            {
                buffer = Utils.makeBuilder(Health.BACKGROUND, buf);
                final int a = 32;
                Health.blit(buffer, pos, -size - padding, -bgHeight, size + padding, barHeight1 + padding, zlevel, 0, 0,
                        0, a, 0);
                zlevel += zshift;
            }
            buffer = Utils.makeBuilder(Health.TYPE, buf);

            // Health bar
            // Gray Space
            Health.blit(buffer, pos, -size, 0, size, barHeight1, zlevel, 127, 127, 127, 255, 1);
            zlevel += zshift;

            // Health Bar Fill
            Health.blit(buffer, pos, -size, 0, healthSize * 2 - size, barHeight1, zlevel, r, g, b, 255, 2);
            zlevel += zshift;

            // Exp Bar
            r = 64;
            g = 64;
            b = 255;

            final int br = 15 << 20 | 15 << 4;

            int exp = pokemob.getExp() - Tools.levelToXp(pokemob.getExperienceMode(), pokemob.getLevel());
            float maxExp = Tools.levelToXp(pokemob.getExperienceMode(), pokemob.getLevel() + 1) - Tools.levelToXp(
                    pokemob.getExperienceMode(), pokemob.getLevel());
            if (pokemob.getLevel() == 100) maxExp = exp = 1;
            if (exp < 0 || !pokemob.getGeneralState(GeneralStates.TAMED)) exp = 0;
            final float expSize = size * (exp / maxExp);
            // Gray Space
            Health.blit(buffer, pos, -size, barHeight1, size, barHeight1 + 1, zlevel, 127, 127, 127, 255, 3);
            zlevel += zshift;

            // Exp Bar Fill
            Health.blit(buffer, pos, -size, barHeight1, expSize * 2 - size, barHeight1 + 1, zlevel, r, g, b, 255, 4);
            zlevel += zshift;

            mat.push();
            mat.translate(-size, -4.5F, 0F);
            mat.scale(s, s, s);

            final UUID owner = pokemob.getOwnerId();
            final boolean isOwner = viewerID.equals(owner);
            int colour = isOwner ? config.ownedNameColour
                    : owner == null ? nametag ? scanned ? config.scannedNameColour : config.caughtNamedColour
                            : config.unknownNameColour : config.otherOwnedNameColour;

            mat.push();
            float s1 = 0.75F;
            mat.scale(s1, s1, s1);

            mat.push();
            s1 = 1.5F;
            mat.scale(s1, s1, s1);
            pos = mat.getLast().getPositionMatrix();
            mc.fontRenderer.renderString(name, 0, 0, colour, false, pos, buf, false, 0, br);
            s1 = 0.75F;
            mat.pop();

            final int h = config.hpTextHeight;
            String maxHpStr = "" + (int) (Math.round(maxHealth * 100.0) / 100.0);
            String hpStr = "" + (int) (Math.round(health * 100.0) / 100.0);
            final String healthStr = hpStr + "/" + maxHpStr;
            final String gender = pokemob.getSexe() == IPokemob.MALE ? "\u2642"
                    : pokemob.getSexe() == IPokemob.FEMALE ? "\u2640" : "";
            final String lvlStr = "L." + pokemob.getLevel();

            if (maxHpStr.endsWith(".0")) maxHpStr = maxHpStr.substring(0, maxHpStr.length() - 2);
            if (hpStr.endsWith(".0")) hpStr = hpStr.substring(0, hpStr.length() - 2);
            colour = 0xBBBBBB;
            if (pokemob.getSexe() == IPokemob.MALE) colour = 0x0011CC;
            else if (pokemob.getSexe() == IPokemob.FEMALE) colour = 0xCC5555;
            if (isOwner) mc.fontRenderer.drawString(healthStr, (int) (size / (s * s1)) - mc.fontRenderer.getStringWidth(
                    healthStr) / 2, h, 0xFFFFFFFF);

            pos = mat.getLast().getPositionMatrix();
            final int i2 = (int) (0 * 255.0F) << 24;
            mc.fontRenderer.renderString(lvlStr, 2, h, 0xFFFFFF, false, pos, buf, false, i2, br);
            mc.fontRenderer.renderString(gender, (int) (size / (s * s1) * 2) - 2 - mc.fontRenderer.getStringWidth(
                    gender), h - 1, colour, false, pos, buf, false, i2, br);

            if (PokecubeCore.getConfig().enableDebugInfo && mc.gameSettings.showDebugInfo)
            {
                final String entityID = entity.getEntityString().toString();
                mc.fontRenderer.drawString("ID: \"" + entityID + "\"" + "(" + entity.getEntityId() + ")", 0, h + 16,
                        0xFFFFFFFF);
            }
            mat.pop();

            int off = 0;
            s1 = 0.5F;
            mat.scale(s1, s1, s1);
            mat.translate(size / (s * s1) * 2 - 16, 0F, 0F);

            if (!stack.isEmpty() && config.showHeldItem) Health.renderIcon(entity, mat, buf, off, 0, stack, 16, 16);
            off -= 16;

            if (armor > 0 && config.showArmor)
            {
                int ironArmor = armor % 5;
                int diamondArmor = armor / 5;
                if (!config.groupArmor)
                {
                    ironArmor = armor;
                    diamondArmor = 0;
                }

                stack = new ItemStack(Items.IRON_CHESTPLATE);
                for (int i = 0; i < ironArmor; i++)
                    Health.renderIcon(entity, mat, buf, off, 0, stack, 16, 16);
                off -= 4;

                stack = new ItemStack(Items.DIAMOND_CHESTPLATE);
                for (int i = 0; i < diamondArmor; i++)
                    Health.renderIcon(entity, mat, buf, off, 0, stack, 16, 16);
                off -= 4;
            }

            mat.pop();
        }
        mat.pop();
    }

    @SuppressWarnings("deprecation")
    public static void renderIcon(final LivingEntity mob, final MatrixStack mat, final IRenderTypeBuffer buf,
            final int vertexX, final int vertexY, final ItemStack stack, final int intU, final int intV)
    {
        mat.push();
        try
        {
            mat.translate(vertexX, vertexY + 7, 0);
            mat.scale(20, -20, 1);
            Minecraft.getInstance().getItemRenderer().renderItem(mob, stack,
                    net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType.GUI, false, mat, buf, mob
                            .getEntityWorld(), 15728880, OverlayTexture.DEFAULT_LIGHT);
        }
        catch (final Exception e)
        {
        }
        mat.pop();
    }
}
