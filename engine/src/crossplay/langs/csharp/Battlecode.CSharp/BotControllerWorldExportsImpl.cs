using Battlecode.CSharp;

namespace BotControllerWorld;

public class BotControllerWorldExportsImpl : IBotControllerWorldExports
{
    private static RobotPlayer? s_inst;

    public static void Init()
    {
        s_inst ??= new();
        s_inst.PlayTurn();
    }

    public static void PlayTurn()
    {
        s_inst?.PlayTurn();
    }
}
