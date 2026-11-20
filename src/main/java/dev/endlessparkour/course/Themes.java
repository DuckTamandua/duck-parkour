package dev.endlessparkour.course;

import java.util.List;
import java.util.Set;

/**
 * The areas of the course: the regions of Orbis with their vanilla names, plus structures
 * that stand in them. Palettes, plants, grass colours, trees and weather come from the game's
 * own world generation (Server/World/Default/Zones) and structure prefabs. The level follows
 * the region tier: Emerald Wilds is easy, Devastated Lands is hard.
 * Every surface block is a full, solid, non-slippery cube (checked against the game assets).
 */
public final class Themes {

    private Themes() {
    }

    /** Blocks with the tag Type=Soil: grass, flowers and bushes only grow on these. */
    public static final Set<String> SOIL = Set.of(
            "Soil_Grass", "Soil_Grass_Sunny", "Soil_Grass_Full", "Soil_Grass_Deep", "Soil_Grass_Cold",
            "Soil_Grass_Dry", "Soil_Grass_Burnt", "Soil_Dirt", "Soil_Dirt_Cold", "Soil_Dirt_Dry",
            "Soil_Dirt_Burnt", "Soil_Dirt_Poisoned", "Soil_Mud_Dry", "Soil_Pathway", "Soil_Leaves",
            "Soil_Needles", "Soil_Clay", "Soil_Ash", "Soil_Hive", "Soil_Sand_White", "Soil_Sand_Red",
            "Soil_Snow_Brick");

    /** Blocks that need a full block right under them, or they would not hold. */
    public static final Set<String> NEEDS_FLOOR = Set.of(
            "Soil_Sand", "Soil_Sand_Red", "Soil_Sand_White", "Soil_Sand_Ashen", "Soil_Gravel",
            "Soil_Gravel_Sand", "Soil_Gravel_Sand_Red", "Soil_Gravel_Sand_White", "Soil_Gravel_Mossy",
            "Soil_Pebbles", "Soil_Pebbles_Frozen",
            "Wood_Oak_Trunk", "Wood_Birch_Trunk", "Wood_Ash_Trunk", "Wood_Beech_Trunk", "Wood_Fir_Trunk",
            "Wood_Cedar_Trunk", "Wood_Redwood_Trunk", "Wood_Dry_Trunk", "Wood_Gumboab_Trunk",
            "Wood_Palo_Trunk", "Wood_Palm_Trunk", "Wood_Banyan_Trunk", "Wood_Petrified_Trunk",
            "Wood_Burnt_Trunk", "Wood_Maple_Trunk", "Wood_Sadwillow_Trunk", "Wood_Oak_Trunk_Full");

    private static final String EMERALD = "Emerald Wilds";
    private static final String SANDS = "Howling Sands";
    private static final String FROST = "Whisperfrost Frontiers";
    private static final String DEVASTATED = "Devastated Lands";

    private static final String Z1 = "SFX_Discovery_Z1_Medium";
    private static final String Z2 = "SFX_Discovery_Z2_Medium";
    private static final String Z3 = "SFX_Discovery_Z3_Medium";
    private static final String Z4 = "SFX_Discovery_Z4_Medium";

    // ------------------------------------------------------------------ Emerald Wilds (zone 1)

