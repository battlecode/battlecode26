import { schema } from 'battlecode-schema'
import {
    MapEditorBrushClickBehavior,
    MapEditorBrushField,
    MapEditorBrushFieldType,
    SinglePointMapEditorBrush,
    SymmetricMapEditorBrush,
    UndoFunction
} from '../components/sidebar/map-editor/MapEditorBrush'
import { ACTION_DEFINITIONS } from './Actions'
import Bodies from './Bodies'
import { CurrentMap, StaticMap } from './Map'
import { Vector } from './Vector'
import { Team } from './Game'
import Round from './Round'
import { GameRenderer } from './GameRenderer'
import { dir } from 'console'

const applyInRadius = (
    map: CurrentMap | StaticMap,
    x: number,
    y: number,
    radius: number,
    func: (idx: number) => void
) => {
    for (let i = -radius; i <= radius; i++) {
        for (let j = -radius; j <= radius; j++) {
            if (Math.sqrt(i * i + j * j) <= radius) {
                const target_x = x + i
                const target_y = y + j
                if (target_x >= 0 && target_x < map.width && target_y >= 0 && target_y < map.height) {
                    const target_idx = map.locationToIndex(target_x, target_y)
                    func(target_idx)
                }
            }
        }
    }
}

const squareIntersects = (check: Vector, center: Vector, radius: number) => {
    return (
        check.x >= center.x - radius &&
        check.x <= center.x + radius &&
        check.y >= center.y - radius &&
        check.y <= center.y + radius
    )
}

const checkValidCheeseMinePlacement = (check: Vector, map: StaticMap, bodies: Bodies) => {
    // Check if cheese mine is too close to the border
    if (check.x <= 1 || check.x >= map.width - 2 || check.y <= 1 || check.y >= map.height - 2) {
        return false
    }

    // Check if this is a valid cheese mine location
    const idx = map.locationToIndex(check.x, check.y)
    const cheeseMine = map.cheeseMines.findIndex((l) => squareIntersects(l, check, 4))
    const wall = map.walls.findIndex((v, i) => !!v && squareIntersects(map.indexToLocation(i), check, 2))
    const dirt = map.initialDirt[idx]

    let tower = undefined
    for (const b of bodies.bodies.values()) {
        if (squareIntersects(check, b.pos, 4)) {
            tower = b
            break
        }
    }

    if (tower || cheeseMine !== -1 || wall !== -1 || dirt) {
        return false
    }

    return true
}

const checkValidCatPlacement = (check: Vector, map: StaticMap, bodies: Bodies) => {
    return true
}

// Create minimal editor-only action data so the real Action
// subclasses in ACTION_DEFINITIONS can draw their visuals.
const makeEditorActionData = (
    map: StaticMap,
    atype: schema.Action,
    tx: number,
    ty: number,
    targetId?: number,
    bodies?: Bodies
) => {
    const mapWidth = map.width
    const mapHeight = map.height

    let targetX = tx
    let targetY = ty
    let validLocFound = false
    // find the first offset that yields a valid square inside the map
    let nx: number = targetX
    let ny: number = targetY
    while (!validLocFound) {
        if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
            targetX = nx
            targetY = ny
            break
        }
        nx = tx + Math.random() * 3 - 1
        ny = ty + Math.random() * 3 - 1
    }

    const loc = map.locationToIndex(targetX, targetY)

    // Update these action data based on action type; change this when the game changes
    switch (atype) {
        case schema.Action.BreakDirt:
            return { loc: () => loc }
        case schema.Action.CatFeed:
            return { loc: () => loc }
        case schema.Action.CatPounce:
            return { startLoc: () => loc, endLoc: () => map.locationToIndex(targetX + 1, targetY + 2) }
        case schema.Action.RatSqueak:
            return { id: () => targetId }
        case schema.Action.CatScratch:
            return { loc: () => loc }
        case schema.Action.CheesePickup:
            return { loc: () => loc }
        case schema.Action.CheeseSpawn:
            return { loc: () => loc, amount: () => 1 }
        case schema.Action.CheeseTransfer:
            return { id: () => targetId, amount: () => 1 }
        case schema.Action.DamageAction:
            return { id: () => targetId, damage: () => 1 }
        case schema.Action.DieAction:
            return { id: () => targetId, dieType: () => 0 }
        case schema.Action.PlaceTrap:
            return { isRatTrapType: () => true, loc: () => loc, team: () => 0 }
        case schema.Action.PlaceDirt:
            return { loc: () => loc }
        case schema.Action.RatAttack:
            return { id: () => targetId }
        case schema.Action.RatCollision:
            return { loc: () => loc }
        case schema.Action.RatNap:
            return { id: () => targetId }
        case schema.Action.TriggerTrap:
            return { loc: () => loc, team: () => 0 }
        case schema.Action.ThrowRat:
            return { id: () => targetId, loc: () => map.locationToIndex(targetX, targetY + 2) }
        case schema.Action.UpgradeToRatKing:
            return { phantom: () => targetId }
        default:
            return {}
    }
}

