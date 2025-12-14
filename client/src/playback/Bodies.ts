import { flatbuffers, schema } from 'battlecode-schema'
import assert from 'assert'
import Game, { Team } from './Game'
import Round from './Round'
import * as renderUtils from '../util/RenderUtil'
import { MapEditorBrush } from '../components/sidebar/map-editor/MapEditorBrush'
import { StaticMap } from './Map'
import { Vector, vectorDistSquared, vectorEq } from './Vector'
import {
    INDICATOR_DOT_SIZE,
    INDICATOR_LINE_WIDTH,
    TOOLTIP_PATH_DECAY_OPACITY,
    TOOLTIP_PATH_DECAY_R,
    TOOLTIP_PATH_INIT_R,
    TOOLTIP_PATH_LENGTH
} from '../constants'
import Match from './Match'
import { ClientConfig } from '../client-config'
import { CatBrush, RatKingBrush, RobotBrush } from './Brushes'
import { getImageIfLoaded } from '../util/ImageLoader'

export default class Bodies {
    public bodies: Map<number, Body> = new Map()

    constructor(
        public readonly game: Game,
        initialBodies?: schema.InitialBodyTable
    ) {
        if (initialBodies) {
            this.insertInitialBodies(initialBodies)
        }
    }

    processRoundEnd(delta: schema.Round | null) {
        // Process unattributed died bodies
        if (delta) {
            for (let i = 0; i < delta.diedIdsLength(); i++) {
                const diedId = delta.diedIds(i)!
                this.getById(diedId).dead = true
            }
        }

        // Update body interp positions
        // We need to update position here so that interp works correctly
        for (const body of this.bodies.values()) {
            body.lastPos = body.pos
        }
    }

    clearDiedBodies() {
        // Remove if marked dead
        for (const body of this.bodies.values()) {
            if (!body.dead) continue

            this.bodies.delete(body.id) // safe
        }
    }

    spawnBodyFromAction(spawnAction: schema.SpawnAction): Body {
        // This assumes ids are never reused
        const id = spawnAction.id()
        assert(!this.bodies.has(id), `Trying to spawn body with id ${id} that already exists`)

        const robotType = spawnAction.robotType()
        const team = spawnAction.team()
        const x = spawnAction.x()
        const y = spawnAction.y()

        return this.spawnBodyFromValues(id, robotType, this.game.getTeamByID(team), { x, y })
    }

    spawnBodyFromValues(id: number, type: schema.RobotType, team: Team, pos: Vector): Body {
        assert(!this.bodies.has(id), `Trying to spawn body with id ${id} that already exists`)

        const bodyClass = BODY_DEFINITIONS[type] ?? assert.fail(`Body type ${type} not found in BODY_DEFINITIONS`)

        

        const body = new bodyClass(this.game, pos, team, id)

        // if (this.checkBodyCollisionAtLocation(type, pos)) {
        //     assert.fail(`Trying to spawn body of type ${type} at occupied location (${pos.x}, ${pos.y})`)
        // }

        this.bodies.set(id, body)

        // Populate default hp, cooldowns, etc
        body.populateDefaultValues()

        return body
    }

    checkBodyCollisionAtLocation(type: schema.RobotType, pos: Vector): boolean {
        const bodyClass = BODY_DEFINITIONS[type] ?? assert.fail(`Body type ${type} not found in BODY_DEFINITIONS`)
        const tempBody = new bodyClass(this.game, pos, this.game.getTeamByID(1), 0)
        const bodySize = tempBody.size;
        const occupiedSpaces: Vector[] = []

        for (const otherBody of this.bodies.values()) {
            if (otherBody.id === tempBody.id || otherBody.dead) continue // skip self or dead
            for(let xoff = 0; xoff < otherBody.size; xoff++){
                for(let yoff = 0; yoff < otherBody.size; yoff++){
                    occupiedSpaces.push({ x: otherBody.pos.x + xoff, y: otherBody.pos.y - yoff } )
                    // console.log(`Added occupied space at (${otherBody.pos.x + xoff}, ${otherBody.pos.y - yoff}) for body ID ${otherBody.id}`) ;
                }
            }
        }
        // check occupied spaces
        for (const space of occupiedSpaces){
            // console.log(`Checking occupied space at (${space.x}, ${space.y}) against new body at (${pos.x}, ${pos.y}) with size ${bodySize}`) ;
            if(space.x-pos.x < bodySize &&  space.x-pos.x >= 0 && pos.y-space.y < bodySize && pos.y-space.y >= 0){
                return true;
            }
        }

        return false
    }