    public static final Theme DRIFTING_PLAINS = Theme.builder("drifting_plains", "Drifting Plains")
            .zone(EMERALD).color("#8BD449").sound(Z1).weather("Zone1_Sunny").env("Env_Zone1_Plains").level(1)
            .top("Soil_Grass", 10, "Soil_Grass_Sunny", 3, "Soil_Grass_Full", 2, "Soil_Pathway", 1)
            .fill("Soil_Dirt", 6, "Rock_Stone", 1)
            .bottom("Rock_Stone", 6, "Rock_Chalk", 1, "Rock_Stone_Mossy", 1)
            .accent(0.08, "Rock_Quartzite", 1, "Rock_Chalk", 1)
            .marker("Rock_Quartzite_Brick_Decorative")
            .plants(0.40)
            .soilPlants("Plant_Grass_Lush_Short", 6, "Plant_Grass_Sharp", 3, "Plant_Bush_Green", 2, "Plant_Bush", 2,
                    "Plant_Flower_Common_Red2", 1, "Plant_Flower_Common_White2", 1, "Plant_Flower_Common_Yellow2", 1,
                    "Plant_Flower_Flax_Blue", 1, "Plant_Fern_Winter", 1)
            .anyPlants("Plant_Crop_Mushroom_Common_Brown", 1)
            .hanging(0.25, "Rock_Stone_Stalactite_Small", 1)
            .pillar("Wood_Birch_Trunk")
            .tints("#3B8E32", 3, "#639726", 4, "#5B9E28", 3)
            .tree("Wood_Birch_Trunk", "Plant_Leaves_Birch").tree("Wood_Birch_Trunk", "Plant_Leaves_Birch")
            .tree("Wood_Oak_Trunk", "Plant_Leaves_Oak")
            .scatter("Rubble_Stone", 3, "Rubble_Marble", 1, "Wood_Sticks", 1)
            .build();

    public static final Theme SEEDLING_WOODS = Theme.builder("seedling_woods", "Seedling Woods")
            .zone(EMERALD).color("#7ED957").sound(Z1).weather("Zone1_Oak_Forest_Windy").env("Env_Zone1_Forests").level(2)
            .top("Soil_Grass", 8, "Soil_Leaves", 3, "Soil_Grass_Deep", 2)
            .fill("Soil_Dirt", 6, "Rock_Stone", 1)
            .bottom("Rock_Stone", 5, "Rock_Stone_Mossy", 2, "Rock_Chalk", 1)
            .accent(0.12, "Plant_Moss_Block_Green", 1, "Rock_Stone_Mossy", 1)
            .marker("Rock_Quartzite_Brick")
            .plants(0.45)
            .soilPlants("Plant_Bush_Green", 4, "Plant_Grass_Sharp", 3, "Plant_Grass_Lush", 2, "Plant_Grass_Lush_Short", 2,
                    "Plant_Flower_Tall_Yellow", 1, "Plant_Fern_Forest", 1)
            .anyPlants("Plant_Crop_Mushroom_Cap_Brown", 2, "Plant_Crop_Mushroom_Common_Brown", 1)
            .hanging(0.25, "Rock_Stone_Stalactite_Small", 1)
            .pillar("Wood_Oak_Trunk")
            .tints("#7EA629", 3, "#5B9E28", 3, "#3B8E32", 2, "#E0962E", 1)
            .tree("Wood_Oak_Trunk", "Plant_Leaves_Oak").tree("Wood_Oak_Trunk", "Plant_Leaves_Oak")
            .tree("Wood_Birch_Trunk", "Plant_Leaves_Birch").tree("Wood_Beech_Trunk", "Plant_Leaves_Beech")
            .scatter("Rubble_Stone", 2, "Rubble_Marble", 1, "Wood_Sticks", 2)
            .build();

    public static final Theme THE_FENS = Theme.builder("the_fens", "The Fens")
            .zone(EMERALD).color("#9DB84A").sound(Z1).weather("Zone1_Swamp").env("Env_Zone1_Swamps").level(3)
            .top("Soil_Grass", 6, "Plant_Moss_Block_Green", 2, "Soil_Clay", 1, "Soil_Mud_Dry", 1)
            .fill("Soil_Dirt", 4, "Soil_Clay", 2)
            .bottom("Rock_Stone", 4, "Rock_Stone_Mossy", 2)
            .accent(0.10, "Rock_Crystal_Cyan_Block", 1, "Plant_Moss_Block_Green", 2)
            .marker("Rock_Crystal_Cyan_Block")
            .plants(0.50)
            .soilPlants("Plant_Fern_Winter", 3, "Plant_Fern_Forest", 3, "Plant_Reeds_Marsh", 3,
                    "Plant_Bush_Dead_Twisted", 1, "Plant_Bush_Arid_Red", 1)
            .anyPlants("Plant_Crop_Mushroom_Cap_Brown", 2, "Plant_Crop_Mushroom_Cap_Red", 2, "Plant_Seaweed_Grass", 1)
            .hanging(0.30, "Rock_Stone_Stalactite_Small", 2, "Soil_Clay_Stalactite_Small", 1)
            .pillar("Wood_Ash_Trunk")
            .tints("#65750D", 2, "#445F0F", 2, "#7EA629", 1, "#A66033", 1, "#C07321", 1)
            .tree("Wood_Sadwillow_Trunk", "Plant_Leaves_Sadwillow").tree("Wood_Ash_Trunk", "Plant_Leaves_Ash")
            .tree("Wood_Oak_Trunk", "Plant_Leaves_Oak")
            .scatter("Rubble_Stone", 2, "Wood_Sticks", 1)
            .build();

