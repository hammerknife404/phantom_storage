package com.phantomstorage.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.phantomstorage.summon.CharmMode;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /phantomstorage unlock <tank|anchor|collector|saddle|gateway|bell|all>}: for testing, a player in
 * creative mode grants themselves a charm mode's unlock advancement, exactly as {@code /advancement grant} would. Creative already
 * ignores unlocks, so this is about what they'll have back in survival. Any player may run it, but only
 * in creative; {@code /advancement revoke} locks a mode again.
 */
public final class PhantomCommands {
    private static final String ALL = "all";
    private static final SuggestionProvider<CommandSourceStack> PETS = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(lockable().map(CharmMode::getSerializedName), Stream.of(ALL)), builder);

    private PhantomCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("phantomstorage")
                .requires(CommandSourceStack::isPlayer)
                .then(Commands.literal("unlock")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(PETS)
                                .executes(context -> unlock(context.getSource(), StringArgumentType.getString(context, "mode"))))));
    }

    /** Modes that start locked: everything but the chest. */
    private static Stream<CharmMode> lockable() {
        return Stream.of(CharmMode.values()).filter(CharmMode::hasUnlock);
    }

    private static int unlock(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.isCreative()) {
            source.sendFailure(Component.translatable("commands.phantomstorage.unlock.creative_only"));
            return 0;
        }
        List<CharmMode> kinds = ALL.equals(name)
                ? lockable().toList()
                : lockable().filter(kind -> kind.getSerializedName().equals(name)).toList();
        if (kinds.isEmpty()) {
            source.sendFailure(Component.translatable("commands.phantomstorage.unlock.unknown", name));
            return 0;
        }
        int granted = 0;
        for (CharmMode kind : kinds) {
            AdvancementHolder advancement = kind.primaryUnlock(player);
            if (kind.isEarned(player) || advancement == null) {
                source.sendSuccess(() -> Component.translatable("commands.phantomstorage.unlock.already", kind.fullName()), false);
                continue;
            }
            // Like /advancement grant: every remaining criterion, so the usual events and toasts fire.
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
            granted++;
            source.sendSuccess(() -> Component.translatable("commands.phantomstorage.unlock.success",
                    kind.fullName(), CharmMode.advancementTitle(advancement)), true);
        }
        return granted;
    }
}
