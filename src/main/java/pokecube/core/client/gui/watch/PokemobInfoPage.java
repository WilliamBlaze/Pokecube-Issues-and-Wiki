package pokecube.core.client.gui.watch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import pokecube.core.PokecubeCore;
import pokecube.core.client.EventsHandlerClient;
import pokecube.core.client.gui.helper.TexButton;
import pokecube.core.client.gui.helper.TexButton.UVImgRender;
import pokecube.core.client.gui.pokemob.GuiPokemobBase;
import pokecube.core.client.gui.watch.pokemob.Bonus;
import pokecube.core.client.gui.watch.pokemob.Breeding;
import pokecube.core.client.gui.watch.pokemob.Description;
import pokecube.core.client.gui.watch.pokemob.Moves;
import pokecube.core.client.gui.watch.pokemob.PokeInfoPage;
import pokecube.core.client.gui.watch.pokemob.Spawns;
import pokecube.core.client.gui.watch.pokemob.StatsInfo;
import pokecube.core.client.gui.watch.util.PageWithSubPages;
import pokecube.core.database.Database;
import pokecube.core.database.Pokedex;
import pokecube.core.database.PokedexEntry;
import pokecube.core.database.stats.StatsCollector;
import pokecube.core.handlers.PokecubePlayerDataHandler;
import pokecube.core.handlers.playerdata.PokecubePlayerStats;
import pokecube.core.interfaces.IPokemob;
import pokecube.core.network.packets.PacketPokedex;
import pokecube.core.utils.EntityTools;
import pokecube.core.utils.PokeType;
import thut.core.common.ThutCore;
import thut.core.common.handlers.PlayerDataHandler;

public class PokemobInfoPage extends PageWithSubPages<PokeInfoPage>
{
    public static int savedIndex = 0;

    public static List<Class<? extends PokeInfoPage>> PAGELIST = Lists.newArrayList();

    static
    {
        PokemobInfoPage.PAGELIST.add(StatsInfo.class);
        PokemobInfoPage.PAGELIST.add(Bonus.class);
        PokemobInfoPage.PAGELIST.add(Moves.class);
        PokemobInfoPage.PAGELIST.add(Spawns.class);
        PokemobInfoPage.PAGELIST.add(Breeding.class);
        PokemobInfoPage.PAGELIST.add(Description.class);
    }

    private static PokeInfoPage makePage(final Class<? extends PokeInfoPage> clazz, final PokemobInfoPage parent)
    {
        try
        {
            return clazz.getConstructor(PokemobInfoPage.class).newInstance(parent);
        }
        catch (final Exception e)
        {
            PokecubeCore.LOGGER.error("Error with making a page for watch", e);
            return null;
        }
    }

    public IPokemob pokemob;
    IPokemob        renderMob;
    TextFieldWidget search;

    public PokemobInfoPage(final GuiPokeWatch watch)
    {
        super(new TranslationTextComponent("pokewatch.title.pokeinfo"), watch, GuiPokeWatch.TEX_DM,
                GuiPokeWatch.TEX_NM);
        this.pokemob = watch.pokemob;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int b, final int c)
    {
        if (this.search.isFocused()) if (keyCode == GLFW.GLFW_KEY_RIGHT && this.search
                .getCursorPosition() == this.search.text.length())
        {
            String text = this.search.getText();
            text = Database.trim(text);
            final List<String> ret = new ArrayList<>();
            for (final PokedexEntry entry : Database.getSortedFormes())
            {
                final String check = entry.getTrimmedName();
                if (check.startsWith(text))
                {
                    String name = entry.getName();
                    if (name.contains(" ")) name = "\'" + name + "\'";
                    ret.add(name);
                }
            }
            Collections.sort(ret, (o1, o2) ->
            {
                if (o1.startsWith("'") && !o2.startsWith("'")) return 1;
                else if (o2.startsWith("'") && !o1.startsWith("'")) return -1;
                return o1.compareToIgnoreCase(o2);
            });
            ret.replaceAll(t ->
            {
                if (t.startsWith("'") && t.endsWith("'")) t = t.substring(1, t.length() - 1);
                return t;
            });
            String match = text;
            for (final String name : ret)
                if (ThutCore.trim(name).startsWith(ThutCore.trim(match)))
                {
                    match = name;
                    break;
                }
            if (!ret.isEmpty()) this.search.setText(ret.get(0));
            return true;
        }
        else if (keyCode == GLFW.GLFW_KEY_ENTER)
        {
            PokedexEntry entry = this.pokemob.getPokedexEntry();
            final PokedexEntry newEntry = Database.getEntry(this.search.getText());
            // Search to see if maybe it was a translated name put into the
            // search.
            if (newEntry == null)
            {
                for (final PokedexEntry e : Database.getSortedFormes())
                {
                    final String translated = I18n.format(e.getUnlocalizedName());
                    if (translated.equalsIgnoreCase(this.search.getText()))
                    {
                        Database.data2.put(translated, e);
                        entry = e;
                        break;
                    }
                }
                // If the pokedex entry is not actually registered, use
                // old
                // entry.
                if (Pokedex.getInstance().getIndex(entry) == null) entry = null;
            }

            if (newEntry != null)
            {
                this.search.setText(newEntry.getName());
                this.pokemob = EventsHandlerClient.getRenderMob(newEntry, this.watch.player.getEntityWorld());
                this.initPages(this.pokemob);
            }
            else this.search.setText(entry.getName());
            return true;
        }
        return super.keyPressed(keyCode, b, c);
    }

