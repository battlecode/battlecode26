using BotControllerWorld;
using BotControllerWorld.wit.Imports.battlecode.botController.v2026_1_0;
using static BotControllerWorld.wit.Imports.battlecode.botController.v2026_1_0.ITypesImports;
using static BotControllerWorld.wit.Imports.battlecode.botController.v2026_1_0.IControllerImports;

namespace Battlecode.CSharp;

/// <summary>
/// Ported from RobotPlayer.java in example-bots
/// </summary>
internal class RobotPlayer
{
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    private int _turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    readonly Random _rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    readonly Direction[] _directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public RobotPlayer()
    {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        Console.WriteLine("I'm alive");

        // You can also use indicators to save debug notes in replays.
        SetIndicatorString("Hello world!");
    }

    /**
    * run() is the method that is called when a robot is instantiated in the Battlecode world.
    * It is like the main function for your robot. If this method returns, the robot dies!
    **/
    public void PlayTurn()
    {
        _turnCount += 1;  // We have now been alive for one more turn!

        // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
        try
        {
            // The same run() function is called for every robot on your team, even if they are
            // different types. Here, we separate the control depending on the UnitType, so we can
            // use different strategies on different robots. If you wish, you are free to rewrite
            // this into a different control structure!

            // Every 10 turns, print out what type of robot we are.
            if (_turnCount % 100 == 0)
            {
                Console.WriteLine("Turn " + _turnCount + ": I am a " + IControllerImports.GetType().ToString());
            }

            // Try to move forward one step.
            if (CanMoveForward())
            {
                Console.WriteLine("Turn " + _turnCount + ": Trying to move " + GetDirection());
                MoveForward();
            }
            else
            {
                Console.WriteLine("couldn't move forward on turn " + _turnCount + " at location " + GetLocation() + " facing " + GetDirection());
                // If we can't move forward, try to turn a random direction.
                int randomDirection = _rng.Next(8);

                if (CanTurn())
                {
                    //Turn(_directions[randomDirection]);
                }
            }
        }
        catch (WitException<GameActionError> e)
        {
            // Oh no! It looks like we did something illegal in the Battlecode world. You should
            // handle GameActionExceptions judiciously, in case unexpected events occur in the game
            // world. Remember, uncaught exceptions cause your robot to explode!
            Console.WriteLine("GameActionException");
            Console.WriteLine(e.Value);
        }
        catch (Exception e)
        {
            // Oh no! It looks like our code tried to do something bad. This isn't a
            // GameActionException, so it's more likely to be a bug in our code.
            Console.WriteLine("Exception");
            Console.WriteLine(e.Message);
        }
    }
}