    public static final Theme KWEEBEC_VILLAGE = Theme.builder("kweebec_village", "Kweebec Village")
            .zone(EMERALD).color("#E3A35B").sound(Z1).weather("Zone1_Autumn_Forest_Windy").env("Env_Zone1_Kweebec").level(2)
            .top("Soil_Mud_Dry", 3, "Soil_Dirt", 2, "Soil_Grass", 2, "Wood_Hardwood_Planks", 1)
            .fill("Soil_Dirt", 4, "Soil_Mud_Dry", 1)
            .bottom("Rock_Basalt_Cobble", 2, "Rock_Stone", 2)
            .accent(0.10, "Wood_Hardwood_Planks", 1)
            .marker("Wood_Oak_Trunk_Full")
            .plants(0.45)
            .soilPlants("Plant_Bush_Arid_Red", 2, "Plant_Flower_Common_Yellow2", 1, "Plant_Grass_Sharp", 2)
            .anyPlants("Plant_Leaves_Autumn_Floor", 3, "Plant_Crop_Mushroom_Common_Brown", 1)
            .hanging(0.20, "Rock_Basalt_Stalactite_Small", 1)
            .pillar("Wood_Oak_Trunk")
            .tints("#7EA629", 2, "#E0962E", 1, "#C07321", 1)
            .tree("Wood_Oak_Trunk", "Plant_Leaves_Autumn").tree("Wood_Oak_Trunk", "Plant_Leaves_Autumn")
            .tree("Wood_Maple_Trunk", "Plant_Leaves_Maple")
            .scatter("Wood_Sticks", 2, "Rubble_Stone", 1)
            .build();

    public static final Theme TRORK_OUTPOST = Theme.builder("trork_outpost", "Trork Outpost")
            .zone(EMERALD).color("#C8B98A").sound(Z1).weather("Zone1_Cloudy_Medium").env("Env_Zone1_Trork").level(3)
            .top("Rock_Quartzite", 5, "Rock_Quartzite_Cobble", 2, "Rock_Quartzite_Brick", 2, "Soil_Grass", 2)
            .fill("Rock_Quartzite", 3, "Soil_Dirt", 1)
            .bottom("Rock_Quartzite", 3, "Rock_Stone", 2)
            .accent(0.12, "Wood_Softwood_Planks", 1, "Rock_Quartzite_Brick", 1)
            .marker("Rock_Quartzite_Brick_Decorative")
            .plants(0.30)
            .soilPlants("Plant_Grass_Sharp", 2, "Plant_Grass_Lush_Short", 2)
            .hanging(0.20, "Rock_Quartzite_Stalactite_Small", 1)
            .pillar("Wood_Beech_Trunk")
            .tints("#5B9E28", 1, "#7EA629", 1)
            .tree("Wood_Beech_Trunk", "Plant_Leaves_Beech").tree("Rock_Quartzite_Brick", null)
            .scatter("Rubble_Stone", 2, "Wood_Sticks", 1)
            .build();