    markBodyAsDead(id: number): void {
        const body = this.getById(id)
        body.dead = true
        // Manually set hp since we don't receive a final delta
        body.hp = 0
    }

    removeBody(id: number): void {
        this.bodies.delete(id)
    }

    /**
     * Clears all indicator objects from the given robot. If the id does not exist
     * (i.e. it has not yet been spawned), does nothing
     */
    clearIndicators(id: number) {
        if (!this.bodies.has(id)) return
        const body = this.getById(id)
        body.indicatorDots = []
        body.indicatorLines = []
        body.indicatorString = ''
    }

    /**
     * Applies a delta to the bodies array. Because of update order, bodies will first
     * be inserted, followed by a call to scopedCallback() in which all bodies are valid.
     */
    applyTurnDelta(round: Round, turn: schema.Turn): void {
        const body = this.getById(turn.robotId())

        // Update properties
        body.pos = { x: turn.x(), y: turn.y() }
        body.hp = Math.max(turn.health(), 0)
        body.moveCooldown = turn.moveCooldown()
        body.actionCooldown = turn.actionCooldown()
        body.bytecodesUsed = turn.bytecodesUsed()

        body.addToPrevSquares()
    }

    getById(id: number): Body {
        return this.bodies.get(id) ?? assert.fail(`Body with id ${id} not found in bodies`)
    }

    hasId(id: number): boolean {
        return this.bodies.has(id)
    }

    copy(): Bodies {
        const newBodies = new Bodies(this.game)
        newBodies.bodies = new Map(this.bodies)
        for (const body of this.bodies.values()) {
            newBodies.bodies.set(body.id, body.copy())
        }

        return newBodies
    }

    draw(
        match: Match,
        bodyCtx: CanvasRenderingContext2D | null,
        overlayCtx: CanvasRenderingContext2D | null,
        config: ClientConfig,
        // multiSelectMode: boolean = false,
        selectedBodyID?: number,
        selectedBodyIDs?: Array<number>,
        focusedBodyIDs?: Array<number>,
        hoveredTile?: Vector
    ): void {
        for (const body of this.bodies.values()) {
            if (bodyCtx) {
                body.draw(match, bodyCtx)
            }

            const selected = selectedBodyID === body.id || !!selectedBodyIDs?.includes(body.id)
            const hovered = !!hoveredTile && vectorEq(body.pos, hoveredTile)
            const focused = !!focusedBodyIDs?.includes(body.id)
            if (overlayCtx) {
                body.drawOverlay(match, overlayCtx, config, selected && focused, hovered || (selected && !focused))
            }
        }
    }

    getNextID(): number {
        return Math.max(0, ...this.bodies.keys()) + 1
    }

    getBodyAtLocation(x: number, y: number, team?: Team): Body | undefined {
        let foundDead: Body | undefined = undefined

        for (const body of this.bodies.values()) {
            const teamMatches = !team || body.team === team
            if (teamMatches && body.pos.x === x && body.pos.y === y) {
                if (!body.dead) return body

                // If dead, keep iterating in case there is an alive body
                // that will take priority
                foundDead = body
            }
        }

        return foundDead
    }

    isEmpty(): boolean {
        return this.bodies.size === 0
    }

    getEditorBrushes(round: Round): MapEditorBrush[] {
        return [new RatKingBrush(round), new CatBrush(round), new RobotBrush(round)]
    }

    toInitialBodyTable(builder: flatbuffers.Builder): number {
        schema.InitialBodyTable.startSpawnActionsVector(builder, this.bodies.size)

        for (const body of this.bodies.values()) {
            schema.SpawnAction.createSpawnAction(builder, body.id, body.pos.x, body.pos.y, body.team.id, body.robotType)
        }
        const spawnActionsVector = builder.endVector()

        return schema.InitialBodyTable.createInitialBodyTable(builder, spawnActionsVector)
    }

