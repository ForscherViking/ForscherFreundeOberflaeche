package com.forscherfreunde.hub.model;

import com.forscherfreunde.hub.AppPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ein Kinder-Profil. Die Feldnamen sind identisch mit dem bisherigen profiles.json-Format,
 * damit bestehende Profile ohne Migration weiter funktionieren.
 */
public final class UserProfile {
    private String name;
    private String workingDirectory;
    private String moduleName;
    private String modulePath;
    private Boolean codeProfile = false;

    /** Für Gson. */
    @SuppressWarnings("unused")
    private UserProfile() { }

    public UserProfile(String name, Camp module) {
        this.name = name;
        Path work = AppPaths.root().resolve(name);
        this.workingDirectory = work.toString();
        this.moduleName = module.id();
        this.modulePath = work.resolve(module.id()).toString();
        this.codeProfile = module.isCode();
    }

    public String getName() { return name; }

    public String getModuleName() { return moduleName; }

    public Camp module() { return Camp.byId(moduleName); }

    public boolean isCodeProfile() {
        Camp m = module();
        return m != null ? m.isCode() : Boolean.TRUE.equals(codeProfile);
    }

    public Path workDir() { return Paths.get(workingDirectory); }

    public Path modulePath() {
        return modulePath != null ? Paths.get(modulePath) : workDir().resolve(moduleName);
    }

    public Path backupDir() { return AppPaths.backups().resolve(name + "_backup"); }

    @Override
    public String toString() { return name; }
}
