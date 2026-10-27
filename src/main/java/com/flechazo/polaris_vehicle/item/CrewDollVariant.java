package com.flechazo.polaris_vehicle.item;

public enum CrewDollVariant {
    FLECHAZO(0, "flechazo", "Flechazo_____", "crew_doll"),
    NAIMIURA(1, "naimiura", "NaimiUra_", "crew_doll"),
    BLUE_JUNZI(2, "blue_junzi", "blue_junzi", "crew_doll"),
    ZHIFAN(3, "zhifan", "zhifan", "crew_doll"),
    DUCKDUCK(4, "duckduck", "duckduck", "crew_doll"),
    NEPTUNE_TADASHI(5, "neptune_tadashi", "NeptuneTADASHI", "crew_doll"),
    YELLOW_DUCK(6, "yellow_duck", "karin", "crew_doll_yellow_duck"),
    SDJGE(7, "sdjge", "sdjge", "crew_doll");

    private final int id;
    private final String textureName;
    private final String officialName;
    private final String modelName;

    CrewDollVariant(int id, String textureName, String officialName, String modelName) {
        this.id = id;
        this.textureName = textureName;
        this.officialName = officialName;
        this.modelName = modelName;
    }

    public int id() {
        return id;
    }

    public String textureName() {
        return textureName;
    }

    public String officialName() {
        return officialName;
    }

    public String modelName() {
        return modelName;
    }

    public static CrewDollVariant byId(int id) {
        for (CrewDollVariant variant : values()) {
            if (variant.id == id) {
                return variant;
            }
        }
        return FLECHAZO;
    }
}