    private insertInitialBodies(bodies: schema.InitialBodyTable): void {
        for (let i = 0; i < bodies.spawnActionsLength(); i++) {
            const spawnAction = bodies.spawnActions(i)!
            this.spawnBodyFromAction(spawnAction)
        }
    }
}

export class Body {
    public robotName: string = ''
    public robotType: schema.RobotType = schema.RobotType.NONE
    public imgPath: string = ''
    public size: number = 1
    public lastPos: Vector
    private prevSquares: Vector[]
    public indicatorDots: { location: Vector; color: string }[] = []
    public indicatorLines: { start: Vector; end: Vector; color: string }[] = []
    public indicatorString: string = ''
    public dead: boolean = false
    public hp: number = 0
    public maxHp: number = 1
    public direction: number = 0 // in degrees
    public level: number = 1 // For towers
    public moveCooldown: number = 0
    public actionCooldown: number = 0
    public bytecodesUsed: number = 0

    constructor(
        private game: Game,
        public pos: Vector,
        public readonly team: Team,
        public readonly id: number
    ) {
        this.lastPos = this.pos
        this.prevSquares = [this.pos]
    }

    get metadata() {
        return this.game.robotTypeMetadata.get(this.robotType) ?? assert.fail('Robot missing metadata!')
    }

    public drawOverlay(
        match: Match,
        ctx: CanvasRenderingContext2D,
        config: ClientConfig,
        selected: boolean,
        hovered: boolean
    ): void {
        if (!this.game.playable) return

        // Draw various statuses
        const focused = selected || hovered
        if (focused) {
            this.drawPath(match, ctx)
        }
        if (focused || config.showAllRobotRadii) {
            this.drawRadii(match, ctx, !selected)
        }
        // Determine whether to show indicators for this body. Indicators show if
        // user selects to see indicators for the team of this body, to show all indicators,
        // or if this body is selected / hovered.
        const teamIndicatorsEnabled =
            config.showAllIndicators ||
            (config.showTeamOneIndicators && this.team.id === 1) ||
            (config.showTeamTwoIndicators && this.team.id === 2)

        if (focused || teamIndicatorsEnabled) {
            // If user is hovering without selecting the body / opting in to see indicators,
            // show indicators lightly
            const lighter = !selected && !teamIndicatorsEnabled
            this.drawIndicators(match, ctx, lighter, config.indicatorOpacity)
        }
        if (focused || config.showHealthBars) {
            this.drawHealthBar(match, ctx)
        }

        // Draw bytecode overage indicator
        if (config.showExceededBytecode && this.bytecodesUsed >= this.metadata.bytecodeLimit()) {
            const pos = this.getInterpolatedCoords(match)
            const renderCoords = renderUtils.getRenderCoords(pos.x, pos.y, match.currentRound.map.staticMap.dimension)
            ctx.globalAlpha = 0.5
            ctx.fillStyle = 'red'
            ctx.fillRect(renderCoords.x, renderCoords.y, 1, 1)
            ctx.globalAlpha = 1.0
        }
    }

    public draw(match: Match, ctx: CanvasRenderingContext2D): void {
        const pos = this.getInterpolatedCoords(match)
        const renderCoords = renderUtils.getRenderCoords(pos.x, pos.y, match.currentRound.map.staticMap.dimension)


        renderCoords.x += (this.size-1)*0.5
        renderCoords.y += (this.size-1)*0.5


        if (this.dead) ctx.globalAlpha = 0.5
        renderUtils.renderCenteredImageOrLoadingIndicator(ctx, getImageIfLoaded(this.imgPath), renderCoords, this.size)
        ctx.globalAlpha = 1
    }