const findNearestRobotId = (bodies: Bodies, id: number, x: number, y: number): number | null => {
    // Find the nearest existing body (excluding the one we just spawned)
    let nearestId: number | null = null
    let nearestDist = Number.POSITIVE_INFINITY
    for (const b of bodies.bodies.values()) {
        if (b.id === id) continue
        const dx = b.pos.x - x
        const dy = b.pos.y - y
        const d2 = dx * dx + dy * dy
        if (d2 < nearestDist) {
            nearestDist = d2
            nearestId = b.id
        }
    }
    return nearestId
}

export class RobotBrush extends SinglePointMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Robots'
    public readonly fields = {
        isRobot: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        team: {
            type: MapEditorBrushFieldType.TEAM,
            value: 0
        },
        robotType: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: schema.RobotType.RAT,
            label: 'Robot Type',
            options: [
                { value: schema.RobotType.RAT, label: 'Rat' },
                { value: schema.RobotType.RAT_KING, label: 'Rat King' },
                { value: schema.RobotType.CAT, label: 'Cat' }
            ]
        },
        actionType: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: 0,
            label: 'Action Type',
            options: [
                { value: null, label: 'None' },
                { value: schema.Action.BreakDirt, label: 'Break Dirt' },
                { value: schema.Action.CatFeed, label: 'Cat Feed' },
                { value: schema.Action.CatPounce, label: 'Cat Pounce' },
                { value: schema.Action.RatSqueak, label: 'Rat Squeak' },
                { value: schema.Action.CatScratch, label: 'Cat Scratch' },
                { value: schema.Action.CheesePickup, label: 'Cheese Pickup' },
                { value: schema.Action.CheeseSpawn, label: 'Cheese Spawn' },
                { value: schema.Action.CheeseTransfer, label: 'Cheese Transfer' },
                { value: schema.Action.DamageAction, label: 'Damage' },
                { value: schema.Action.DieAction, label: 'Die' },
                { value: schema.Action.PlaceDirt, label: 'Place Dirt' },
                { value: schema.Action.PlaceTrap, label: 'Place Trap' },
                { value: schema.Action.RatAttack, label: 'Rat Attack' },
                { value: schema.Action.RatCollision, label: 'Rat Collision' },
                { value: schema.Action.RatNap, label: 'Rat Nap' },
                { value: schema.Action.TriggerTrap, label: 'Trigger Trap' },
                { value: schema.Action.ThrowRat, label: 'Throw Rat' },
                { value: schema.Action.UpgradeToRatKing, label: 'Upgrade To Rat King' }
            ]
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public singleApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const robotType: schema.RobotType = fields.robotType.value
        const actionType: schema.Action = fields.actionType.value
        const isRobot: boolean = fields.isRobot.value

        const add = (x: number, y: number, team: Team) => {
            const pos = { x, y }

            const id = this.bodies.getNextID()
            if (this.bodies.checkBodyCollisionAtLocation(robotType, pos)) return null

            this.bodies.spawnBodyFromValues(id, robotType, team, pos, 0, 0)

            return id
        }

        const remove = (x: number, y: number) => {
            const body = this.bodies.getBodyAtLocation(x, y)

            if (!body) return null

            const team = body.team
            this.bodies.removeBody(body.id)

            return team
        }

        if (isRobot) {
            let teamIdx = robotOne ? 0 : 1
            if (fields.team.value === 1) teamIdx = 1 - teamIdx
            const team = this.bodies.game.teams[teamIdx]
            const id = add(x, y, team)
            if (id && actionType) {
                const ActionCtor = ACTION_DEFINITIONS[actionType]
                if (ActionCtor) {
                    const targetIdToUse = findNearestRobotId(this.bodies, id, x, y) ?? id
                    const adata = makeEditorActionData(this.map, actionType, x, y, targetIdToUse, this.bodies)
                    const actionInstance = new ActionCtor(id, adata as any)
                    const actionsArray = this.bodies.game?.currentMatch?.currentRound?.actions?.actions
                    const currentRound = this.bodies.game?.currentMatch?.currentRound

                    if (actionsArray && currentRound) {
                        // Apply immediately for stateful actions (e.g., PaintAction) so map state changes in editor
                        actionInstance.apply(currentRound)
                        actionsArray.push(actionInstance)

                        return () => {
                            const arr = this.bodies.game?.currentMatch?.currentRound?.actions?.actions
                            if (arr) {
                                const idx = arr.indexOf(actionInstance)
                                if (idx >= 0) arr.splice(idx, 1)
                            }
                            this.bodies.removeBody(id)
                        }
                    }
                }
            }
            if (id) return () => this.bodies.removeBody(id)
            return null
        } else {
            const team = remove(x, y)
            if (!team) return null
            return () => add(x, y, team)
        }
    }
}

