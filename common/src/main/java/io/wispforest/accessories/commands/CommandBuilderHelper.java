package io.wispforest.accessories.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class CommandBuilderHelper {

    public Map<String, LiteralArgumentBuilder<CommandSourceStack>> baseCommandPart = new LinkedHashMap<>();

    public Map<Key, LiteralArgumentBuilder<CommandSourceStack>> commandParts = new LinkedHashMap<>();

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        generateTrees(context);

        this.baseCommandPart.forEach((string, builder) -> dispatcher.register(builder));
    }

    protected abstract void generateTrees(CommandBuildContext context);

    public abstract void registerArgumentTypes(ArgumentRegistration registration);

    //--

    public <T> CommandArgumentHolder<T> argumentHolder(String name, ArgumentType<?> type, CommandArgumentGetter<T> getter) {
        return new CommandArgumentHolder<>(name, type, getter);
    }

    public LiteralArgumentBuilder<CommandSourceStack> getOrCreateNode(String key, CommandArgumentHolder<?> ...argumentsParts) {
        return getOrCreateNode(key.split("/"));
    }

    public LiteralArgumentBuilder<CommandSourceStack> getOrCreateNode(String key) {
        return getOrCreateNode(key.split("/"));
    }

    public LiteralArgumentBuilder<CommandSourceStack> getOrCreateNode(String ...keyParts) {
        var key = new Key(keyParts);

        return getOrCreateNode(key);
    }

    public LiteralArgumentBuilder<CommandSourceStack> getOrCreateNode(Key key) {
        if (commandParts.containsKey(key)) return commandParts.get(key);

        LiteralArgumentBuilder<CommandSourceStack> builder;

        var parentKey = key.parent();

        if (parentKey == null) {
            builder = Commands.literal(key.topPath());

            this.baseCommandPart.put(key.topPath(), builder);
        } else {
            var parentBuilder = getOrCreateNode(parentKey);

            builder = Commands.literal(key.topPath());

            parentBuilder.then(builder);
        }

        this.commandParts.put(key, builder);

        return builder;
    }

    //--

    public <T1> LiteralArgumentBuilder<CommandSourceStack> optionalArgExectution(LiteralArgumentBuilder<CommandSourceStack> builder, CommandArgumentHolder<T1> arg1, CommandFunction1<@Nullable T1> commandExecution) {
        return builder.then(
                arg1.builder().executes((ctx) -> {
                    return commandExecution.execute(ctx, arg1.getArgument(ctx));
                })
        ).executes(ctx -> {
            return commandExecution.execute(ctx, null);
        });
    }

    public <T1> LiteralArgumentBuilder<CommandSourceStack> requiredArgExectution(LiteralArgumentBuilder<CommandSourceStack> builder, CommandArgumentHolder<T1> arg1, CommandFunction1<T1> commandExecution) {
        return builder.then(
                arg1.builder().executes((ctx) -> {
                    return commandExecution.execute(ctx, arg1.getArgument(ctx));
                })
        );
    }

    public <T1, T2> LiteralArgumentBuilder<CommandSourceStack> requiredArgExectution(LiteralArgumentBuilder<CommandSourceStack> builder, CommandArgumentHolder<T1> arg1, CommandArgumentHolder<T2> arg2, CommandFunction2<T1, T2> commandExecution) {
        return builder.then(
                arg1.builder().then(
                        arg2.builder().executes((ctx) -> {
                            return commandExecution.execute(ctx, arg1.getArgument(ctx), arg2.getArgument(ctx));
                        })
                )
        );
    }

    public <T1, T2, T3> LiteralArgumentBuilder<CommandSourceStack> requiredArgExectution(LiteralArgumentBuilder<CommandSourceStack> builder, CommandArgumentHolder<T1> arg1, CommandArgumentHolder<T2> arg2, CommandArgumentHolder<T3> arg3, CommandFunction3<T1, T2, T3> commandExecution) {
        return builder.then(
                arg1.builder().then(
                        arg2.builder().then(
                                arg3.builder().executes((ctx) -> {
                                    return commandExecution.execute(ctx, arg1.getArgument(ctx), arg2.getArgument(ctx), arg3.getArgument(ctx));
                                })
                        )
                )
        );
    }

    public LiteralArgumentBuilder<CommandSourceStack> requiredExectutionBranched(LiteralArgumentBuilder<CommandSourceStack> builder, List<String> literalBranches, CommandFunction1<String> commandExecution) {
        for (var branch : literalBranches) {
            builder.then(
                    Commands.literal(branch).executes((ctx) -> commandExecution.execute(ctx, branch))
            );
        }

        return builder;
    }

    public <T1> LiteralArgumentBuilder<CommandSourceStack> requiredArgExectutionBranched(LiteralArgumentBuilder<CommandSourceStack> builder, List<String> literalBranches, CommandArgumentHolder<T1> arg1, CommandFunction2<String, T1> commandExecution) {
        for (var branch : literalBranches) {
            builder.then(
                    Commands.literal(branch)
                            .then(
                                    arg1.builder().executes((ctx) -> {
                                        return commandExecution.execute(ctx, branch, arg1.getArgument(ctx));
                                    })
                            )
            );
        }

        return builder;
    }

    //--

    public record CommandArgumentHolder<T>(String name, ArgumentType<?> type, CommandArgumentGetter<T> getter) {
        public static <T> CommandArgumentHolder<T> of(String name, ArgumentType<?> type, CommandArgumentGetter<T> getter) {
            return new CommandArgumentHolder<>(name, type, getter);
        }

        public RequiredArgumentBuilder<CommandSourceStack, ?> builder() {
            return Commands.argument(name, type);
        }

        public T getArgument(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            return this.getter.get(ctx, name);
        }
    }

    public interface CommandArgumentGetter<T> {
        T get(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException;
    }

    //--

    public interface CommandFunction {
        int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    public interface CommandFunction1<T1> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1) throws CommandSyntaxException;
    }

    public interface CommandFunction2<T1, T2> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1, T2 t2) throws CommandSyntaxException;
    }

    public interface CommandFunction3<T1, T2, T3> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1, T2 t2, T3 t3) throws CommandSyntaxException;
    }

    public interface CommandFunction4<T1, T2, T3, T4> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1, T2 t2, T3 t3, T4 t4) throws CommandSyntaxException;
    }

    public interface CommandFunction5<T1, T2, T3, T4, T5> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) throws CommandSyntaxException;
    }

    public interface CommandFunction6<T1, T2, T3, T4, T5, T6> {
        int execute(CommandContext<CommandSourceStack> ctx, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) throws CommandSyntaxException;
    }

    //--

    public interface ArgumentRegistration {
        <A extends ArgumentType<?>, T> RecordArgumentTypeInfo<A, T> register(ResourceLocation location, Class<A> clazz, RecordArgumentTypeInfo<A, T> info);
    }
}