    @Override
    protected PokeInfoPage createPage(final int index)
    {
        return PokemobInfoPage.makePage(PokemobInfoPage.PAGELIST.get(index), this);
    }

    // Search Bar
    @Override
    public void init()
    {
        super.init();
        final int x = this.watch.width / 2 + 90;
        final int y = this.watch.height / 2 + 30;
        this.search = new TextFieldWidget(this.font, x - 200, y - 109, 100, 10, new StringTextComponent(""));
        this.addButton(this.search);
        this.index = PokemobInfoPage.savedIndex;
    }

    public void initPages(IPokemob pokemob)
    {
        if (pokemob == null)
        {
            final String name = PokecubePlayerDataHandler.getCustomDataTag(this.watch.player).getString("WEntry");
            PokedexEntry entry = Database.getEntry(name);
            if (entry == null) entry = Pokedex.getInstance().getFirstEntry();
            pokemob = EventsHandlerClient.getRenderMob(entry, this.watch.player.getEntityWorld());
        }
        this.pokemob = pokemob;
        this.renderMob = pokemob;
        this.search.setVisible(!this.watch.canEdit(pokemob));
        this.search.setText(pokemob.getPokedexEntry().getName());
        PacketPokedex.sendSpecificSpawnsRequest(pokemob.getPokedexEntry());
        PacketPokedex.updateWatchEntry(pokemob.getPokedexEntry());
        // Force close and open the page to update.
        this.changePage(this.index);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int mouseButton)
    {
        final boolean ret = super.mouseClicked(mouseX, mouseY, mouseButton);
        // change gender if clicking on the gender, and shininess otherwise
        if (!this.watch.canEdit(this.pokemob))
        {
            // If it is actually a real mob, swap it out for the fake one.
            if (this.pokemob.getEntity().addedToChunk) this.pokemob = this.renderMob = EventsHandlerClient.getRenderMob(
                    this.pokemob.getPokedexEntry(), this.watch.player.getEntityWorld());

            final int x = (this.watch.width - GuiPokeWatch.GUIW) / 2;
            final int y = (this.watch.height - GuiPokeWatch.GUIH) / 2;
            final int mx = (int) (mouseX - x);
            final int my = (int) (mouseY - y);

            // The box to click goes from (ox, oy) -> (ox + dx, oy + dy)
            int ox = 8;
            int oy = 90;
            int dx = 10;
            int dy = 10;

            // Click for toggling if it is male or female
            if (mx > ox && mx < ox + dx && my > oy && my < oy + dy)
            {
                switch (this.pokemob.getSexe())
                {
                case IPokemob.MALE:
                    this.pokemob.setSexe(IPokemob.FEMALE);
                    break;
                case IPokemob.FEMALE:
                    this.pokemob.setSexe(IPokemob.MALE);
                    break;
                }
                return ret;
            }

            ox = 32;
            oy = 35;
            dx = 90;
            dy = 60;

            // Click for toggling if it is shiny
            if (mx > ox && mx < ox + dx && my > oy && my < oy + dy) if (this.pokemob.getPokedexEntry().hasShiny)
                this.pokemob.setShiny(!this.pokemob.isShiny());
        }
        return ret;
    }