export class WallsBrush extends SymmetricMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Walls'
    public readonly fields = {
        shouldAdd: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        radius: {
            type: MapEditorBrushFieldType.POSITIVE_INTEGER,
            value: 1,
            label: 'Radius'
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>) {
        const add = (idx: number) => {
            // Check if this is a valid wall location
            const pos = this.map.indexToLocation(idx)
            const cheeseMine = this.map.cheeseMines.findIndex((l) => squareIntersects(l, pos, 2))
            const dirt = this.map.initialDirt[idx]

            for (const waypoints of this.map.catWaypoints.values()) {
                for (const waypoint of waypoints) {
                    if (waypoint.x === pos.x && waypoint.y === pos.y) return true
                    for (let nei of this.map.getNeighbors(waypoint.x, waypoint.y)) {
                        if (nei.x === pos.x && nei.y === pos.y) return true
                    }
                }
            }

            if (cheeseMine !== -1 || dirt) return true

            this.map.walls[idx] = 1
        }

        const remove = (idx: number) => {
            this.map.walls[idx] = 0
        }

        const radius: number = fields.radius.value - 1
        const changes: { idx: number; prevValue: number }[] = []
        applyInRadius(this.map, x, y, radius, (idx) => {
            const prevValue = this.map.walls[idx]
            if (fields.shouldAdd.value) {
                if (add(idx)) return
                changes.push({ idx, prevValue })
            } else {
                remove(idx)
                changes.push({ idx, prevValue })
            }
        })

        return () => {
            changes.forEach(({ idx, prevValue }) => {
                this.map.walls[idx] = prevValue
            })
        }
    }
}

export class CheeseMinesBrush extends SymmetricMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Cheese Mines'
    public readonly fields = {
        shouldAdd: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>) {
        const add = (x: number, y: number) => {
            if (!checkValidCheeseMinePlacement({ x, y }, this.map, this.bodies)) {
                return true
            }

            this.map.cheeseMines.push({ x, y })
        }

        const remove = (x: number, y: number) => {
            const foundIdx = this.map.cheeseMines.findIndex((l) => l.x === x && l.y === y)
            if (foundIdx === -1) return true
            this.map.cheeseMines.splice(foundIdx, 1)
        }

        if (fields.shouldAdd.value) {
            if (add(x, y)) return null
            return () => remove(x, y)
        } else {
            if (remove(x, y)) return null
            return () => add(x, y)
        }
    }
}

export class DirtBrush extends SymmetricMapEditorBrush<CurrentMap> {
    private readonly bodies: Bodies
    public readonly name = 'Dirt'
    public readonly fields = {
        shouldAdd: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        radius: {
            type: MapEditorBrushFieldType.POSITIVE_INTEGER,
            value: 1,
            label: 'Radius'
        }
    }

