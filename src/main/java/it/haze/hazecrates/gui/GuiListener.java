// made by haze
package it.haze.hazecrates.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.haze.hazecrates.HazeCrates;
import it.haze.hazecrates.crate.CrateDefinition;
import it.haze.hazecrates.crate.RewardDefinition;
import it.haze.hazecrates.gui.preview.PreviewHolder;
import it.haze.hazecrates.gui.preview.PreviewInventory;
import it.haze.hazecrates.item.ItemProvider;
import it.haze.hazecrates.item.ItemSpec;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuiListener implements Listener {

    private final HazeCrates plugin;

    public GuiListener(HazeCrates plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.guiManager().hasPendingInput(player)) return;

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        GuiManager.PendingInput pending = plugin.guiManager().takePendingInput(player);
        if (pending == null) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                ChatInputGui.reopenEditor(player, plugin);
                return;
            }
            String value = text.equalsIgnoreCase("default") ? pending.defaultValue() : text;
            pending.callback().accept(value);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                int slot = event.getRawSlot();
                String soundName = holder.soundAt(slot);
                if (soundName != null && !soundName.isBlank()) {
                    try {
                        org.bukkit.Sound snd = org.bukkit.Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
                        player.playSound(player.getLocation(), snd, 1.0f, 1.0f);
                    } catch (IllegalArgumentException ignored) {}
                }
                String action = holder.actionAt(slot);
                if (action != null && !action.isBlank()) {
                    handlePreviewAction(player, holder, action);
                }
            }
            return;
        }

        if (plugin.hasAnimSession(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        String titlePlainRaw = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (titlePlainRaw.contains("Opening") || titlePlainRaw.contains("Apertura")) {
            if (plugin.hasAnimSession(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!plugin.guiManager().hasSession(player)) return;

        CrateEditorSession session = plugin.guiManager().session(player);
        String titlePlain = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        boolean isEditorGui = titlePlain.startsWith(CrateListGui.TAG)
                || titlePlain.startsWith(CrateEditorGui.TAG)
                || titlePlain.contains("Nuova crate:")
                || titlePlain.startsWith(RewardListGui.TAG)
                || titlePlain.startsWith(RewardEditorGui.TAG)
                || titlePlain.contains("Nuovo premio");
        if (!isEditorGui) return;

        boolean top = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        if (hasCursor && applyCursorItem(player, session, slot, cursor)) {
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

        switch (session.currentPage()) {
            case CRATE_LIST    -> handleCrateList(player, session, slot);
            case CRATE_EDITOR  -> handleCrateEditor(player, session, slot);
            case REWARD_LIST   -> handleRewardList(player, session, slot, event.isRightClick());
            case REWARD_EDITOR -> handleRewardEditor(player, session, slot);
        }
    }

    private boolean applyCursorItem(Player player, CrateEditorSession session, int slot, ItemStack cursor) {
        if (session.currentPage() != CrateEditorSession.Page.REWARD_EDITOR
                || slot != RewardEditorGui.SLOT_MATERIAL) {
            return false;
        }
        session.rewardItemSpec(plugin.externalItems().identify(cursor));
        session.rewardName("");
        RewardEditorGui.open(player, plugin, session);
        return true;
    }

    private void handleCrateList(Player player, CrateEditorSession session, int slot) {
        if (slot == 49) {
            ChatInputGui.open(player, plugin, "Crate ID", "my_crate",
                    List.of("my_crate", "legendary", "vote"), id -> {
                String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                if (safeId.isBlank()) safeId = "my_crate";
                session.initNew(safeId);
                session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
                CrateEditorGui.open(player, plugin, session);
            });
            return;
        }
        int innerIdx = CrateListGui.innerIndexOf(slot);
        if (innerIdx < 0) return;
        List<CrateDefinition> sorted = new ArrayList<>(plugin.crates().all());
        sorted.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        if (innerIdx >= sorted.size()) return;
        CrateDefinition crate = sorted.get(innerIdx);
        session.loadFrom(crate);
        session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
        CrateEditorGui.open(player, plugin, session);
    }

    private void handleCrateEditor(Player player, CrateEditorSession session, int slot) {
        switch (slot) {

            case CrateEditorGui.SLOT_BACK -> {
                session.currentPage(CrateEditorSession.Page.CRATE_LIST);
                CrateListGui.open(player, plugin);
            }
            case CrateEditorGui.SLOT_SAVE   -> saveCrate(player, session);
            case CrateEditorGui.SLOT_DELETE -> {
                String crateId = session.crateId();
                boolean deleted = plugin.crateWriter().delete(crateId);
                plugin.messages().send(player, deleted ? "crate-deleted" : "unknown-crate",
                        Map.of("crate", crateId));
                session.currentPage(CrateEditorSession.Page.CRATE_LIST);
                CrateListGui.open(player, plugin);
            }
            case CrateEditorGui.SLOT_REWARDS -> {
                session.currentPage(CrateEditorSession.Page.REWARD_LIST);
                RewardListGui.open(player, plugin, session);
            }

            case CrateEditorGui.SLOT_NAME ->
                ChatInputGui.open(player, plugin, "Display Name", session.displayName(), text -> {
                    session.displayName(text);
                    back(player, session);
                });

            case CrateEditorGui.SLOT_GLOW -> {
                session.glow(!session.glow());
                CrateEditorGui.open(player, plugin, session);
            }

            case CrateEditorGui.SLOT_KEYTYPE -> {
                String next = switch (session.keyType().toUpperCase(Locale.ROOT)) {
                    case "PHYSICAL" -> "VIRTUAL";
                    case "VIRTUAL"  -> "LOOTBOX";
                    default         -> "PHYSICAL";
                };
                session.keyType(next);
                CrateEditorGui.open(player, plugin, session);
            }

            case CrateEditorGui.SLOT_KEY_ITEM ->
                ChatInputGui.open(player, plugin, "Key Item",
                        session.keyItemSpec() != null ? session.keyItemSpec().serialize() : "TRIPWIRE_HOOK",
                        List.of("TRIPWIRE_HOOK", "NAME_TAG", "GOLD_NUGGET"),
                        text -> {
                            session.keyItemSpec(ItemSpec.parse(text.trim()));
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_ANIMATION -> {
                session.animation(plugin.animations().cycleNext(session.animation()));
                CrateEditorGui.open(player, plugin, session);
            }

            case CrateEditorGui.SLOT_HOLOGRAM ->
                ChatInputGui.open(player, plugin, "Hologram Lines",
                        String.join("|", session.hologramLines()),
                        List.of("%crate%", "&b%crate%|&7Right-click to open"),
                        text -> {
                            session.hologramLines(Arrays.asList(text.split("\\|")));
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_BROADCAST ->
                ChatInputGui.open(player, plugin, "Broadcast Message", session.broadcast(),
                        List.of("&6%player% won %reward%!", ""),
                        text -> {
                            session.broadcast(text);
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_BCAST_THRESH ->
                ChatInputGui.open(player, plugin, "Broadcast Threshold",
                        String.valueOf(session.broadcastThreshold()),
                        List.of("0", "5", "15"),
                        text -> {
                            try { session.broadcastThreshold(Math.max(0, Integer.parseInt(text.trim()))); }
                            catch (NumberFormatException ignored) {}
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_PARTICLE -> {
                session.particleType(plugin.animations().cycleIdleNext(session.particleType()));
                CrateEditorGui.open(player, plugin, session);
            }

            case CrateEditorGui.SLOT_TITLE ->
                ChatInputGui.open(player, plugin, "Open Title",
                        session.titleText().isBlank()
                                ? plugin.getConfig().getString("opening-title.text", "")
                                : session.titleText(),
                        List.of("&b%crate%", "", "none"),
                        text -> {
                            session.titleText(text);
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_SUBTITLE ->
                ChatInputGui.open(player, plugin, "Open Subtitle",
                        session.titleSubtitle().isBlank()
                                ? plugin.getConfig().getString("opening-title.subtitle", "")
                                : session.titleSubtitle(),
                        List.of("&7Opening...", "", "none"),
                        text -> {
                            session.titleSubtitle(text);
                            back(player, session);
                        });

            case CrateEditorGui.SLOT_PREVIEW_TOGGLE -> {
                session.previewOnLeftClick(!session.previewOnLeftClick());
                CrateEditorGui.open(player, plugin, session);
            }
        }
    }

    private void handleRewardList(Player player, CrateEditorSession session,
                                  int slot, boolean rightClick) {
        if (slot == RewardListGui.SLOT_BACK) {
            session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
            CrateEditorGui.open(player, plugin, session);
            return;
        }
        if (slot == RewardListGui.SLOT_ADD) {
            session.initNewReward();
            session.currentPage(CrateEditorSession.Page.REWARD_EDITOR);
            RewardEditorGui.open(player, plugin, session);
            return;
        }
        int innerIdx = RewardListGui.innerIndexOf(slot);
        if (innerIdx < 0) return;

        List<RewardDefinition> sorted = new ArrayList<>(session.rewards());
        sorted.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        if (innerIdx >= sorted.size()) return;

        RewardDefinition target = sorted.get(innerIdx);
        int origIdx = session.rewards().indexOf(target);
        if (origIdx < 0) return;

        if (rightClick) {
            session.rewards().remove(origIdx);
            RewardListGui.open(player, plugin, session);
        } else {
            session.loadReward(origIdx);
            session.currentPage(CrateEditorSession.Page.REWARD_EDITOR);
            RewardEditorGui.open(player, plugin, session);
        }
    }

    private void handleRewardEditor(Player player, CrateEditorSession session, int slot) {
        switch (slot) {

            case RewardEditorGui.SLOT_BACK -> {
                session.currentPage(CrateEditorSession.Page.REWARD_LIST);
                RewardListGui.open(player, plugin, session);
            }
            case RewardEditorGui.SLOT_APPLY -> {
                applyReward(player, session);
                session.currentPage(CrateEditorSession.Page.REWARD_LIST);
                RewardListGui.open(player, plugin, session);
            }
            case RewardEditorGui.SLOT_REMOVE -> {
                if (session.editingRewardIndex() >= 0)
                    session.rewards().remove(session.editingRewardIndex());
                session.currentPage(CrateEditorSession.Page.REWARD_LIST);
                RewardListGui.open(player, plugin, session);
            }
            case RewardEditorGui.SLOT_PREVIEW -> { }

            case RewardEditorGui.SLOT_ID ->
                ChatInputGui.open(player, plugin, "Reward ID", session.rewardId(),
                        List.of("common_item", "rare_sword", "jackpot"),
                        text -> {
                            session.rewardId(text.isBlank() ? session.rewardId()
                                    : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_MATERIAL ->
                ChatInputGui.open(player, plugin, "Reward Item",
                        session.rewardItemSpec() != null
                                ? session.rewardItemSpec().serialize() : "STONE",
                        List.of("DIAMOND", "NETHERITE_INGOT", "mmoitems:SWORD:EXCALIBUR", "nexo:ruby_sword"),
                        text -> {
                            session.rewardItemSpec(ItemSpec.parse(text.trim()));
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_AMOUNT ->
                ChatInputGui.open(player, plugin, "Amount",
                        String.valueOf(session.rewardAmount()),
                        List.of("1", "8", "16", "64"),
                        text -> {
                            try { session.rewardAmount(Math.max(1, Integer.parseInt(text.trim()))); }
                            catch (NumberFormatException ignored) {}
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_WEIGHT ->
                ChatInputGui.open(player, plugin, "Weight",
                        String.valueOf(session.rewardWeight()),
                        List.of("1", "5", "10", "25", "50"),
                        text -> {
                            try { session.rewardWeight(Math.max(1, Integer.parseInt(text.trim()))); }
                            catch (NumberFormatException ignored) {}
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_NAME ->
                ChatInputGui.open(player, plugin, "Display Name",
                        session.rewardName() == null || session.rewardName().isBlank()
                                ? "" : session.rewardName(),
                        List.of(""),
                        text -> {
                            session.rewardName(text);
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_GLOW -> {
                session.rewardGlow(!session.rewardGlow());
                RewardEditorGui.open(player, plugin, session);
            }
            case RewardEditorGui.SLOT_BROADCAST -> {
                session.rewardBroadcast(!session.rewardBroadcast());
                RewardEditorGui.open(player, plugin, session);
            }

            case RewardEditorGui.SLOT_PERMISSION ->
                ChatInputGui.open(player, plugin, "Permission", session.rewardPermission(),
                        List.of("", "crate.vip", "crate.legendary"),
                        text -> {
                            session.rewardPermission(text.trim());
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_LORE ->
                ChatInputGui.open(player, plugin, "Lore Lines",
                        String.join("|", session.rewardLore()),
                        List.of("&7A cool item", "&eExtremely rare!|&8Limited"),
                        text -> {
                            session.rewardLore(Arrays.asList(text.split("\\|")));
                            backReward(player, session);
                        });

            case RewardEditorGui.SLOT_LORE_TOGGLE -> {
                session.rewardLoreOverride(!session.rewardLoreOverride());
                RewardEditorGui.open(player, plugin, session);
            }

            case RewardEditorGui.SLOT_COMMANDS ->
                ChatInputGui.open(player, plugin, "Commands",
                        String.join("|", session.rewardCommands()),
                        List.of("give %player% diamond 1", "eco give %player% 1000"),
                        text -> {
                            session.rewardCommands(Arrays.asList(text.split("\\|")));
                            backReward(player, session);
                        });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        it.haze.hazecrates.animation.AnimationSession animSession =
                plugin.takeAnimSession(player.getUniqueId());
        if (animSession != null) {
            if (animSession.inventory() == null) {
                plugin.startAnimSession(player.getUniqueId(), animSession);
            } else if (event.getInventory().equals(animSession.inventory())) {
                animSession.grantIfPending();
            } else {
                plugin.startAnimSession(player.getUniqueId(), animSession);
            }
        }

        if (plugin.guiManager().hasPendingInput(player)) return;
        if (!plugin.guiManager().hasSession(player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.guiManager().hasPendingInput(player)
                    && player.getOpenInventory().getTopInventory().getType()
                    == org.bukkit.event.inventory.InventoryType.CRAFTING) {
                plugin.guiManager().removeSession(player);
            }
        });
    }

    private void back(Player player, CrateEditorSession session) {
        session.currentPage(CrateEditorSession.Page.CRATE_EDITOR);
        CrateEditorGui.open(player, plugin, session);
    }

    private void backReward(Player player, CrateEditorSession session) {
        session.currentPage(CrateEditorSession.Page.REWARD_EDITOR);
        RewardEditorGui.open(player, plugin, session);
    }

    private void saveCrate(Player player, CrateEditorSession session) {
        if (session.rewards().isEmpty()) {
            player.sendMessage(GuiItems.legacy("&cAdd at least one reward before saving."));
            return;
        }
        try {
            plugin.crateWriter().save(session);
            plugin.messages().send(player, "crate-saved", Map.of("crate", session.crateId()));
            session.currentPage(CrateEditorSession.Page.CRATE_LIST);
            CrateListGui.open(player, plugin);
        } catch (Exception e) {
            player.sendMessage(GuiItems.legacy("&cFailed to save: " + e.getMessage()));
            plugin.getLogger().severe("CrateWriter save failed: " + e.getMessage());
        }
    }

    private void applyReward(Player player, CrateEditorSession session) {
        ItemSpec spec = session.rewardItemSpec() != null
                ? session.rewardItemSpec()
                : ItemSpec.vanilla("STONE");

        List<String> lore = session.rewardLoreOverride() ? session.rewardLore() : null;

        String name = session.rewardName() == null || session.rewardName().isBlank()
                ? null : session.rewardName();
        if (!session.rewardLoreOverride() && spec.provider() != ItemProvider.VANILLA) {
            name = null;
        }
        ItemStack icon = plugin.externalItems().resolve(
                spec,
                session.rewardAmount(),
                name,
                lore,
                session.rewardGlow()
        );

        RewardDefinition reward = new RewardDefinition(
                session.rewardId(),
                spec,
                icon,
                session.rewardCommands(),
                session.rewardWeight(),
                session.rewardPermission(),
                session.rewardBroadcast(),
                session.rewardLoreOverride()
        );

        int idx = session.editingRewardIndex();
        if (idx >= 0 && idx < session.rewards().size()) {
            session.rewards().set(idx, reward);
        } else {
            session.rewards().add(reward);
        }
    }

    private void handlePreviewAction(Player player, PreviewHolder holder, String action) {
        String act = action.trim();
        String upper = act.toUpperCase(Locale.ROOT);

        if (upper.equals("CLOSE")) {
            player.closeInventory();
            return;
        }

        if (upper.equals("PREVIOUS_PAGE") || upper.equals("PREV_PAGE")) {
            if (holder.page() > 1) {
                PreviewInventory.open(player, holder.crate(), plugin, holder.page() - 1);
            }
            return;
        }

        if (upper.equals("NEXT_PAGE")) {
            if (holder.page() < holder.totalPages()) {
                PreviewInventory.open(player, holder.crate(), plugin, holder.page() + 1);
            }
            return;
        }

        if (upper.equals("OPEN_CRATE") || upper.equals("OPEN")) {
            CrateDefinition crate = holder.crate();
            if (plugin.hasAnimSession(player.getUniqueId())) {
                return;
            }
            if (!player.hasPermission("hazecrates.open." + crate.id())
                    && !player.hasPermission("hazecrates.open.*")) {
                plugin.messages().send(player, "no-permission");
                return;
            }

            player.closeInventory();

            if (crate.keyType() == it.haze.hazecrates.crate.KeyType.LOOTBOX
                    || crate.keyType() == it.haze.hazecrates.crate.KeyType.PHYSICAL) {
                boolean found = false;
                for (ItemStack is : player.getInventory().getContents()) {
                    if (is != null && plugin.keys().isKey(is, crate.id())) {
                        is.setAmount(is.getAmount() - 1);
                        found = true;
                        break;
                    }
                }
                if (found) {
                    new it.haze.hazecrates.listener.CrateListener(plugin).open(player, crate, player.getLocation());
                } else {
                    plugin.messages().send(player, "need-key",
                            Map.of("crate", crate.displayName(), "type", "PHYSICAL"));
                }
            } else {
                plugin.keys().consumeVirtual(player.getUniqueId(), crate.id())
                        .whenComplete((ok, err) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            if (err != null) plugin.messages().send(player, "database-error");
                            else if (!ok)   plugin.messages().send(player, "need-virtual-key", Map.of("crate", crate.displayName()));
                            else            new it.haze.hazecrates.listener.CrateListener(plugin).open(player, crate, player.getLocation());
                        }));
            }
            return;
        }

        if (upper.startsWith("COMMAND:")) {
            String cmd = act.substring("COMMAND:".length()).trim();
            cmd = cmd.replace("%player%", player.getName()).replace("%crate%", holder.crate().id());
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
            return;
        }

        if (upper.startsWith("PLAYER_COMMAND:")) {
            String cmd = act.substring("PLAYER_COMMAND:".length()).trim();
            cmd = cmd.replace("%player%", player.getName()).replace("%crate%", holder.crate().id());
            player.performCommand(cmd);
            return;
        }

        if (upper.startsWith("MESSAGE:")) {
            String msg = act.substring("MESSAGE:".length()).trim();
            msg = msg.replace("%player%", player.getName()).replace("%crate%", holder.crate().displayName());
            player.sendMessage(plugin.messages().parse(msg));
        }
    }
}