    public static final Theme FORGOTTEN_TEMPLE = Theme.builder("forgotten_temple", "Forgotten Temple")
            .zone(EMERALD).color("#E8DCC0").sound(Z1).weather("Zone1_Sunny_Fireflies").env("Env_Forgotten_Temple_Exterior").level(3)
            .top("Rock_Marble_Brick", 5, "Rock_Marble_Brick_Smooth", 2, "Rock_Marble", 1, "Soil_Grass", 1)
            .fill("Rock_Stone", 3, "Rock_Shale", 1)
            .bottom("Rock_Stone", 3, "Rock_Shale_Brick", 1, "Rock_Marble_Cobble", 1)
            .accent(0.15, "Rock_Marble_Cobble", 1, "Rock_Shale_Brick", 1, "Rock_Sandstone_White", 1)
            .marker("Rock_Marble_Brick_Decorative")
            .plants(0.30)
            .soilPlants("Plant_Bush", 2, "Plant_Bush_Green", 2, "Plant_Grass_Sharp", 1)
            .hanging(0.20, "Rock_Marble_Stalactite_Small", 1)
            .pillar("Rock_Marble_Brick")
            .tints("#5B9E28", 1, "#3B8E32", 1)
            .tree("Rock_Marble_Brick", null).tree("Rock_Marble_Brick_Smooth", null)
            .scatter("Rubble_Marble", 2, "Rubble_Stone", 1)
            .build();

    // ------------------------------------------------------------------ Howling Sands (zone 2)

    public static final Theme GOLDEN_STEPPES = Theme.builder("golden_steppes", "Golden Steppes")
            .zone(SANDS).color("#E5C442").sound(Z2).weather("Zone2_Sunny").env("Env_Zone2_Savanna").level(4)
            .top("Soil_Grass_Dry", 7, "Soil_Dirt_Dry", 2, "Soil_Sand_White", 1)
            .fill("Soil_Dirt_Dry", 3, "Rock_Sandstone", 2)
            .bottom("Rock_Sandstone", 5, "Rock_Sandstone_Red", 1)
            .accent(0.08, "Rock_Sandstone_Red", 1)
            .marker("Rock_Sandstone_Brick_Decorative")
            .plants(0.55)
            .soilPlants("Plant_Grass_Arid", 4, "Plant_Grass_Arid_Short", 3, "Plant_Grass_Arid_Tall", 2,
                    "Plant_Flower_Common_White2", 1, "Plant_Bush_Arid", 1)
            .anyPlants("Plant_Crop_Mushroom_Common_Brown", 1)
            .hanging(0.25, "Rock_Sandstone_Stalactite_Small", 1)
            .pillar("Wood_Dry_Trunk")
            .tints("#BFAC29", 2, "#D8AF49", 2, "#D39E3D", 1, "#D7BE35", 1, "#E5C442", 1)
            .tree("Wood_Gumboab_Trunk", "Plant_Leaves_Gumboab").tree("Wood_Dry_Trunk", "Plant_Leaves_Dry")
            .scatter("Rubble_Sandstone", 3)
            .build();

    public static final Theme FERAN_VILLAGE = Theme.builder("feran_village", "Feran Village")
            .zone(SANDS).color("#E8B060").sound(Z2).weather("Zone2_Sunny").env("Env_Zone2_Feran").level(4)
            .top("Rock_Sandstone_Brick", 4, "Soil_Dirt_Dry", 3, "Rock_Sandstone_Cobble", 2)
            .fill("Rock_Sandstone_Cobble", 2, "Soil_Dirt_Dry", 2)
            .bottom("Rock_Sandstone", 3, "Rock_Sandstone_Red", 1)
            .accent(0.10, "Wood_Drywood_Planks", 1, "Deco_Bone_Full", 1)
            .marker("Rock_Sandstone_Brick_Decorative")
            .plants(0.25)
            .soilPlants("Plant_Grass_Arid_Short", 2, "Plant_Grass_Arid", 1)
            .hanging(0.20, "Rock_Sandstone_Stalactite_Small", 1)
            .pillar("Wood_Dry_Trunk")
            .tints("#D8AF49", 1, "#BFAC29", 1)
            .tree("Wood_Palm_Trunk", "Plant_Leaves_Palm").tree("Wood_Dry_Trunk", "Plant_Leaves_Dry")
            .scatter("Rubble_Sandstone", 2)
            .build();

