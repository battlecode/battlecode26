package battlecode.world;

import battlecode.common.*;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for LiveMap symmetry validation.
 */
public class LiveMapTest {

    /**
     * Helper to create a basic valid symmetric map with rat kings.
     */
    private MapBuilder createBasicMapBuilder(int width, int height, MapSymmetry symmetry) {
        MapBuilder builder = new MapBuilder("test", width, height, 0, 0, 1337);
        builder.setSymmetry(symmetry);

        // Add symmetric rat kings (required for valid map)
        int symX = symmetry == MapSymmetry.HORIZONTAL ? 5 : width - 1 - 5;
        int symY = symmetry == MapSymmetry.VERTICAL ? 5 : height - 1 - 5;

        builder.addBody(new RobotInfo(1, Team.A, UnitType.RAT_KING, 600,
                new MapLocation(5, 5), Direction.NORTH, 1, 0, null));
        builder.addBody(new RobotInfo(2, Team.B, UnitType.RAT_KING, 600,
                new MapLocation(symX, symY), Direction.NORTH, 1, 0, null));

        return builder;
    }

    // ==================== MAP DIMENSION TESTS ====================

    @Test
    public void testMapWidthExceedsMaxFails() {
        // MAP_MAX_WIDTH = 60, so 61 should fail
        MapBuilder builder = new MapBuilder("test", 61, 20, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 61, 20, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("MAP WIDTH EXCEEDS"));
    }

    @Test
    public void testMapWidthBeneathMinFails() {
        // MAP_MIN_WIDTH = 20, so 19 should fail
        MapBuilder builder = new MapBuilder("test", 19, 20, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 19, 20, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("MAP WIDTH BENEATH"));
    }

    @Test
    public void testMapHeightExceedsMaxFails() {
        // MAP_MAX_HEIGHT = 60, so 61 should fail
        MapBuilder builder = new MapBuilder("test", 20, 61, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 20, 61, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("MAP HEIGHT EXCEEDS"));
    }

    @Test
    public void testMapHeightBeneathMinFails() {
        // MAP_MIN_HEIGHT = 20, so 19 should fail
        MapBuilder builder = new MapBuilder("test", 20, 19, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 20, 19, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("MAP HEIGHT BENEATH"));
    }

    @Test
    public void testMapAtMinDimensionsPass() throws Exception {
        // Both at minimum (20x20) should pass
        MapBuilder builder = new MapBuilder("test", 20, 20, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 20, 20, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    @Test
    public void testMapAtMaxDimensionsPass() throws Exception {
        // Both at maximum (60x60) should pass
        MapBuilder builder = new MapBuilder("test", 60, 60, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);
        addSymmetricRatKings(builder, 60, 60, MapSymmetry.ROTATIONAL);

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    /**
     * Helper to add symmetric rat kings to a map.
     */
    private void addSymmetricRatKings(MapBuilder builder, int width, int height, MapSymmetry symmetry) {
        int x = 5;
        int y = 5;
        int symX = (symmetry == MapSymmetry.HORIZONTAL) ? x : width - 1 - x;
        int symY = (symmetry == MapSymmetry.VERTICAL) ? y : height - 1 - y;

        builder.addBody(new RobotInfo(1, Team.A, UnitType.RAT_KING, 600,
                new MapLocation(x, y), Direction.NORTH, 1, 0, null));
        builder.addBody(new RobotInfo(2, Team.B, UnitType.RAT_KING, 600,
                new MapLocation(symX, symY), Direction.NORTH, 1, 0, null));
    }

    // ==================== VALID SYMMETRY TESTS ====================

    @Test
    public void testValidRotationalSymmetry() throws Exception {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add symmetric walls
        builder.setSymmetricWalls(2, 3, true);

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    @Test
    public void testValidHorizontalSymmetry() throws Exception {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.HORIZONTAL);

        // Add symmetric walls (horizontal: x stays same, y flips)
        builder.setWall(5, 2, true);
        builder.setWall(5, 17, true);

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    @Test
    public void testValidVerticalSymmetry() throws Exception {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.VERTICAL);

        // Add symmetric walls (vertical: y stays same, x flips)
        builder.setWall(2, 5, true);
        builder.setWall(17, 5, true);

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    // ==================== INVALID SYMMETRY TESTS ====================

    @Test
    public void testAsymmetricWallsFail() {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add asymmetric wall (only on one side)
        builder.setWall(2, 3, true);
        // Don't add the symmetric wall at (17, 16)

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("symmetry"));
    }

    @Test
    public void testAsymmetricDirtFails() {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add asymmetric dirt
        builder.setDirt(4, 4, true);
        // Don't add the symmetric dirt

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("symmetry"));
    }

    @Test
    public void testAsymmetricCheeseFails() {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add asymmetric cheese amounts
        builder.setCheese(3, 3, 100);
        // Don't add matching cheese on other side

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("symmetry"));
    }

    @Test
    public void testAsymmetricRatKingsFail() {
        MapBuilder builder = new MapBuilder("test", 20, 20, 0, 0, 1337);
        builder.setSymmetry(MapSymmetry.ROTATIONAL);

        // Add rat kings in non-symmetric positions
        builder.addBody(new RobotInfo(1, Team.A, UnitType.RAT_KING, 600,
                new MapLocation(5, 5), Direction.NORTH, 1, 0, null));
        builder.addBody(new RobotInfo(2, Team.B, UnitType.RAT_KING, 600,
                new MapLocation(10, 10), Direction.NORTH, 1, 0, null)); // Wrong position for rotational

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("No robot at symmetric location"));
    }