    @Override
    protected int pageCount()
    {
        return PokemobInfoPage.PAGELIST.size();
    }

    @Override
    public void postPageDraw(final MatrixStack mat, final int mouseX, final int mouseY, final float partialTicks)
    {
        final int x = (this.watch.width - GuiPokeWatch.GUIW) / 2 + 80;
        final int y = (this.watch.height - GuiPokeWatch.GUIH) / 2 + 8;
        if (this.pokemob != null)
        {
            // Draw hovored tooltip with pokemob's name
            int mx = mouseX - x;
            int my = mouseY - y;
            if (mx > -35 && mx < -35 + 50) if (my > 25 && my < 25 + 50)
            {
                final List<String> text = Lists.newArrayList();
                text.add(this.pokemob.getPokedexEntry().getTranslatedName().getString());
                if (!this.pokemob.getPokemonNickname().isEmpty()) text.add("\"" + this.pokemob.getPokemonNickname()
                        + "\"");
                GlStateManager.disableDepthTest();
                mx = -65;
                my = 20;
                final int dy = this.font.FONT_HEIGHT;
                int box = 0;
                for (final String s : text)
                    box = Math.max(box, this.font.getStringWidth(s) + 2);

                AbstractGui.fill(mat, x + mx - 1, y + my - 1, x + mx + box + 1, y + my + dy * text.size() + 1,
                        0xFF78C850);
                for (final String s : text)
                {
                    AbstractGui.fill(mat, x + mx, y + my, x + mx + box, y + my + dy, 0xFF000000);
                    this.font.drawString(mat, s, x + mx + 1, y + my, 0xFFFFFFFF);
                    my += dy;
                }
                GlStateManager.enableDepthTest();
            }
        }
    }