    public static final Theme BADLANDS = Theme.builder("badlands", "Badlands")
            .zone(SANDS).color("#E07A3C").sound(Z2).weather("Zone2_Blazing_Light").env("Env_Zone2_Plateaus").level(5)
            .top("Soil_Dirt_Dry", 4, "Rock_Sandstone_Red", 3, "Soil_Sand_Red", 2, "Soil_Grass_Dry", 1)
            .fill("Rock_Sandstone_Red", 4, "Rock_Sandstone", 2)
            .bottom("Rock_Sandstone_Red", 4, "Rock_Sandstone", 1)
            .accent(0.10, "Rock_Sandstone_White", 1, "Rock_Sandstone_Red_Brick", 1)
            .marker("Rock_Sandstone_Red_Brick")
            .plants(0.35)
            .soilPlants("Plant_Grass_Arid", 3, "Plant_Grass_Arid_Tall", 2, "Plant_Grass_Arid_Short", 1,
                    "Plant_Flower_Common_Blue2", 1)
            .hanging(0.30, "Rock_Sandstone_Red_Stalactite_Small", 1)
            .pillar("Rock_Sandstone_Red_Brick")
            .tints("#D5BA6E", 2, "#DA9137", 1, "#CDB868", 1)
            .tree("Wood_Palo_Trunk", "Plant_Leaves_Palo").tree("Wood_Dry_Trunk", "Plant_Leaves_Dry")
            .scatter("Rubble_Sandstone_Red", 3)
            .build();

    public static final Theme SCARAK_HIVE = Theme.builder("scarak_hive", "Scarak Hive")
            .zone(SANDS).color("#C9A227").sound(Z2).weather("Zone2_Cloudy_Light").env("Env_Zone2_Scarak").level(5)
            .top("Soil_Hive", 5, "Soil_Mud_Dry", 3, "Rock_Basalt", 1)
            .fill("Soil_Hive", 3, "Rock_Basalt", 2)
            .bottom("Rock_Basalt", 3, "Rock_Volcanic", 2)
            .accent(0.15, "Soil_Dirt_Poisoned", 1, "Soil_Roots_Poisoned", 1)
            .marker("Soil_Hive_Brick")
            .plants(0.15)
            .soilPlants("Plant_Bush_Dead_Twisted", 1)
            .anyPlants("Plant_Crop_Mushroom_Glowing_Violet", 1)
            .hanging(0.30, "Rock_Basalt_Stalactite_Small", 1)
            .pillar("Soil_Hive_Brick")
            .tints("#D5BA6E", 1, "#CDB868", 1)
            .tree("Soil_Hive_Brick", null).tree("Soil_Hive", null)
            .scatter("Rubble_Basalt", 2)
            .build();

    public static final Theme DESOLATE_BASIN = Theme.builder("desolate_basin", "Desolate Basin")
            .zone(SANDS).color("#D6C565").sound(Z2).weather("Zone2_Desert_Haze").env("Env_Zone2_Deserts").level(6)
            .top("Soil_Dirt_Dry", 4, "Soil_Sand_White", 3, "Rock_Sandstone", 2, "Soil_Grass_Dry", 1)
            .fill("Rock_Sandstone", 3, "Soil_Dirt_Dry", 1)
            .bottom("Rock_Sandstone", 3, "Rock_Sandstone_White", 2)
            .accent(0.10, "Deco_Bone_Full", 1)
            .marker("Deco_Bone_Full")
            .plants(0.25)
            .soilPlants("Plant_Grass_Arid", 2, "Plant_Grass_Arid_Short", 2, "Plant_Grass_Dry_Tall", 1)
            .hanging(0.25, "Rock_Sandstone_White_Stalactite_Small", 1)
            .pillar("Deco_Bone_Full")
            .tints("#D5BA6E", 1, "#D6C565", 1, "#E3C75D", 1)
            .tree("Deco_Bone_Full", null)
            .scatter("Rubble_Sandstone", 2)
            .build();

    // ------------------------------------------------------------------ Oceans