    constructor(round: Round) {
        super(round.map)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const add = (idx: number) => {
            // Check if this is a valid paint location
            const pos = this.map.indexToLocation(idx)
            const cheeseMine = this.map.staticMap.cheeseMines.find((r) => r.x === pos.x && r.y === pos.y)
            const wall = this.map.staticMap.walls[idx]
            const body = this.bodies.getBodyAtLocation(pos.x, pos.y)
            if (body || cheeseMine || wall) return true
            this.map.dirt[idx] = 1
            this.map.staticMap.initialDirt[idx] = this.map.dirt[idx]
        }

        const remove = (idx: number) => {
            this.map.dirt[idx] = 0
            this.map.staticMap.initialDirt[idx] = 0
        }

        const radius: number = fields.radius.value - 1
        const changes: { idx: number; prevDirt: number }[] = []
        applyInRadius(this.map, x, y, radius, (idx) => {
            const prevDirt = this.map.dirt[idx]
            if (fields.shouldAdd.value) {
                if (add(idx)) return
                changes.push({ idx, prevDirt })
            } else {
                remove(idx)
                changes.push({ idx, prevDirt })
            }
        })

        return () => {
            changes.forEach(({ idx, prevDirt }) => {
                this.map.dirt[idx] = prevDirt
                this.map.staticMap.initialDirt[idx] = prevDirt
            })
        }
    }
}

export class CatBrush extends SymmetricMapEditorBrush<StaticMap> {
    public clickBehavior = MapEditorBrushClickBehavior.NO_DESELECT
    private readonly bodies: Bodies
    public readonly name = 'Cat'
    public readonly fields = {
        isCat: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        catOrWaypointMode: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: 0,
            label: 'Mode',
            options: [
                { value: 0, label: 'Cat' },
                { value: 1, label: 'Waypoint' }
            ]
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public lastSelectedCat = -1

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const isCat: boolean = fields.isCat.value
        const selectedBodyID = GameRenderer.getSelectedRobot()
        let lastSelectedCatLoc: Vector | null = null

        if (selectedBodyID !== null && selectedBodyID !== undefined) {
            const body = this.bodies.bodies.get(selectedBodyID)
            if (body && body.robotType === schema.RobotType.CAT) {
                this.lastSelectedCat = selectedBodyID
                lastSelectedCatLoc = this.bodies.getById(this.lastSelectedCat)?.pos
            } else {
                this.lastSelectedCat = -1
                lastSelectedCatLoc = null
            }
        }
        
        // console.log(`last selected cat: ${this.lastSelectedCat}`)

        if (fields.catOrWaypointMode.value === 1) {
            // Waypoint mode
            if (this.lastSelectedCat === -1) return null
            let currentCat = this.lastSelectedCat
            if (!robotOne) {
                const symmetricPoint = this.map.applySymmetryCat(this.bodies.getById(this.lastSelectedCat)!.pos)
                currentCat = this.bodies.getBodyAtLocation(symmetricPoint.x, symmetricPoint.y)!.id
                lastSelectedCatLoc = this.map.applySymmetry(lastSelectedCatLoc!)
            }

            // if undoing a waypoint addition
            if (fields.isCat.value === false) {
                if (!this.map.catWaypoints.has(currentCat)) {
                    return null
                }
                this.map.catWaypoints.set(
                    currentCat,
                    this.map.catWaypoints.get(currentCat)!.filter((wp) => wp.x !== x || wp.y !== y)
                )
                return () => {
                    this.map.catWaypoints.set(currentCat, this.map.catWaypoints.get(currentCat)!.concat({ x, y }))
                }
            }

            // if adding a waypoint
            for (let nei of this.map.getNeighbors(x, y)) {
                if (this.map.wallAt(nei.x, nei.y) || !this.map.inBounds(nei.x, nei.y)) {
                    return null
                }
            }

            if (this.bodies.getBodyAtLocation(x, y) || this.map.wallAt(x, y)) {
                return null
            }

            if (!this.map.catWaypoints.has(currentCat)) {
                this.map.catWaypoints.set(currentCat, [lastSelectedCatLoc!])
            }
            this.map.catWaypoints.get(currentCat)?.push({ x, y })

            return () => {
                this.map.catWaypoints.set(
                    currentCat,
                    this.map.catWaypoints.get(currentCat)!.filter((wp) => wp.x !== x || wp.y !== y)
                )
            }
        }

        const add = (x: number, y: number, team: Team) => {
            const pos = { x, y }

            if (
                this.bodies.checkBodyCollisionAtLocation(schema.RobotType.CAT, pos)||
                !this.bodies.checkBodyOutofBoundsAtLocation(schema.RobotType.CAT, pos)
            ) {
                return null
            }

            const id = this.bodies.getNextID()
            this.bodies.spawnBodyFromValues(id, schema.RobotType.CAT, team, pos, 0, robotOne ? 0 : 1)

            return id
        }

        const remove = (x: number, y: number) => {
            const body = this.bodies.getBodyAtLocation(x, y)

            if (!body) return null

            const team = body.team
            const pos = body.pos
            this.bodies.removeBody(body.id)
            const waypoints = this.map.catWaypoints.get(body.id)
            this.map.catWaypoints.delete(body.id)

            return { team, waypoints, pos }
        }
        if (isCat) {
            // shouldnt matter which team we add to since cats are neutral
            const id = add(x, y, this.bodies.game.teams[0])
            if (id) return () => this.bodies.removeBody(id)
            return null
        } else {
            const removed = remove(x, y)
            if (!removed) return null

            const { team, waypoints, pos } = removed
            if (!team) return null

            return () => {
                add(pos.x, pos.y, team)
                if (waypoints) {
                    this.map.catWaypoints.set(this.bodies.getBodyAtLocation(x, y)!.id, waypoints)
                }
            }
        }
    }