    private drawPath(match: Match, ctx: CanvasRenderingContext2D) {
        const interpolatedCoords = this.getInterpolatedCoords(match)
        let alphaValue = 1
        let radius = TOOLTIP_PATH_INIT_R
        let lastPos: Vector | undefined = undefined
        const posList = [...this.prevSquares, interpolatedCoords].reverse()
        for (const prevPos of posList) {
            const color = `rgba(255, 255, 255, ${alphaValue})`
            ctx.beginPath()
            ctx.fillStyle = color
            ctx.ellipse(prevPos.x + 0.5, match.map.height - (prevPos.y + 0.5), radius, radius, 0, 0, 360)
            ctx.fill()
            alphaValue *= TOOLTIP_PATH_DECAY_OPACITY
            radius *= TOOLTIP_PATH_DECAY_R
            if (lastPos) {
                ctx.beginPath()
                ctx.strokeStyle = color
                ctx.lineWidth = radius / 2
                ctx.moveTo(lastPos.x + 0.5, match.map.height - (lastPos.y + 0.5))
                ctx.lineTo(prevPos.x + 0.5, match.map.height - (prevPos.y + 0.5))
                ctx.stroke()
            }
            lastPos = prevPos
        }
    }

    private getAllLocationsWithinRadiusSquared(match: Match, location: Vector, radius: number) {
        const ceiledRadius = Math.ceil(Math.sqrt(radius)) + 1
        const minX = Math.max(location.x - ceiledRadius, 0)
        const minY = Math.max(location.y - ceiledRadius, 0)
        const maxX = Math.min(location.x + ceiledRadius, match.map.width - 1)
        const maxY = Math.min(location.y + ceiledRadius, match.map.height - 1)

        const coords: Vector[] = []
        for (let x = minX; x <= maxX; x++) {
            for (let y = minY; y <= maxY; y++) {
                const dx = x - location.x
                const dy = y - location.y
                if (dx * dx + dy * dy <= radius) {
                    coords.push({ x, y })
                }
            }
        }

        return coords
    }

    private getAllLocationsWithinFOVAndRadiusSquared(
        match: Match,
        location: Vector,
        radius: number,
        direction: number,
        fov: number
    ) {
        const ceiledRadius = Math.ceil(Math.sqrt(radius)) + 1
        const minX = Math.max(location.x - ceiledRadius, 0)
        const minY = Math.max(location.y - ceiledRadius, 0)
        const maxX = Math.min(location.x + ceiledRadius, match.map.width - 1)
        const maxY = Math.min(location.y + ceiledRadius, match.map.height - 1)

        const coords: Vector[] = []
        const halfFOV = fov / 2
        const directionRad = (direction * Math.PI) / 180

        for (let x = minX; x <= maxX; x++) {
            for (let y = minY; y <= maxY; y++) {
                const dx = x - location.x
                const dy = y - location.y
                if (dx * dx + dy * dy <= radius) {
                    const angleToPoint = Math.atan2(dy, dx)
                    let angleDiff = angleToPoint - directionRad
                    angleDiff = ((angleDiff + Math.PI) % (2 * Math.PI)) - Math.PI //normalize angle difference
                    if (Math.abs(angleDiff) <= (halfFOV * Math.PI) / 180) {
                        coords.push({ x, y })
                    }
                }
            }
        }

        return coords
    }

    private drawEdges(match: Match, ctx: CanvasRenderingContext2D, lightly: boolean, squares: Array<Vector>) {
        for (let i = 0; i < squares.length; ++i) {
            const squarePos = squares[i]
            const renderCoords = renderUtils.getRenderCoords(
                squarePos.x,
                squarePos.y,
                match.currentRound.map.staticMap.dimension
            )

            const hasTopNeighbor = squares.some((square) => square.x === squarePos.x && square.y === squarePos.y + 1)
            const hasBottomNeighbor = squares.some((square) => square.x === squarePos.x && square.y === squarePos.y - 1)
            const hasLeftNeighbor = squares.some((square) => square.x === squarePos.x - 1 && square.y === squarePos.y)
            const hasRightNeighbor = squares.some((square) => square.x === squarePos.x + 1 && square.y === squarePos.y)

            ctx.beginPath()

            if (!hasTopNeighbor) {
                ctx.moveTo(renderCoords.x, renderCoords.y)
                ctx.lineTo(renderCoords.x + 1, renderCoords.y)
            }

            if (!hasBottomNeighbor) {
                ctx.moveTo(renderCoords.x, renderCoords.y + 1)
                ctx.lineTo(renderCoords.x + 1, renderCoords.y + 1)
            }

            if (!hasLeftNeighbor) {
                ctx.moveTo(renderCoords.x, renderCoords.y)
                ctx.lineTo(renderCoords.x, renderCoords.y + 1)
            }

            if (!hasRightNeighbor) {
                ctx.moveTo(renderCoords.x + 1, renderCoords.y)
                ctx.lineTo(renderCoords.x + 1, renderCoords.y + 1)
            }

            ctx.stroke()
        }
    }