    public static final Theme CRYSTALLINE_DEPTHS = Theme.builder("crystalline_depths", "Crystalline Depths")
            .zone("Oceans").color("#5FD3E8").sound("SFX_Discovery_Z1_Short").weather("Skylands_Sunny").env("Env_Zone0_Temperate").level(5)
            .top("Soil_Sand_White", 3, "Plant_Moss_Block_Green", 2, "Soil_Gravel_Sand", 1, "Rock_Quartzite", 1,
                    "Plant_Coral_Block_Cyan", 1, "Plant_Coral_Block_Pink", 1)
            .fill("Rock_Sandstone", 2, "Rock_Stone", 2)
            .bottom("Rock_Stone", 3, "Rock_Shale", 1)
            .accent(0.20, "Plant_Coral_Block_Yellow", 1, "Plant_Coral_Block_Orange", 1, "Plant_Coral_Block_Purple", 1,
                    "Wood_Deadwood_Planks", 1)
            .marker("Plant_Coral_Block_Pink")
            .plants(0.40)
            .anyPlants("Plant_Seaweed_Grass", 3, "Plant_Seaweed_Arid_Short", 2, "Plant_Coral_Bush_Yellow", 1,
                    "Deco_Coral_Shell", 1)
            .hanging(0.20, "Rock_Stone_Stalactite_Small", 1)
            .pillar("Wood_Banyan_Trunk")
            .tints("#669415", 1)
            .tree("Wood_Banyan_Trunk", "Plant_Leaves_Banyan").tree("Wood_Palm_Trunk", "Plant_Leaves_Palm")
            .scatter("Rubble_Stone", 1)
            .build();

    // ------------------------------------------------------------------ Whisperfrost Frontiers (zone 3)

    public static final Theme FROSTMARCH_TUNDRA = Theme.builder("frostmarch_tundra", "Frostmarch Tundra")
            .zone(FROST).color("#7FB89A").sound(Z3).weather("Zone3_Cloudy_Light").env("Env_Zone3_Tundra").level(6)
            .top("Soil_Grass_Cold", 7, "Soil_Needles", 2, "Soil_Dirt_Cold", 1)
            .fill("Soil_Dirt_Cold", 4)
            .bottom("Rock_Shale", 3, "Rock_Basalt", 2)
            .accent(0.08, "Rock_Basalt", 1)
            .marker("Rock_Shale_Brick")
            .plants(0.45)
            .soilPlants("Plant_Fern_Winter", 3, "Plant_Fern_Forest", 3, "Plant_Grass_Winter", 2,
                    "Plant_Flower_Flax_Blue", 1, "Plant_Flower_Common_White2", 1, "Plant_Bush_Lush", 1)
            .anyPlants("Plant_Crop_Mushroom_Common_Brown", 1, "Plant_Crop_Mushroom_Cap_Brown", 1)
            .hanging(0.25, "Rock_Shale_Stalactites_Small", 2, "Rock_Basalt_Stalactite_Small", 1)
            .pillar("Wood_Redwood_Trunk")
            .tints("#487E4D", 3, "#36763C", 2, "#998E3E", 1)
            .tree("Wood_Fir_Trunk", "Plant_Leaves_Fir").tree("Wood_Fir_Trunk", "Plant_Leaves_Fir")
            .tree("Wood_Redwood_Trunk", "Plant_Leaves_Redwood")
            .scatter("Rubble_Basalt", 2)
            .build();