    @Override
    public void prePageDraw(final MatrixStack mat, final int mouseX, final int mouseY, final float partialTicks)
    {
        if (this.current_page != null) this.current_page.renderBackground(mat);
        if (!this.watch.canEdit(this.pokemob))
        {
            final String name = PokecubePlayerDataHandler.getCustomDataTag(this.watch.player).getString("WEntry");
            if (!name.equals(this.pokemob.getPokedexEntry().getName()))
            {
                this.search.setText(name);
                final PokedexEntry entry = this.pokemob.getPokedexEntry();
                final PokedexEntry newEntry = Database.getEntry(this.search.getText());
                if (newEntry != null)
                {
                    this.search.setText(newEntry.getName());
                    this.pokemob = EventsHandlerClient.getRenderMob(newEntry, this.watch.player.getEntityWorld());
                    this.initPages(this.pokemob);
                }
                else this.search.setText(entry.getName());
            }
        }

        final int x = (this.watch.width - GuiPokeWatch.GUIW) / 2 + 90;
        final int y = (this.watch.height - GuiPokeWatch.GUIH) / 2 - 5;
        int colour = 0xFF78C850;
        AbstractGui.drawCenteredString(mat, this.font, this.getTitle().getString(), x + 70, y + 17, colour);
        AbstractGui.drawCenteredString(mat, this.font, this.current_page.getTitle().getString(), x + 70, y + 27,
                colour);
        int dx = -76;
        int dy = 10;

        // We only want to draw the level if we are actually inspecting a
        // pokemob.
        // Otherwise this will just show as lvl 1
        boolean drawLevel = this.watch.pokemob != null && this.watch.pokemob.getEntity().addedToChunk;

        // Draw Pokemob
        if (this.pokemob != null)
        {
            if (drawLevel) drawLevel = this.watch.pokemob.getPokedexEntry() == this.pokemob.getPokedexEntry();

            // Draw the icon indicating capture/inspect status.
            this.minecraft.getTextureManager().bindTexture(GuiPokeWatch.TEXTURE_BASE);
            final PokedexEntry pokedexEntry = this.pokemob.getPokedexEntry();
            final PokecubePlayerStats stats = PlayerDataHandler.getInstance().getPlayerData(Minecraft
                    .getInstance().player).getData(PokecubePlayerStats.class);
            boolean fullColour = StatsCollector.getCaptured(pokedexEntry, Minecraft.getInstance().player) > 0
                    || StatsCollector.getHatched(pokedexEntry, Minecraft.getInstance().player) > 0
                    || this.minecraft.player.abilities.isCreativeMode;

            // Megas Inherit colouring from the base form.
            if (!fullColour && pokedexEntry.isMega) fullColour = StatsCollector.getCaptured(pokedexEntry.getBaseForme(),
                    Minecraft.getInstance().player) > 0 || StatsCollector.getHatched(pokedexEntry.getBaseForme(),
                            Minecraft.getInstance().player) > 0;

            IPokemob pokemob = this.renderMob;
            // Copy the stuff to the render mob if this mob is in world
            if (pokemob.getEntity().addedToChunk)
            {
                final IPokemob newMob = EventsHandlerClient.getRenderMob(pokemob.getPokedexEntry(), pokemob.getEntity()
                        .getEntityWorld());
                newMob.read(pokemob.write());
                pokemob = this.renderMob = newMob;
            }
            else
            {
                final LivingEntity player = this.watch.player;
                EntityTools.copyEntityTransforms(pokemob.getEntity(), player);

                // Set colouring accordingly.
                if (fullColour) pokemob.setRGBA(255, 255, 255, 255);
                else if (stats.hasInspected(pokedexEntry)) pokemob.setRGBA(127, 127, 127, 255);
                else pokemob.setRGBA(15, 15, 15, 255);
            }
            pokemob.setSize(1);

            final float yaw = Util.milliTime() / 20;
            dx = -69;
            dy = 40;
            // Draw the actual pokemob
            GuiPokemobBase.renderMob(pokemob.getEntity(), x + dx, y + dy, 0, yaw, 0, yaw, 1.5f);

            // Draw gender, types and lvl
            int genderColor = 0xBBBBBB;
            String gender = "";
            if (this.pokemob.getSexe() == IPokemob.MALE)
            {
                genderColor = 0x0011CC;
                gender = "\u2642";
            }
            else if (this.pokemob.getSexe() == IPokemob.FEMALE)
            {
                genderColor = 0xCC5555;
                gender = "\u2640";
            }
            final String level = "L. " + this.pokemob.getLevel();
            dx = -80;
            dy = 105;
            // Only draw the lvl if it is a real mob, otherwise it will just say
            // L.1
            final int lvlColour = GuiPokeWatch.nightMode ? 0xFFFFFF : 0x444444;
            if (drawLevel) this.font.drawString(mat, level, x + dx, y + dy, lvlColour);
            dx = -80;
            dy = 97;
            this.font.drawString(mat, gender, x + dx, y + dy, genderColor);
            this.pokemob.getType1();
            final String type1 = PokeType.getTranslatedName(this.pokemob.getType1()).getString();
            dx = -80;
            dy = 114;
            colour = this.pokemob.getType1().colour;
            this.font.drawString(mat, type1, x + dx, y + dy, colour);
            dy = 114;
            if (this.pokemob.getType2() != PokeType.unknown)
            {
                final String slash = "/";
                colour = this.pokemob.getType2().colour;
                dx += this.font.getStringWidth(type1);
                this.font.drawString(mat, slash, x + dx, y + dy, 0x444444);
                final String type2 = PokeType.getTranslatedName(this.pokemob.getType2()).getString();
                dx += this.font.getStringWidth(slash);
                this.font.drawString(mat, type2, x + dx, y + dy, colour);
            }
        }
    }

    @Override
    public void preSubOpened()
    {
        this.getEventListeners().clear();
        this.initPages(this.pokemob);
        final int x = this.watch.width / 2;
        final int y = this.watch.height / 2 - 5;
        final ITextComponent next = new StringTextComponent(">");
        final ITextComponent prev = new StringTextComponent("<");
        final TexButton nextBtn = this.addButton(new TexButton(x + 95, y - 74, 12, 12, next, b ->
        {
            this.changePage(this.index + 1);
            PokemobInfoPage.savedIndex = this.index;
        }).setTex(GuiPokeWatch.getWidgetTex()).setRender(new UVImgRender(200, 0, 12, 12)));
        final TexButton prevBtn = this.addButton(new TexButton(x + 81, y - 74, 12, 12, prev, b ->
        {
            this.changePage(this.index - 1);
            PokemobInfoPage.savedIndex = this.index;
        }).setTex(GuiPokeWatch.getWidgetTex()).setRender(new UVImgRender(200, 0, 12, 12)));

        nextBtn.setFGColor(0x444444);
        prevBtn.setFGColor(0x444444);

        this.addButton(this.search);
    }
}