    // Override default symmetric apply behavior because cats occupy a 2x2 footprint
    public apply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean): UndoFunction {
        const undoFunctions: UndoFunction[] = []

        const body = this.bodies.getBodyAtLocation(x, y)
        const anchor = body?.pos

        const undo0 = this.symmetricApply(x, y, fields, robotOne)

        // Return early if brush could not be applied
        if (!undo0) return () => {}

        undoFunctions.push(undo0)

        let symmetryPoint: { x: number; y: number }
        if (fields.catOrWaypointMode.value === 1) {
            symmetryPoint = this.map.applySymmetry({ x: x, y: y })
        } else {
            symmetryPoint = !anchor ? this.map.applySymmetryCat({ x: x, y: y }) : this.map.applySymmetryCat(anchor)
        }
        if (symmetryPoint.x != x || symmetryPoint.y != y) {
            const undo1 = this.symmetricApply(symmetryPoint.x, symmetryPoint.y, fields, !robotOne)

            // If the symmetry is not applied, revert the original change
            if (!undo1) {
                undo0()
                return () => {}
            }

            undoFunctions.push(undo1)
        }

        return () => undoFunctions.forEach((f) => f && f())
    }
}

export class RatKingBrush extends SymmetricMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Rat Kings'
    public readonly fields = {
        isRatKing: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        team: {
            type: MapEditorBrushFieldType.TEAM,
            value: 0
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const isRatKing: boolean = fields.isRatKing.value

        const add = (x: number, y: number, team: Team) => {
            const pos = { x, y }
            if (
                this.bodies.getBodyAtLocation(x, y) ||
                this.bodies.checkBodyCollisionAtLocation(schema.RobotType.RAT_KING, pos) ||
                !this.bodies.checkBodyOutofBoundsAtLocation(schema.RobotType.RAT_KING, pos)
            ) {
                return null
            }

            const id = this.bodies.getNextID()
            this.bodies.spawnBodyFromValues(id, schema.RobotType.RAT_KING, team, pos, 0, robotOne ? 0:1)

            return id
        }

        const remove = (x: number, y: number) => {
            const body = this.bodies.getBodyAtLocation(x, y)

            if (!body) return null

            const team = body.team
            this.bodies.removeBody(body.id)

            return team
        }

        if (isRatKing) {
            let teamIdx = robotOne ? 0 : 1
            if (fields.team.value === 1) teamIdx = 1 - teamIdx
            const team = this.bodies.game.teams[teamIdx]
            const id = add(x, y, team)
            if (id) return () => this.bodies.removeBody(id)
            return null
        } else {
            const team = remove(x, y)
            if (!team) return null
            return () => add(x, y, team)
        }
    }
}