    public static final Theme BOREAL_REACH = Theme.builder("boreal_reach", "Boreal Reach")
            .zone(FROST).color("#5E9E6E").sound(Z3).weather("Zone3_Cloudy_Boreal").env("Env_Zone3_Forests").level(7)
            .top("Soil_Grass_Cold", 5, "Soil_Grass", 2, "Soil_Dirt_Cold", 1, "Soil_Pathway", 1)
            .fill("Soil_Dirt_Cold", 3, "Soil_Dirt", 1)
            .bottom("Rock_Shale", 3, "Rock_Marble", 1)
            .accent(0.10, "Rock_Marble", 1)
            .marker("Rock_Shale_Brick")
            .plants(0.40)
            .soilPlants("Plant_Grass_Winter", 3, "Plant_Grass_Sharp_Tall", 1, "Plant_Flower_Common_White2", 1,
                    "Plant_Fern_Forest", 2, "Plant_Bush_Winter", 1, "Plant_Flower_Tall_Purple", 1)
            .anyPlants("Plant_Crop_Mushroom_Flatcap_Blue", 1)
            .hanging(0.30, "Rock_Shale_Stalactites_Small", 1)
            .pillar("Wood_Cedar_Trunk")
            .tints("#3D6922", 2, "#5C5B30", 1, "#487E4D", 2)
            .tree("Wood_Cedar_Trunk", "Plant_Leaves_Cedar").tree("Wood_Cedar_Trunk", "Plant_Leaves_Cedar")
            .tree("Wood_Fir_Trunk", "Plant_Leaves_Fir")
            .scatter("Rubble_Shale", 2, "Rubble_Stone", 1)
            .build();

    public static final Theme OUTLANDER_CAMP = Theme.builder("outlander_camp", "Outlander Camp")
            .zone(FROST).color("#9AA7B0").sound(Z3).weather("Zone3_Cloudy_Medium").env("Env_Zone3_Outlander").level(7)
            .top("Rock_Shale", 3, "Rock_Shale_Brick", 2, "Rock_Shale_Cobble", 2, "Wood_Redwood_Planks", 1,
                    "Soil_Dirt_Cold", 1)
            .fill("Rock_Shale", 3, "Soil_Dirt_Cold", 1)
            .bottom("Rock_Shale", 3, "Rock_Basalt_Cobble", 1)
            .accent(0.10, "Cloth_Block_Wool_White", 1, "Wood_Darkwood_Planks", 1)
            .marker("Rock_Shale_Brick")
            .plants(0.15)
            .soilPlants("Plant_Grass_Winter", 1)
            .hanging(0.30, "Rock_Shale_Stalactites_Small", 1)
            .pillar("Wood_Fir_Trunk")
            .tints("#487E4D", 1)
            .tree("Wood_Fir_Trunk", "Plant_Leaves_Fir").tree("Rock_Shale_Brick", null)
            .scatter("Rubble_Shale", 2)
            .build();

    public static final Theme THE_EVERFROST = Theme.builder("the_everfrost", "The Everfrost")
            .zone(FROST).color("#BFE6FF").sound(Z3).weather("Zone3_Snow").env("Env_Zone3_Glacial").level(8)
            .top("Soil_Snow_Brick", 5, "Soil_Grass_Cold", 2, "Rock_Shale", 1)
            .fill("Soil_Dirt_Cold", 2, "Rock_Shale", 2)
            .bottom("Rock_Ice", 3, "Rock_Ice_Permafrost", 2, "Rock_Shale", 2)
            .accent(0.12, "Rock_Ice_Permafrost", 1)
            .marker("Rock_Crystal_White_Block")
            .plants(0.35)
            .soilPlants("Plant_Grass_Winter", 2, "Plant_Grass_Snowy", 2, "Plant_Bush_Winter_Snow", 1,
                    "Plant_Bush_Winter_Sharp", 1, "Plant_Flower_Bushy_White", 1, "Plant_Flower_Common_Violet", 1)
            .hanging(0.40, "Rock_Ice_Icicles", 1)
            .pillar("Wood_Fir_Trunk")
            .tints("#699587", 3, "#487E4D", 1)
            .tree("Wood_Fir_Trunk", "Plant_Leaves_Fir_Snow")
            .scatter("Rubble_Ice", 2, "Rubble_Shale", 1)
            .build();

    // ------------------------------------------------------------------ Devastated Lands (zone 4)

