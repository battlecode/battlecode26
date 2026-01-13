package battlecode.server;

import java.io.File;

import battlecode.crossplay.CrossPlayLanguage;

public class Main {

    private static boolean runHeadless(Config options) {
        try {
            final Server server = new Server(
                    options,
                    false
            );

            final String teamA = options.get("bc.game.team-a");
            if (teamA == null) {
                System.err.println("Can't run match without bc.game.team-a set!");
                return false;
            }

            final String teamAURL;
            if (options.get("bc.game.team-a.url") != null) {
                teamAURL = options.get("bc.game.team-a.url");
            } else {
                System.err.println("Can't run match without bc.game.team-a.url set!");
                return false;
            }

            final String teamAPackage;
            if (options.get("bc.game.team-a.package") != null) {
                teamAPackage = options.get("bc.game.team-a.package");
            } else {
                teamAPackage = teamA;
            }

            final CrossPlayLanguage teamALanguage;
            if (options.get("bc.game.team-a.language") != null) {
                teamALanguage = CrossPlayLanguage.parse(options.get("bc.game.team-a.language"));
            } else {
                teamALanguage = CrossPlayLanguage.JAVA;
            }

            final String teamB = options.get("bc.game.team-b");
            if (teamB == null) {
                System.err.println("Can't run match without bc.game.team-b set!");
                return false;
            }

            final String teamBURL;
            if (options.get("bc.game.team-b.url") != null) {
                teamBURL = options.get("bc.game.team-b.url");
            } else {
                System.err.println("Can't run match without bc.game.team-b.url set!");
                return false;
            }

            final String teamBPackage;
            if (options.get("bc.game.team-b.package") != null) {
                teamBPackage = options.get("bc.game.team-b.package");
            } else {
                teamBPackage = teamB;
            }

            final CrossPlayLanguage teamBLanguage;
            if (options.get("bc.game.team-b.language") != null) {
                teamBLanguage = CrossPlayLanguage.parse(options.get("bc.game.team-b.language"));
            } else {
                teamBLanguage = CrossPlayLanguage.JAVA;
            }

            final String mapsCommaSep = options.get("bc.game.maps");
            if (mapsCommaSep == null) {
                System.err.println("Can't run match without bc.game.maps set!");
                return false;
            }
            final String[] maps = mapsCommaSep.split(",");

            File saveFile;
            if (options.get("bc.server.save-file") != null) {
                saveFile = new File(options.get("bc.server.save-file"));
            } else {
                System.err.println("Can't run match without bc.server.save-file set!");
                return false;
            }

            server.addGameNotification(new GameInfo(
                    teamA, teamALanguage, teamAPackage, teamAURL,
                    teamB, teamBLanguage, teamBPackage, teamBURL,
                    maps,
                    saveFile,
                    options.getBoolean("bc.game.best-of-three") && maps.length == 3
            ));
            server.terminateNotification();

            server.run();

            return server.getState() == ServerState.FINISHED;
        } catch (Exception e) {
            ErrorReporter.report(e, true);
            return false;
        }
    }

    public static Config setupConfig(String[] args) {
        try {
            Config options = new Config(args);
            Config.setGlobalConfig(options);
            return options;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(64);
            return null;
        }
    }

    public static boolean run(Config options) {
        Server.Mode mode;
        try {
            mode = Server.Mode.valueOf(options.get("bc.server.mode").toUpperCase());
        } catch (Exception e) {
            System.err.println("Failed to get bc.server.mode mode, using headless");
            mode = Server.Mode.HEADLESS;
        }

        switch (mode) {
            case HEADLESS:
                return runHeadless(options);
            default:
                return false;
        }
    }

    public static void main(String[] args) {
        final Config options = setupConfig(args);

        if (!run(options)) {
            System.err.println("invalid bc.server.mode");
            System.exit(64);
        } else {
            System.exit(0);
        }

    }

}