    @Test
    public void testAsymmetricCatsFail() {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add cat in non-symmetric position
        // For CAT (size 2), symmetric of [8, 8] is [10, 10]
        builder.addBody(new RobotInfo(3, Team.NEUTRAL, UnitType.CAT, 4000,
                new MapLocation(8, 8), Direction.NORTH, 1, 0, null));
        // No matching cat at the correct symmetric position (10, 10)

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        assertTrue(exception.getMessage().contains("No robot at symmetric location"));
    }

    @Test
    public void testSymmetricCatsPass() throws Exception {
        MapBuilder builder = createBasicMapBuilder(20, 20, MapSymmetry.ROTATIONAL);

        // Add cats in symmetric positions
        // For CAT (size 2, even), symmetric position = [width - 2 - x, height - 2 - y]
        // Cat at [8, 8] -> symmetric at [20 - 2 - 8, 20 - 2 - 8] = [10, 10]
        builder.addBody(new RobotInfo(3, Team.NEUTRAL, UnitType.CAT, 4000,
                new MapLocation(8, 8), Direction.NORTH, 1, 0, null));
        builder.addBody(new RobotInfo(4, Team.NEUTRAL, UnitType.CAT, 4000,
                new MapLocation(10, 10), Direction.NORTH, 1, 0, null));

        LiveMap map = builder.build();
        map.assertIsValid(); // Should not throw
    }

    @Test
    public void testWrongDeclaredSymmetryFails() {
        MapBuilder builder = new MapBuilder("test", 20, 20, 0, 0, 1337);
        // Declare ROTATIONAL but build for HORIZONTAL
        builder.setSymmetry(MapSymmetry.ROTATIONAL);

        // Add rat kings symmetric for HORIZONTAL only (x same, y flips)
        builder.addBody(new RobotInfo(1, Team.A, UnitType.RAT_KING, 600,
                new MapLocation(5, 5), Direction.NORTH, 1, 0, null));
        builder.addBody(new RobotInfo(2, Team.B, UnitType.RAT_KING, 600,
                new MapLocation(5, 14), Direction.NORTH, 1, 0, null)); // Horizontal symmetric, not rotational

        // Add walls that are horizontally symmetric but not rotationally
        builder.setWall(3, 2, true);
        builder.setWall(3, 17, true); // Horizontal symmetric

        LiveMap map = builder.build();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            map.assertIsValid();
        });

        // Should fail on wall mismatch since walls are horizontally symmetric but not rotationally
        assertTrue(exception.getMessage().contains("mismatch") || exception.getMessage().contains("No robot"));
    }

    // ==================== DIAGNOSTIC TEST FOR ACTUAL MAPS ====================

    /**
     * Diagnostic test that loads all actual map files and reports which ones
     * pass or fail symmetry validation. This test never fails - it just reports.
     */
    @Test
    public void diagnoseAllMaps() throws IOException {
        File mapsDir = new File("maps");
        if (!mapsDir.exists()) mapsDir = new File("../maps");
        if (!mapsDir.exists()) mapsDir = new File("../../maps");

        if (!mapsDir.exists()) {
            System.out.println("Could not find maps directory, skipping diagnostic test");
            return;
        }

        File[] mapFiles = mapsDir.listFiles((dir, name) -> name.endsWith(".map26"));

        if (mapFiles == null || mapFiles.length == 0) {
            System.out.println("No .map26 files found in " + mapsDir.getAbsolutePath());
            return;
        }

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        final File finalMapsDir = mapsDir;
        for (File mapFile : mapFiles) {
            String mapName = mapFile.getName().replace(".map26", "");
            try {
                LiveMap map = GameMapIO.loadMap(mapName, finalMapsDir, false);
                map.assertIsValid();
                passed.add(mapName);
            } catch (Exception e) {
                failed.add(mapName + ": " + e.getMessage());
            }
        }

        System.out.println("\n========== MAP SYMMETRY DIAGNOSTIC ==========");
        System.out.println("Maps directory: " + mapsDir.getAbsolutePath());
        System.out.println("Total maps: " + (passed.size() + failed.size()));
        System.out.println("PASSED: " + passed.size());
        System.out.println("FAILED: " + failed.size());

        if (!failed.isEmpty()) {
            System.out.println("\n--- Failed Maps ---");
            for (String failure : failed) {
                System.out.println("  " + failure);
            }
        }

        if (!passed.isEmpty()) {
            System.out.println("\n--- Passed Maps ---");
            for (String success : passed) {
                System.out.println("  " + success);
            }
        }
    }

    /**
     * Debug a single map to see where robots actually are.
     */
    @Test
    public void debugSingleMapRobots() throws IOException {
        File mapsDir = new File("maps");
        if (!mapsDir.exists()) mapsDir = new File("../maps");
        if (!mapsDir.exists()) mapsDir = new File("../../maps");

        String mapName = "corridorofdoomanddespair"; // Change this to test different maps

        try {
            LiveMap map = GameMapIO.loadMap(mapName, mapsDir, false);
            MapSymmetry declared = map.getSymmetry();
            int width = map.getWidth();
            int height = map.getHeight();

            System.out.println("\n========== DEBUG: " + mapName + " ==========");
            System.out.println("Size: " + width + "x" + height);
            System.out.println("Declared symmetry: " + declared);
            System.out.println("\nInitial bodies:");

            RobotInfo[] bodies = map.getInitialBodies();
            for (RobotInfo r : bodies) {
                int expectedSymX = (declared == MapSymmetry.HORIZONTAL) ? r.location.x : width - 1 - r.location.x;
                int expectedSymY = (declared == MapSymmetry.VERTICAL) ? r.location.y : height - 1 - r.location.y;

                System.out.println("  " + r.type + " " + r.team + " at " + r.location +
                        " -> expected symmetric at [" + expectedSymX + ", " + expectedSymY + "]");
            }
            System.out.println("===========================================\n");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
