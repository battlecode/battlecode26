import { flatbuffers, schema } from 'battlecode-schema'
import assert from 'assert'
import Game, { Team } from './Game'
import Round from './Round'
import * as renderUtils from '../util/RenderUtil'
import { MapEditorBrush } from '../components/sidebar/map-editor/MapEditorBrush'
import { StaticMap } from './Map'
import { Vector, vectorDistSquared, vectorEq } from './Vector'
import {
    DIRECTIONS,
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
        const dir = spawnAction.dir()
        const chirality = spawnAction.chirality()

        return this.spawnBodyFromValues(id, robotType, this.game.getTeamByID(team), { x, y }, dir, chirality)
    }

    spawnBodyFromValues(
        id: number,
        type: schema.RobotType,
        team: Team,
        pos: Vector,
        dir: number,
        chirality: number
    ): Body {
        assert(!this.bodies.has(id), `Trying to spawn body with id ${id} that already exists`)

        const bodyClass = BODY_DEFINITIONS[type] ?? assert.fail(`Body type ${type} not found in BODY_DEFINITIONS`)

        const body = new bodyClass(this.game, pos, team, id)
        body.direction = dir
        body.chirality = chirality

        // if (this.checkBodyCollisionAtLocation(type, pos)) {
        //     assert.fail(`Trying to spawn body of type ${type} at occupied location (${pos.x}, ${pos.y})`)
        // }

        this.bodies.set(id, body)

        // Populate default hp, cooldowns, etc
        body.populateDefaultValues()

        return body
    }

    checkBodyCollisionAtLocation(type: schema.RobotType, pos: Vector): boolean {
        const occupiedSpaces: Vector[] = []

        for (const otherBody of this.bodies.values()) {
            if (otherBody.robotType == schema.RobotType.CAT) {
                for (let xoff = 0; xoff <= 1; xoff++) {
                    for (let yoff = 0; yoff <= 1; yoff++) {
                        occupiedSpaces.push({ x: otherBody.pos.x + xoff, y: otherBody.pos.y + yoff })
                    }
                }
            }
            if (otherBody.robotType == schema.RobotType.RAT) {
                occupiedSpaces.push({ x: otherBody.pos.x, y: otherBody.pos.y })
            }
            if (otherBody.robotType == schema.RobotType.RAT_KING) {
                for (let xoff = -1; xoff <= 1; xoff++) {
                    for (let yoff = -1; yoff <= 1; yoff++) {
                        occupiedSpaces.push({ x: otherBody.pos.x + xoff, y: otherBody.pos.y - yoff })
                    }
                }
            }
        }
        // check occupied spaces
        for (const space of occupiedSpaces) {
            // console.log(`Checking occupied space at (${space.x}, ${space.y}) against new body at (${pos.x}, ${pos.y}) with size ${bodySize}`) ;
            if (type == schema.RobotType.RAT) {
                if (space.x == pos.x && space.y == pos.y) {
                    return true
                }
            }
            if (type == schema.RobotType.CAT) {
                for (let xoff = 0; xoff <= 1; xoff++) {
                    for (let yoff = 0; yoff <= 1; yoff++) {
                        if (space.x == pos.x + xoff && space.y == pos.y + yoff) {
                            return true
                        }
                    }
                }
            }
            if (type == schema.RobotType.RAT_KING) {
                for (let xoff = -1; xoff <= 1; xoff++) {
                    for (let yoff = -1; yoff <= 1; yoff++) {
                        if (space.x == pos.x + xoff && space.y == pos.y - yoff) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    checkBodyOutofBoundsAtLocation(type: schema.RobotType, pos: Vector): boolean {
        const map = this.game.currentMatch?.map
        if(!map) return false

        const dimension = map.dimension
        const occupiedSpaces: Vector[] = []

        if (type == schema.RobotType.RAT) {
            if(!map.inBounds(pos.x, pos.y)) return false
        }
        if (type == schema.RobotType.CAT) {
            for (let xoff = 0; xoff <= 1; xoff++) {
                for (let yoff = 0; yoff <= 1; yoff++) {
                    if(!map.inBounds(pos.x+xoff, pos.y+yoff)) return false
                }
            }
        }
        if (type == schema.RobotType.RAT_KING) {
            for (let xoff = -1; xoff <= 1; xoff++) {
                for (let yoff = -1; yoff <= 1; yoff++) {
                    if(!map.inBounds(pos.x+xoff, pos.y+yoff)) return false
                }
            }
        }
        
        return true
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
        body.direction = turn.dir()
        body.hp = Math.max(turn.health(), 0)
        body.moveCooldown = turn.moveCooldown()
        body.actionCooldown = turn.actionCooldown()
        body.turningCooldown = turn.turningCooldown()
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
            const hovered = !!hoveredTile && this.getBodyAtLocation(hoveredTile.x, hoveredTile.y)?.id === body.id
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
            // this is a bit gross but oh well, we should really make a better way to handle body shapes

            if (body.robotType == schema.RobotType.CAT) {
                for (let xoff = 0; xoff <= 1; xoff++) {
                    for (let yoff = 0; yoff <= 1; yoff++) {
                        if (teamMatches && body.pos.x + xoff === x && body.pos.y + yoff === y) {
                            if (!body.dead) return body
                            foundDead = body
                        }
                    }
                }
            }
            if (body.robotType == schema.RobotType.RAT) {
                if (teamMatches && body.pos.x === x && body.pos.y === y) {
                    if (!body.dead) return body
                    foundDead = body
                }
            }
            if (body.robotType == schema.RobotType.RAT_KING) {
                for (let xoff = -1; xoff <= 1; xoff++) {
                    for (let yoff = -1; yoff <= 1; yoff++) {
                        if (teamMatches && body.pos.x + xoff === x && body.pos.y - yoff === y) {
                            if (!body.dead) return body
                            foundDead = body
                        }
                    }
                }
            }
            //     // If dead, keep iterating in case there is an alive body
            //     // that will take priority
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
            schema.SpawnAction.createSpawnAction(
                builder,
                body.id,
                body.pos.x,
                body.pos.y,
                body.direction,
                body.chirality,
                body.team.id,
                body.robotType
            )
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
    public direction: number = 0
    public chirality: number = 0
    public moveCooldown: number = 0
    public actionCooldown: number = 0
    public turningCooldown: number = 0
    public carriedRobot: number | undefined = undefined  // id of carried robot
    public carrierRobot: number | undefined = undefined  // id of robot carrying this robot
    public beingCarried: boolean = false
    public bytecodesUsed: number = 0
    public cheese: number = 0
    public textureOverride: boolean = false

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
        const OFFSET = { x: .35, y: 0 } // to center the rat on the tile
        const ratnapped = this.robotType === schema.RobotType.RAT && this.beingCarried

        if(ratnapped) this.pos.x += OFFSET.x
        const pos = this.getInterpolatedCoords(match)
        if(ratnapped) this.pos.x -= OFFSET.x

        const renderCoords = renderUtils.getRenderCoords(pos.x, pos.y, match.currentRound.map.staticMap.dimension)

        if (this.robotType == schema.RobotType.CAT) {
            renderCoords.x += (this.size - 1) * 0.5
            renderCoords.y -= (this.size - 1) * 0.5
        }

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
        const directionAngles = [
            -1, //should not happen
            180,
            225,
            270,
            315,
            0,
            45,
            90,
            135
        ]
        const ceiledRadius = Math.ceil(Math.sqrt(radius)) + 1
        const minX = Math.max(location.x - ceiledRadius, 0)
        const minY = Math.max(location.y - ceiledRadius, 0)
        const maxX = Math.min(location.x + ceiledRadius, match.map.width - 1)
        const maxY = Math.min(location.y + ceiledRadius, match.map.height - 1)

        const coords: Vector[] = [location]
        const halfFOV = fov / 2
        if (direction == 0) {
            return coords
        }

        const directionRad = (directionAngles[direction] * Math.PI) / 180

        for (let x = minX; x <= maxX; x++) {
            for (let y = minY; y <= maxY; y++) {
                const dx = x - location.x
                const dy = y - location.y
                if (dx * dx + dy * dy <= radius) {
                    const angleToPoint = Math.atan2(dy, dx)
                    let angleDiff = angleToPoint - directionRad
                    angleDiff = ((((angleDiff + Math.PI) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI)) - Math.PI
                    if (Math.abs(angleDiff) <= (halfFOV * Math.PI) / 180 + 0.0001) {
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

        // const pos = this.getInterpolatedCoords(match)
        const pos = this.pos

        if (lightly) ctx.globalAlpha = 0.5
        // const squares = this.getAllLocationsWithinRadiusSquared(match, pos, this.metadata.actionRadiusSquared())
        // ctx.beginPath()
        // ctx.strokeStyle = 'red'
        // ctx.lineWidth = 0.1
        // this.drawEdges(match, ctx, lightly, squares)

        ctx.beginPath()
        ctx.strokeStyle = 'blue'
        ctx.lineWidth = 0.1
        const squares2 = this.getAllLocationsWithinFOVAndRadiusSquared(
            match,
            pos,
            this.metadata.visionConeRadiusSquared(),
            this.direction,
            this.metadata.visionConeAngle()
        )
        this.drawEdges(match, ctx, lightly, squares2)

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
        const hpBarWidth = 0.8 * this.size
        const hpBarHeight = 0.1
        const hpBarYOffset = this.robotType === schema.RobotType.RAT_KING ? 1.4 : 0.4
        const hpBarXOffset = this.robotType === schema.RobotType.CAT ? 0.5 : 0
        const hpBarX = renderCoords.x + 0.5 - hpBarWidth / 2 + hpBarXOffset
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
        const directionMap = [
            'None',
            'West',
            'Southwest',
            'South',
            'Southeast',
            'East',
            'Northeast',
            'North',
            'Northwest'
        ]

        if (!this.game.playable) return [this.robotName]

        const defaultInfo = [
            `${this.robotName}`,
            `ID: ${this.id}`,
            `HP: ${this.hp}/${this.maxHp}`,
            `Location: (${this.pos.x}, ${this.pos.y})`,
            `Direction: ${directionMap[this.direction]}`,
            `${this.robotType === schema.RobotType.CAT || schema.RobotType.RAT_KING ? 'Chirality: ' + this.chirality : ''}`,
            `${this.robotType === schema.RobotType.RAT ? 'Cheese: ' + this.cheese : ''}`,
            `Move Cooldown: ${this.moveCooldown}`,
            `Action Cooldown: ${this.actionCooldown}`,
            `${this.robotType !== schema.RobotType.CAT ? 'Turning Cooldown: ' + this.turningCooldown : ''}`,
            `${this.carriedRobot !== undefined ? 'Carrying: ' + this.carriedRobot : ''}`,
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
        this.turningCooldown = metadata.turningCooldown()
        this.moveCooldown = metadata.movementCooldown()
    }

    public promoteTo(newType: schema.RobotType): void {
        // Only support rat -> rat-king promotions for now
        if (newType !== schema.RobotType.RAT_KING) return
        if (this.robotType === schema.RobotType.RAT_KING) return

        const bodyClass =
            BODY_DEFINITIONS[schema.RobotType.RAT_KING] ??
            assert.fail(`Body type ${schema.RobotType.RAT_KING} not found in BODY_DEFINITIONS`)
        const oldHp = this.hp

        // Change prototype so instance methods come from the RatKing class
        Object.setPrototypeOf(this, bodyClass.prototype)

        this.robotType = schema.RobotType.RAT_KING
        this.robotName = `${this.team.colorName} Rat King`
        this.size = 3
        const dir = this.direction
        this.imgPath = `robots/${this.team.colorName.toLowerCase()}/rat_king_64x64.png`
        this.populateDefaultValues()
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
            this.cheese = 0
        }

        public draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const dir = this.direction
            if (!this.textureOverride) this.imgPath = `robots/${this.team.colorName.toLowerCase()}/rat_${dir}_64x64.png`
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
            const dir = this.direction
            if (!this.textureOverride) this.imgPath = `robots/${this.team.colorName.toLowerCase()}/rat_king_64x64.png`
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
            const dir = this.direction
            if (!this.textureOverride) this.imgPath = `robots/cat/cat_${dir}.png`
            super.draw(match, ctx)
        }
    }
}