    private drawRadii(match: Match, ctx: CanvasRenderingContext2D, lightly: boolean) {
        // TODO: support vision cone

        //// const pos = this.getInterpolatedCoords(match)
        //const pos = this.pos

        //if (lightly) ctx.globalAlpha = 0.5
        //const squares = this.getAllLocationsWithinRadiusSquared(match, pos, this.metadata.actionRadiusSquared())
        //ctx.beginPath()
        //ctx.strokeStyle = 'red'
        //ctx.lineWidth = 0.1
        //this.drawEdges(match, ctx, lightly, squares)

        //ctx.beginPath()
        //ctx.strokeStyle = 'blue'
        //ctx.lineWidth = 0.1
        //const squares2 = this.getAllLocationsWithinRadiusSquared(match, pos, this.metadata.visionRadiusSquared())
        //this.drawEdges(match, ctx, lightly, squares2)

        // Currently vision/message radius are always the same
        /*
        ctx.beginPath()
        ctx.strokeStyle = 'brown'
        ctx.lineWidth = 0.1
        const squares3 = this.getAllLocationsWithinRadiusSquared(match, pos, this.metadata.messageRadiusSquared())
        this.drawEdges(match, ctx, lightly, squares3)
        */

        ctx.globalAlpha = 1
    }

    private drawIndicators(match: Match, ctx: CanvasRenderingContext2D, lighter: boolean, opacity: number): void {
        const dimension = match.currentRound.map.staticMap.dimension
        // Render indicator dots
        for (const data of this.indicatorDots) {
            ctx.globalAlpha = lighter ? 0.5 : opacity / 100
            const coords = renderUtils.getRenderCoords(data.location.x, data.location.y, dimension)
            ctx.beginPath()
            ctx.arc(coords.x + 0.5, coords.y + 0.5, INDICATOR_DOT_SIZE, 0, 2 * Math.PI, false)
            ctx.fillStyle = data.color
            ctx.fill()
            ctx.globalAlpha = 1
        }

        ctx.lineWidth = INDICATOR_LINE_WIDTH
        for (const data of this.indicatorLines) {
            ctx.globalAlpha = lighter ? 0.5 : opacity / 100
            const start = renderUtils.getRenderCoords(data.start.x, data.start.y, dimension)
            const end = renderUtils.getRenderCoords(data.end.x, data.end.y, dimension)
            ctx.beginPath()
            ctx.moveTo(start.x + 0.5, start.y + 0.5)
            ctx.lineTo(end.x + 0.5, end.y + 0.5)
            ctx.strokeStyle = data.color
            ctx.stroke()
            ctx.globalAlpha = 1
        }
    }

    private drawHealthBar(match: Match, ctx: CanvasRenderingContext2D): void {
        const dimension = match.currentRound.map.staticMap.dimension
        const interpCoords = this.getInterpolatedCoords(match)
        const renderCoords = renderUtils.getRenderCoords(interpCoords.x, interpCoords.y, dimension)
        const hpBarWidth = 0.8
        const hpBarHeight = 0.1
        const hpBarYOffset = 0.4
        const hpBarX = renderCoords.x + 0.5 - hpBarWidth / 2
        const hpBarY = renderCoords.y + 0.5 + hpBarYOffset
        ctx.fillStyle = 'rgba(0,0,0,.3)'
        ctx.fillRect(hpBarX, hpBarY, hpBarWidth, hpBarHeight)
        ctx.fillStyle = this.team.id == 1 ? 'red' : '#00ffff'
        ctx.fillRect(hpBarX, hpBarY, hpBarWidth * (this.hp / this.maxHp), hpBarHeight)
    }

    public getInterpolatedCoords(match: Match): Vector {
        return renderUtils.getInterpolatedCoords(this.lastPos, this.pos, match.getInterpolationFactor())
    }

