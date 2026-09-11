// made by haze
package it.haze.hazecrates.gui;

import it.haze.hazecrates.animation.AnimationRegistry;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.CrateDisplayConfig;
import it.haze.hazecrates.crate.KeyType;
import it.haze.hazecrates.crate.RewardDefinition;
import it.haze.hazecrates.item.ItemSpec;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CrateEditorSession {

    public enum Page { CRATE_LIST, CRATE_EDITOR, REWARD_LIST, REWARD_EDITOR }

    private final Player player;
    private Page currentPage = Page.CRATE_LIST;
    private boolean isNew = false;

    private String crateId;
    private String displayName;
    private String keyType;
    private ItemSpec keyItemSpec;
    private String animation;
    private String broadcast;
    private int broadcastThreshold;
    private boolean previewOnLeftClick;
    private List<RewardDefinition> rewards;

    private ItemSpec blockSpec;
    private int blockCustomModelData;
    private boolean glowingOutline;

    private List<String> hologramLines;
    private double hologramHeight;
    private int hologramRefreshTicks;

    private String particleType;
    private int particleCount;
    private double particleRadius;
    private int particleIntervalTicks;

    private String titleText;
    private String titleSubtitle;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    private int editingRewardIndex = -1;
    private String rewardId;
    private ItemSpec rewardItemSpec;
    private String rewardName;
    private int rewardAmount;
    private int rewardWeight;
    private boolean rewardGlow;
    private boolean rewardBroadcast;
    private String rewardPermission;
    private boolean rewardLoreOverride;
    private List<String> rewardLore;
    private List<String> rewardCommands;

    public CrateEditorSession(Player player) {
        this.player = player;
    }

    public void loadFrom(CrateDefinition def) {
        this.isNew             = false;
        this.crateId           = def.id();
        this.displayName       = def.displayName();
        this.keyType           = def.keyType().name();
        this.keyItemSpec       = def.keySpec();
        this.animation         = AnimationRegistry.normalize(def.animation());
        this.broadcast         = def.broadcast();
        this.broadcastThreshold = def.broadcastThreshold();
        this.previewOnLeftClick = def.previewOnLeftClick();
        this.rewards           = new ArrayList<>(def.rewards());

        CrateDisplayConfig d   = def.display();
        this.blockSpec             = d.blockSpec();
        this.blockCustomModelData  = d.blockSpec().customModelData();
        this.glowingOutline        = d.glowingOutline();
        this.hologramLines         = new ArrayList<>(d.hologramLines());
        this.hologramHeight        = d.hologramHeight();
        this.hologramRefreshTicks  = d.hologramRefreshTicks();
        this.particleType          = d.idleEffectId() == null || d.idleEffectId().isBlank()
                ? "none" : d.idleEffectId();
        this.particleCount         = d.particleCount();
        this.particleRadius        = d.particleRadius();
        this.particleIntervalTicks = d.particleIntervalTicks();
        this.titleText             = d.titleLine();
        this.titleSubtitle         = d.subtitleLine();
        this.titleFadeIn           = d.titleFadeIn();
        this.titleStay             = d.titleStay();
        this.titleFadeOut          = d.titleFadeOut();
    }

    public void initNew(String id) {
        CrateDisplayConfig def = CrateDisplayConfig.defaults();
        this.isNew             = true;
        this.crateId           = id;
        this.displayName       = "<gold>" + id + "</gold>";
        this.keyType           = KeyType.PHYSICAL.name();
        this.keyItemSpec       = ItemSpec.vanilla("TRIPWIRE_HOOK");
        this.animation         = "csgo";
        this.broadcast         = "<gold><yellow>%player%</yellow> <gold>ha trovato </gold><white>%reward%</white><gold>!</gold>";
        this.broadcastThreshold = 0;
        this.previewOnLeftClick = true;
        this.rewards           = new ArrayList<>();

        this.blockSpec             = ItemSpec.vanilla("CHEST");
        this.blockCustomModelData  = 0;
        this.glowingOutline        = false;
        this.hologramLines         = new ArrayList<>(List.of(
                "<gold>" + id.toUpperCase() + "</gold>",
                "<gray>Clic destro con una chiave</gray>"
        ));
        this.hologramHeight        = def.hologramHeight();
        this.hologramRefreshTicks  = def.hologramRefreshTicks();
        this.particleType          = "flame_ring";
        this.particleCount         = 0;
        this.particleRadius        = 0;
        this.particleIntervalTicks = 0;
        this.titleText             = "";
        this.titleSubtitle         = "";
        this.titleFadeIn           = def.titleFadeIn();
        this.titleStay             = def.titleStay();
        this.titleFadeOut          = def.titleFadeOut();
    }

    public void loadReward(int index) {
        this.editingRewardIndex = index;
        RewardDefinition r = rewards.get(index);
        this.rewardId         = r.id();
        this.rewardItemSpec   = r.itemSpec();
        this.rewardAmount     = r.icon().getAmount();
        this.rewardWeight     = r.weight();
        this.rewardGlow       = !r.icon().getEnchantments().isEmpty();
        this.rewardBroadcast  = r.broadcast();
        this.rewardPermission = r.permission();
        this.rewardCommands   = new ArrayList<>(r.commands());
        this.rewardLoreOverride = r.loreOverride();
        if (r.loreOverride() && r.icon().hasItemMeta() && r.icon().getItemMeta().hasLore()
                && r.icon().getItemMeta().lore() != null) {
            this.rewardLore = r.icon().getItemMeta().lore().stream()
                    .map(it.haze.hazecrates.config.MessageService::serialize)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } else {
            this.rewardLore = new ArrayList<>();
        }
        if (r.liveProviderAppearance()) {
            this.rewardName = "";
        } else if (r.icon().hasItemMeta() && r.icon().getItemMeta().hasDisplayName()
                && r.icon().getItemMeta().displayName() != null) {
            this.rewardName = it.haze.hazecrates.config.MessageService.serialize(
                    r.icon().getItemMeta().displayName());
            if (it.haze.hazecrates.config.MessageService.isBrokenLegacyHex(this.rewardName)) {
                this.rewardName = "";
            }
        } else {
            this.rewardName = "";
        }
    }

    public void initNewReward() {
        this.editingRewardIndex = -1;
        this.rewardId           = "new_reward";
        this.rewardItemSpec     = ItemSpec.vanilla("STONE");
        this.rewardName         = "";
        this.rewardAmount       = 1;
        this.rewardWeight       = 50;
        this.rewardGlow         = false;
        this.rewardBroadcast    = true;
        this.rewardPermission   = "";
        this.rewardLoreOverride = false;
        this.rewardLore         = new ArrayList<>();
        this.rewardCommands     = new ArrayList<>(List.of("say %player% won a reward!"));
    }

    public Material material() {
        if (blockSpec == null) return Material.CHEST;
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(
                blockSpec.id().toUpperCase(java.util.Locale.ROOT));
        return m != null ? m : Material.CHEST;
    }

    public void material(Material m) {
        this.blockSpec = it.haze.hazecrates.item.ItemSpec.vanilla(m.name(), blockCustomModelData);
    }

    public boolean glow()        { return glowingOutline; }
    public void    glow(boolean g) { this.glowingOutline = g; }

    public Material rewardMaterial() {
        if (rewardItemSpec == null) return Material.STONE;
        org.bukkit.Material m = org.bukkit.Material.matchMaterial(
                rewardItemSpec.id().toUpperCase(java.util.Locale.ROOT));
        return m != null ? m : Material.STONE;
    }

    public void rewardMaterial(Material m) {
        this.rewardItemSpec = ItemSpec.vanilla(m.name());
    }

    public Player player()                          { return player; }
    public Page   currentPage()                     { return currentPage; }
    public void   currentPage(Page p)               { this.currentPage = p; }
    public boolean isNew()                          { return isNew; }

    public String crateId()                         { return crateId; }
    public void   crateId(String id)                { this.crateId = id; }
    public String displayName()                     { return displayName; }
    public void   displayName(String n)             { this.displayName = n; }
    public String keyType()                         { return keyType; }
    public void   keyType(String t)                 { this.keyType = t; }
    public ItemSpec keyItemSpec()                   { return keyItemSpec; }
    public void   keyItemSpec(ItemSpec s)           { this.keyItemSpec = s; }
    public String animation()                       { return animation; }
    public void   animation(String a)               { this.animation = a; }
    public String broadcast()                       { return broadcast; }
    public void   broadcast(String b)               { this.broadcast = b; }
    public int    broadcastThreshold()              { return broadcastThreshold; }
    public void   broadcastThreshold(int t)         { this.broadcastThreshold = t; }
    public boolean previewOnLeftClick()             { return previewOnLeftClick; }
    public void   previewOnLeftClick(boolean v)     { this.previewOnLeftClick = v; }
    public List<RewardDefinition> rewards()         { return rewards; }

    public ItemSpec blockSpec()                     { return blockSpec; }
    public void     blockSpec(ItemSpec s)           { this.blockSpec = s; }
    public int      blockCustomModelData()          { return blockCustomModelData; }
    public void     blockCustomModelData(int v)     { this.blockCustomModelData = v; }
    public boolean  glowingOutline()                { return glowingOutline; }
    public void     glowingOutline(boolean g)       { this.glowingOutline = g; }

    public List<String> hologramLines()             { return hologramLines; }
    public void   hologramLines(List<String> l)     { this.hologramLines = new ArrayList<>(l); }
    public double hologramHeight()                  { return hologramHeight; }
    public void   hologramHeight(double h)          { this.hologramHeight = h; }
    public int    hologramRefreshTicks()            { return hologramRefreshTicks; }
    public void   hologramRefreshTicks(int t)       { this.hologramRefreshTicks = t; }

    public String particleType()                    { return particleType; }
    public void   particleType(String t)            { this.particleType = t; }
    public int    particleCount()                   { return particleCount; }
    public void   particleCount(int c)              { this.particleCount = c; }
    public double particleRadius()                  { return particleRadius; }
    public void   particleRadius(double r)          { this.particleRadius = r; }
    public int    particleIntervalTicks()           { return particleIntervalTicks; }
    public void   particleIntervalTicks(int t)      { this.particleIntervalTicks = t; }

    public String titleText()                       { return titleText; }
    public void   titleText(String t)               { this.titleText = t; }
    public String titleSubtitle()                   { return titleSubtitle; }
    public void   titleSubtitle(String s)           { this.titleSubtitle = s; }
    public int    titleFadeIn()                     { return titleFadeIn; }
    public void   titleFadeIn(int t)                { this.titleFadeIn = t; }
    public int    titleStay()                       { return titleStay; }
    public void   titleStay(int t)                  { this.titleStay = t; }
    public int    titleFadeOut()                    { return titleFadeOut; }
    public void   titleFadeOut(int t)               { this.titleFadeOut = t; }

    public int    editingRewardIndex()              { return editingRewardIndex; }
    public void   editingRewardIndex(int i)         { this.editingRewardIndex = i; }
    public String rewardId()                        { return rewardId; }
    public void   rewardId(String id)               { this.rewardId = id; }
    public ItemSpec rewardItemSpec()                { return rewardItemSpec; }
    public void   rewardItemSpec(ItemSpec s)        { this.rewardItemSpec = s; }
    public String rewardName()                      { return rewardName; }
    public void   rewardName(String n)              { this.rewardName = n; }
    public int    rewardAmount()                    { return rewardAmount; }
    public void   rewardAmount(int a)               { this.rewardAmount = a; }
    public int    rewardWeight()                    { return rewardWeight; }
    public void   rewardWeight(int w)               { this.rewardWeight = w; }
    public boolean rewardGlow()                     { return rewardGlow; }
    public void   rewardGlow(boolean g)             { this.rewardGlow = g; }
    public boolean rewardBroadcast()                { return rewardBroadcast; }
    public void   rewardBroadcast(boolean b)        { this.rewardBroadcast = b; }
    public String rewardPermission()                { return rewardPermission; }
    public void   rewardPermission(String p)        { this.rewardPermission = p; }
    public List<String> rewardLore()                { return rewardLore; }
    public void   rewardLore(List<String> l)        { this.rewardLore = new ArrayList<>(l); }

    public boolean rewardLoreOverride()             { return rewardLoreOverride; }
    public void   rewardLoreOverride(boolean v)     { this.rewardLoreOverride = v; }
    public List<String> rewardCommands()            { return rewardCommands; }
    public void   rewardCommands(List<String> c)    { this.rewardCommands = new ArrayList<>(c); }
}