    public static final Theme CINDER_WASTES = Theme.builder("cinder_wastes", "Cinder Wastes")
            .zone(DEVASTATED).color("#C08A5A").sound(Z4).weather("Zone4_AshWastes").env("Env_Zone4_Wastes").level(9)
            .top("Soil_Grass_Burnt", 4, "Soil_Dirt_Burnt", 2, "Soil_Ash", 2, "Rock_Slate", 1)
            .fill("Rock_Basalt", 3, "Soil_Dirt_Burnt", 1)
            .bottom("Rock_Slate", 3, "Rock_Volcanic", 2, "Rock_Basalt", 1)
            .accent(0.08, "Rock_Crystal_Blue_Block", 1)
            .marker("Rock_Crystal_Blue_Block")
            .plants(0.40)
            .soilPlants("Plant_Grass_Gnarled", 3, "Plant_Grass_Gnarled_Tall", 1, "Plant_Bush_Dead_Twisted", 2,
                    "Plant_Bush_Dead", 1, "Plant_Crop_Wheat_Stage_4_Burnt", 1)
            .anyPlants("Plant_Leaves_Autumn_Floor", 2, "Plant_Crop_Mushroom_Cap_Green", 1,
                    "Plant_Crop_Mushroom_Glowing_Violet", 1)
            .hanging(0.30, "Rock_Basalt_Stalactite_Small", 1, "Rock_Slate_Stalactite_Small", 1)
            .pillar("Wood_Petrified_Trunk")
            .tints("#786133", 2, "#766633", 1, "#62626C", 1, "#595F6D", 1)
            .tree("Wood_Petrified_Trunk", "Plant_Leaves_Petrified")
            .scatter("Rubble_Basalt", 2, "Rubble_Volcanic", 1)
            .build();

    public static final Theme CHARRED_WOODLANDS = Theme.builder("charred_woodlands", "Charred Woodlands")
            .zone(DEVASTATED).color("#FF7A3D").sound(Z4).weather("Zone4_Forest_Burnt").env("Env_Zone4_Forests").level(10)
            .top("Soil_Grass_Burnt", 3, "Soil_Ash", 3, "Rock_Slate", 2, "Rock_Volcanic", 2)
            .fill("Rock_Slate", 3, "Soil_Ash", 1)
            .bottom("Rock_Volcanic", 3, "Rock_Slate", 2, "Rock_Basalt", 1)
            .accent(0.15, "Rock_Volcanic_Cracked_Lava", 2, "Rock_Crystal_Red_Block", 1)
            .marker("Rock_Volcanic_Cracked_Lava")
            .plants(0.35)
            .soilPlants("Plant_Grass_Jungle", 2, "Plant_Grass_Jungle_Short", 1, "Plant_Grass_Gnarled", 2,
                    "Plant_Bush_Dead_Twisted", 1, "Plant_Bush_Crystal", 1, "Plant_Bush_Jungle", 1,
                    "Plant_Flower_Tall_Purple", 1)
            .anyPlants("Plant_Crop_Mushroom_Glowing_Red", 1)
            .hanging(0.35, "Rock_Volcanic_Stalactite_Small", 2, "Rock_Basalt_Stalactite_Small", 1)
            .pillar("Wood_Burnt_Trunk")
            .tints("#A29D55", 2, "#AFAA5F", 1, "#4E4E4E", 1)
            .tree("Wood_Burnt_Trunk", "Plant_Leaves_Burnt")
            .scatter("Rubble_Volcanic", 2, "Rubble_Slate", 1)
            .build();

    /** Every run begins here. */
    public static final Theme START = DRIFTING_PLAINS;

    /** All areas; after the start, the next area is always picked at random. */
    public static final List<Theme> ALL = List.of(
            DRIFTING_PLAINS, SEEDLING_WOODS, THE_FENS, KWEEBEC_VILLAGE, TRORK_OUTPOST, FORGOTTEN_TEMPLE,
            GOLDEN_STEPPES, FERAN_VILLAGE, BADLANDS, SCARAK_HIVE, DESOLATE_BASIN,
            CRYSTALLINE_DEPTHS,
            FROSTMARCH_TUNDRA, BOREAL_REACH, OUTLANDER_CAMP, THE_EVERFROST,
            CINDER_WASTES, CHARRED_WOODLANDS);
}
