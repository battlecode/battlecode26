import { schema } from 'battlecode-schema'
import {
    MapEditorBrushClickBehavior,
    MapEditorBrushField,
    MapEditorBrushFieldType,
    SinglePointMapEditorBrush,
    SymmetricMapEditorBrush
} from '../components/sidebar/map-editor/MapEditorBrush'
import { ACTION_DEFINITIONS } from './Actions'
import Bodies from './Bodies'
import { CurrentMap, StaticMap } from './Map'
import { Vector } from './Vector'
import { Team } from './Game'
import Round from './Round'
import { GameRenderer } from './GameRenderer'
import { once } from 'process'
import { assert } from 'console'
import { current } from 'tailwindcss/colors'

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
const makeEditorActionData = (map: StaticMap, atype: schema.Action, tx: number, ty: number, targetId?: number) => {
    const mapWidth = map.width
    const mapHeight = map.height

    let targetX = tx
    let targetY = ty
    let validLocFound = false
    // find the first offset that yields a valid square inside the map
    while (!validLocFound) {
        const nx = tx + Math.random() * 3 - 1
        const ny = ty + Math.random() * 3 - 1
        if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
            targetX = nx
            targetY = ny
            break
        }
    }

    const loc = map.locationToIndex(targetX, targetY)

    // Update these action data based on action type; change this when the game changes
    switch (atype) {
        case schema.Action.BreakDirt:
            return { loc: () => loc }
        case schema.Action.CatFeed:
            return { loc: () => loc }
        case schema.Action.CatPounce:
            return { loc: () => loc }
        case schema.Action.CatScratch:
            return { loc: () => loc }
        case schema.Action.CheesePickup:
            return { id: () => targetId, amount: () => 1 }
        case schema.Action.CheeseSpawn:
            return { id0: () => targetId, id1: () => 0, id2: () => 0 }
        case schema.Action.DamageAction:
            return { id: () => targetId, loc: () => loc }
        case schema.Action.DieAction:
            return { id: () => targetId }
        case schema.Action.PlaceTrap:
            return { loc: () => loc }
        case schema.Action.PlaceDirt:
            return { loc: () => loc }
        case schema.Action.RatAttack:
            return { id: () => targetId }
        case schema.Action.RatCollision:
            return { loc: () => loc }
        case schema.Action.RatNap:
            return { id: () => targetId }
        case schema.Action.SpawnAction:
            return { loc: () => loc, id: () => targetId }
        case schema.Action.TriggerTrap:
            return { id: () => targetId }
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
                { value: schema.Action.CatScratch, label: 'Cat Scratch' },
                { value: schema.Action.CheesePickup, label: 'Cheese Pickup' },
                { value: schema.Action.CheeseSpawn, label: 'Cheese Spawn' },
                { value: schema.Action.DamageAction, label: 'Damage' },
                { value: schema.Action.DieAction, label: 'Die' },
                { value: schema.Action.PlaceDirt, label: 'Place Dirt' },
                { value: schema.Action.PlaceTrap, label: 'Place Trap' },
                { value: schema.Action.RatAttack, label: 'Rat Attack' },
                { value: schema.Action.RatCollision, label: 'Rat Collision' },
                { value: schema.Action.RatNap, label: 'Rat Nap' },
                { value: schema.Action.SpawnAction, label: 'Spawn Action' },
                { value: schema.Action.TriggerTrap, label: 'Trigger Trap' }
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
            if(this.bodies.checkBodyCollisionAtLocation(robotType, pos)) return null;
            
            this.bodies.spawnBodyFromValues(id, robotType, team, pos)

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
                    const adata = makeEditorActionData(this.map, actionType, x, y, targetIdToUse)
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

        if (selectedBodyID !== null && selectedBodyID !== undefined) {
            const body = this.bodies.bodies.get(selectedBodyID)
            if (body && body.robotType === schema.RobotType.CAT) {
                this.lastSelectedCat = selectedBodyID
            }
        }

        if (fields.catOrWaypointMode.value === 1) {
            // Waypoint mode
            if (this.lastSelectedCat === -1) return null
            let currentCat = this.lastSelectedCat
            if (!robotOne) {
                const symmetricPoint = this.map.applySymmetry(this.bodies.getById(this.lastSelectedCat)!.pos)
                currentCat = this.bodies.getBodyAtLocation(symmetricPoint.x, symmetricPoint.y)!.id
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
            if (this.bodies.getBodyAtLocation(x, y)) {
                return null
            }
            if (!this.map.catWaypoints.has(currentCat)) {
                this.map.catWaypoints.set(currentCat, [])
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
            
            if(this.bodies.checkBodyCollisionAtLocation(schema.RobotType.CAT, pos)) return null;

            const id = this.bodies.getNextID()
            this.bodies.spawnBodyFromValues(id, schema.RobotType.CAT, team, pos)

            return id
        }

        const remove = (x: number, y: number) => {
            const body = this.bodies.getBodyAtLocation(x, y)

            if (!body) return null

            const team = body.team
            this.bodies.removeBody(body.id)
            const waypoints = this.map.catWaypoints.get(body.id)
            this.map.catWaypoints.delete(body.id)

            return { team, waypoints }
        }

        if (isCat) {
            // shouldnt matter which team we add to since cats are neutral
            const id = add(x, y, this.bodies.game.teams[0])
            if (id) return () => this.bodies.removeBody(id)
            return null
        } else {
            const { team, waypoints } = remove(x, y)!
            if (!team) return null
            return () => {
                add(x, y, team)
                if (waypoints) {
                    this.map.catWaypoints.set(this.bodies.getBodyAtLocation(x, y)!.id, waypoints)
                }
            }
        }
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
            if (this.bodies.getBodyAtLocation(x, y) || this.bodies.checkBodyCollisionAtLocation(schema.RobotType.RAT_KING, pos)) {
                return null
            }

            const id = this.bodies.getNextID()
            this.bodies.spawnBodyFromValues(id, schema.RobotType.RAT_KING, team, pos)

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
