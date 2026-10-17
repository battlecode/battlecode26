import Match from './Match'
import { flatbuffers, schema } from 'battlecode-schema'
import { ungzip } from 'pako'
import assert from 'assert'
import { SPEC_VERSION, TEAM_COLOR_NAMES } from '../constants'
import { FakeGameWrapper } from '../components/sidebar/runner/websocket'
import { getTeamColors } from '../colors'

let nextID = 0

export type EventList = (
    | schema.GameHeader
    | schema.GameFooter
    | schema.MatchHeader
    | schema.MatchFooter
    | schema.Round
)[]

export default class Game {
    public readonly matches: Match[] = []
    public currentMatch: Match | undefined = undefined
    public readonly teams: [Team, Team]
    public winner: Team | null = null

    // Map of team id -> Team (includes neutral if present)
    private readonly teamMap: Map<number, Team> = new Map()

    // Metadata
    private readonly specVersion: string
    public readonly constants: schema.GameplayConstants
    public readonly robotTypeMetadata: Map<schema.RobotType, schema.RobotTypeMetadata>

    /**
     * Whether this game is playable (not currently being made in the map editor)
     */
    public readonly playable: boolean

    /**
     * The ID of this game. This is used to uniquely identify games in the UI, and is just based on uploaded order
     */
    public readonly id: number

    constructor(wrapper?: schema.GameWrapper | FakeGameWrapper) {
        this.playable = !!wrapper

        if (!wrapper) {
            //bare minimum setup for map editor
            this.teams = [
                new Team(TEAM_COLOR_NAMES[0], { wins: 0, elo: 0 }, 1, 'map_editor_red'),
                new Team(TEAM_COLOR_NAMES[1], { wins: 0, elo: 0 }, 2, 'map_editor_blue')
            ]
            this.teamMap.set(1, this.teams[0])
            this.teamMap.set(2, this.teams[1])
            this.winner = this.teams[0]
            this.specVersion = SPEC_VERSION
            this.constants = new schema.GameplayConstants()
            this.robotTypeMetadata = new Map()
            this.id = nextID++
            this.playable = false
            return
        }

        const eventCount = wrapper.eventsLength()
        const eventSlot = new schema.EventWrapper() // not sure what this is for, probably better performance (this is how it was done in the old client)

        // load header and metadata =============================================================================
        const gameHeaderEvent = wrapper.events(0, eventSlot) ?? assert.fail('Event was null')
        assert(gameHeaderEvent.eType() === schema.Event.GameHeader, 'First event must be GameHeader')
        const gameHeader = gameHeaderEvent.e(new schema.GameHeader()) as schema.GameHeader

        // check spec version
        this.specVersion = (gameHeader.specVersion() as string) || assert.fail('Unknown spec version')
        if (this.specVersion !== SPEC_VERSION) {
            throw new Error(
                `Your client is using spec version ${SPEC_VERSION}, but the game has spec version ${this.specVersion}. Try updating?`
            )
        }

        const foundTeams: (Team | undefined)[] = [undefined, undefined]
        for (let i = 0; i < gameHeader.teamsLength(); i++) {
            const data = gameHeader.teams(i) ?? assert.fail(`Team ${i} null`)
            const t = Team.fromSchema(data)
            this.teamMap.set(t.id, t)
            if (t.id === 1) foundTeams[0] = t
            else if (t.id === 2) foundTeams[1] = t
        }
        this.teams = [foundTeams[0] ?? assert.fail('Team A missing'), foundTeams[1] ?? assert.fail('Team B missing')]

        // load constants
        this.constants = gameHeader.constants() ?? assert.fail('Constants was null')

        // load metadata
        this.robotTypeMetadata = new Map()
        for (let i = 0; i < gameHeader.robotTypeMetadataLength(); i++) {
            const metadata = gameHeader.robotTypeMetadata(i)!
            this.robotTypeMetadata.set(metadata.type(), metadata)
        }

        // load all other events  ==========================================================================================
        for (let i = 1; i < eventCount; i++) {
            const event = wrapper.events(i, eventSlot) ?? assert.fail('Event was null')
            this.addEvent(event)
        }

        this.id = nextID++
    }

    get complete() {
        return this.winner !== null
    }

    /*
     * Adds a new game event to the game.
     */
    public addEvent(event: schema.EventWrapper): void {
        switch (event.eType()) {
            case schema.Event.GameHeader: {
                assert(false, 'Cannot add another GameHeader event to Game')
            }
            case schema.Event.MatchHeader: {
                const header = event.e(new schema.MatchHeader()) as schema.MatchHeader
                this.matches.push(Match.fromSchema(this, header))
                this.currentMatch = this.matches[this.matches.length - 1]
                return
            }
            case schema.Event.Round: {
                assert(
                    this.matches.length > 0,
                    'Cannot add Round event to Game if no MatchHeaders have been added first'
                )
                const round = event.e(new schema.Round()) as schema.Round
                this.matches[this.matches.length - 1].addNewRound(round)
                return
            }
            case schema.Event.MatchFooter: {
                assert(
                    this.matches.length > 0,
                    'Cannot add MatchFooter event to Game if no MatchHeaders have been added first'
                )
                const footer = event.e(new schema.MatchFooter()) as schema.MatchFooter
                this.matches[this.matches.length - 1].addMatchFooter(footer)
                return
            }
            case schema.Event.GameFooter: {
                assert(this.winner === null, 'Cannot add another GameFooter event to Game')
                const footer = event.e(new schema.GameFooter()) as schema.GameFooter
                this.winner = this.getTeamByID(footer.winner())
                return
            }
            default: {
                console.log(`Unknown event type: ${event.eType()}`)
                return
            }
        }
    }

    public getTeamByID(id: number): Team {
        const t = this.teamMap.get(id)
        if (t) return t
        // If server uses id 0 for NEUTRAL but didn't include it in header, create a neutral stub.
        if (id === 0) {
            const neutral = new Team('Neutral', { wins: 0, elo: 0 }, 0, 'neutral')
            this.teamMap.set(0, neutral)
            return neutral
        }
        throw new Error(`Team '${id}' not found`)
    }

    /**
     * Load a full game from a gzipped ArrayBuffer containing a GameWrapper.
     *
     * Do not mutate `data` after calling this function!
     */
    public static loadFullGameRaw(data: ArrayBuffer): Game {
        const ungzipped = ungzip(new Uint8Array(data))
        console.log('Game un-gzipped!')
        const wrapper = schema.GameWrapper.getRootAsGameWrapper(new flatbuffers.ByteBuffer(ungzipped))
        return new Game(wrapper)
    }
}

export class Team {
    public readonly colorName: string
    public readonly color: string
    constructor(
        public readonly name: string,
        public stats: TeamStat,
        public readonly id: number,
        public readonly packageName: string
    ) {
        // id 0 is Team.NEUTRAL; handle it explicitly so the client doesn't crash when
        // the server includes neutral team data in the header or actions reference team 0.
        if (this.id === 0) {
            this.colorName = 'Neutral'
            this.color = '#999999' // neutral gray
        } else {
            this.colorName = TEAM_COLOR_NAMES[id - 1]
            this.color = getTeamColors()[id - 1]
        }
    }

    static fromSchema(team: schema.TeamData) {
        const name = team.name() ?? assert.fail('Team name is missing')
        const stats = {
            wins: 0,
            elo: 0
        }
        const id = team.teamId() ?? assert.fail('Team id is missing')
        const packageName = team.packageName() ?? assert.fail('Team package name is missing')
        return new Team(name, stats, id, packageName)
    }
}

export type TeamStat = {
    wins: number
    elo: number
}