    public onHoverInfo(): string[] {
        if (!this.game.playable) return [this.robotName]

        const defaultInfo = [
            `${this.robotName}${this.level === 2 ? ' (Lvl II)' : ''}${this.level >= 3 ? ' (Lvl III)' : ''}`,
            `ID: ${this.id}`,
            `HP: ${this.hp}/${this.maxHp}`,
            `Location: (${this.pos.x}, ${this.pos.y})`,
            `Move Cooldown: ${this.moveCooldown}`,
            `Action Cooldown: ${this.actionCooldown}`,
            `Bytecodes Used: ${this.bytecodesUsed}${
                this.bytecodesUsed >= this.metadata.bytecodeLimit() ? ' <EXCEEDED!>' : ''
            }`
        ]
        if (this.indicatorString != '') {
            defaultInfo.push(this.indicatorString)
        }

        return defaultInfo
    }

    public copy(): Body {
        // Creates a new object using this object's prototype and all its parameters.
        // this is a shallow copy, override this if you need a deep copy
        const newBody = Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))
        newBody.prevSquares = [...this.prevSquares]
        return newBody
    }

    public addToPrevSquares(): void {
        this.prevSquares.push(this.pos)
        if (this.prevSquares.length > TOOLTIP_PATH_LENGTH) {
            this.prevSquares.splice(0, 1)
        }
    }

    public populateDefaultValues(): void {
        if (!this.game.playable) return

        const metadata = this.metadata

        this.maxHp = metadata.baseHealth()
        this.hp = this.maxHp
        this.actionCooldown = metadata.actionCooldown()
        this.moveCooldown = metadata.movementCooldown()
    }
}

export const BODY_DEFINITIONS: Record<schema.RobotType, typeof Body> = {
    // For future games, this dictionary translate schema values of robot
    // types to their respective class, such as this:
    //
    // [schema.BodyType.HEADQUARTERS]: class Headquarters extends Body {
    // 	public robotName = 'Headquarters'
    // 	public actionRadius = 8
    // 	public visionRadius = 34
    // 	public type = schema.BodyType.HEADQUARTERS
    // 	constructor(pos: Vector, hp: number, team: Team, id: number) {
    // 		super(pos, hp, team, id)
    // 		this.imgPath = `robots/${team.color}_headquarters_smaller.png`
    //	}
    //	onHoverInfo(): string[] {
    // 		return super.onHoverInfo();
    // 	}
    // },
    //
    // This game has no types or headquarters to speak of, so there is only
    // one type pointed to by 0:

    [schema.RobotType.NONE]: class None extends Body {
        constructor(game: Game, pos: Vector, team: Team, id: number) {
            super(game, pos, team, id)

            throw new Error("Body type 'NONE' not supported")
        }
    },

    [schema.RobotType.RAT]: class Rat extends Body {
        public robotName = 'Rat'

        constructor(game: Game, pos: Vector, team: Team, id: number) {
            super(game, pos, team, id)
            this.robotName = `${team.colorName} Rat`
            this.robotType = schema.RobotType.RAT
            this.imgPath = `robots/${this.team.colorName.toLowerCase()}/rat_64x64.png`
            this.size = 1
        }

        public draw(match: Match, ctx: CanvasRenderingContext2D): void {
            super.draw(match, ctx)
        }
    },

    [schema.RobotType.RAT_KING]: class RatKing extends Body {
        public robotName = 'RatKing'

        constructor(game: Game, pos: Vector, team: Team, id: number) {
            super(game, pos, team, id)
            this.robotName = `${team.colorName} Rat King`
            this.robotType = schema.RobotType.RAT_KING
            this.imgPath = `robots/${this.team.colorName.toLowerCase()}/rat_king_64x64.png`
            this.size = 3
        }

        public draw(match: Match, ctx: CanvasRenderingContext2D): void {
            super.draw(match, ctx)
        }
    },

    [schema.RobotType.CAT]: class Cat extends Body {
        public robotName = 'Cat'

        constructor(game: Game, pos: Vector, team: Team, id: number) {
            super(game, pos, team, id)
            this.robotName = 'Cat'
            this.robotType = schema.RobotType.CAT
            this.imgPath = `robots/cat/cat.png`
            this.size = 2
        }

        public draw(match: Match, ctx: CanvasRenderingContext2D): void {
            super.draw(match, ctx)
        }
    }
}
