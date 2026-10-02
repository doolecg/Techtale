package com.doolecg.techtale.hytale.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/** The /techtale command: parent of the mod's commands. */
public class TechtaleCommand extends AbstractCommandCollection {
    public TechtaleCommand() {
        super("techtale", "Techtale commands");
        addSubCommand(new OresCommand());
        addSubCommand(new SelfTestCommand());
    }
}
