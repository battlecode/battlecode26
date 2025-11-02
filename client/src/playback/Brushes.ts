import { schema } from 'battlecode-schema'
import {
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

const checkValidRuinPlacement = (check: Vector, map: StaticMap, bodies: Bodies) => {
    // Check if ruin is too close to the border
    if (check.x <= 1 || check.x >= map.width - 2 || check.y <= 1 || check.y >= map.height - 2) {
        return false
    }

    // Check if this is a valid ruin location
    const idx = map.locationToIndex(check.x, check.y)
    const ruin = map.ruins.findIndex((l) => squareIntersects(l, check, 4))
    const wall = map.walls.findIndex((v, i) => !!v && squareIntersects(map.indexToLocation(i), check, 2))
    const paint = map.initialPaint[idx]

    let tower = undefined
    for (const b of bodies.bodies.values()) {
        if (squareIntersects(check, b.pos, 4)) {
            tower = b
            break
        }
    }

    if (tower || ruin !== -1 || wall !== -1 || paint) {
        return false
    }

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
        case schema.Action.PaintAction:
            return { loc: () => loc, isSecondary: () => 0 }
        case schema.Action.UnpaintAction:
            return { loc: () => loc }
        case schema.Action.SplashAction:
            return { loc: () => loc }
        case schema.Action.AttackAction:
            return { id: () => targetId }
        case schema.Action.TransferAction:
            return { id: () => targetId, amount: () => 1 }
        case schema.Action.MopAction:
            return { id0: () => targetId, id1: () => 0, id2: () => 0 }
        case schema.Action.BuildAction:
            return { id: () => targetId, loc: () => loc }
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
            value: schema.RobotType.SOLDIER,
            label: 'Robot Type',
            options: [
                { value: schema.RobotType.SOLDIER, label: 'Soldier' },
                { value: schema.RobotType.SPLASHER, label: 'Splasher' },
                { value: schema.RobotType.MOPPER, label: 'Mopper' }
            ]
        },
        actionType: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: 0,
            label: 'Action Type',
            options: [
                { value: null, label: 'None' },
                { value: schema.Action.TransferAction, label: 'Transfer' },
                { value: schema.Action.AttackAction, label: 'Attack' },
                { value: schema.Action.PaintAction, label: 'Paint' },
                { value: schema.Action.UnpaintAction, label: 'Unpaint' },
                { value: schema.Action.SplashAction, label: 'Splash' },
                { value: schema.Action.MopAction, label: 'Mop' },
                { value: schema.Action.BuildAction, label: 'Build' }
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
            const ruin = this.map.ruins.findIndex((l) => squareIntersects(l, pos, 2))
            const paint = this.map.initialPaint[idx]

            let tower = undefined
            for (const b of this.bodies.bodies.values()) {
                if (squareIntersects(pos, b.pos, 2)) {
                    tower = b
                    break
                }
            }

            if (tower || ruin !== -1 || paint) return true

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

export class RuinsBrush extends SymmetricMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Ruins'
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
            if (!checkValidRuinPlacement({ x, y }, this.map, this.bodies)) {
                return true
            }

            this.map.ruins.push({ x, y })
        }

        const remove = (x: number, y: number) => {
            const foundIdx = this.map.ruins.findIndex((l) => l.x === x && l.y === y)
            if (foundIdx === -1) return true
            this.map.ruins.splice(foundIdx, 1)
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

export class PaintBrush extends SymmetricMapEditorBrush<CurrentMap> {
    private readonly bodies: Bodies
    public readonly name = 'Paint'
    public readonly fields = {
        shouldAdd: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        team: {
            type: MapEditorBrushFieldType.TEAM,
            value: 0
        },
        radius: {
            type: MapEditorBrushFieldType.POSITIVE_INTEGER,
            value: 1,
            label: 'Radius'
        },
        paintType: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: 0,
            label: 'Paint Type',
            options: [
                { value: 0, label: 'Primary' },
                { value: 1, label: 'Secondary' }
            ]
        }
    }

    constructor(round: Round) {
        super(round.map)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const add = (idx: number, value: number) => {
            // Check if this is a valid paint location
            const pos = this.map.indexToLocation(idx)
            const ruin = this.map.staticMap.ruins.find((r) => r.x === pos.x && r.y === pos.y)
            const wall = this.map.staticMap.walls[idx]
            const body = this.bodies.getBodyAtLocation(pos.x, pos.y)
            if (body || ruin || wall) return true
            this.map.paint[idx] = value
            this.map.staticMap.initialPaint[idx] = this.map.paint[idx]
        }

        const remove = (idx: number) => {
            this.map.paint[idx] = 0
            this.map.staticMap.initialPaint[idx] = 0
        }

        const radius: number = fields.radius.value - 1
        const changes: { idx: number; prevPaint: number }[] = []
        applyInRadius(this.map, x, y, radius, (idx) => {
            const prevPaint = this.map.paint[idx]
            if (fields.shouldAdd.value) {
                let teamIdx = robotOne ? 0 : 1
                if (fields.team.value === 1) teamIdx = 1 - teamIdx
                const newVal = teamIdx * 2 + 1 + fields.paintType.value
                if (add(idx, newVal)) return
                changes.push({ idx, prevPaint })
            } else {
                remove(idx)
                changes.push({ idx, prevPaint })
            }
        })

        return () => {
            changes.forEach(({ idx, prevPaint }) => {
                this.map.paint[idx] = prevPaint
                this.map.staticMap.initialPaint[idx] = prevPaint
            })
        }
    }
}

export class TowerBrush extends SymmetricMapEditorBrush<StaticMap> {
    private readonly bodies: Bodies
    public readonly name = 'Towers'
    public readonly fields = {
        isTower: {
            type: MapEditorBrushFieldType.ADD_REMOVE,
            value: true
        },
        team: {
            type: MapEditorBrushFieldType.TEAM,
            value: 0
        },
        towerType: {
            type: MapEditorBrushFieldType.SINGLE_SELECT,
            value: schema.RobotType.PAINT_TOWER,
            label: 'Tower Type',
            options: [
                { value: schema.RobotType.PAINT_TOWER, label: 'Paint Tower' },
                { value: schema.RobotType.MONEY_TOWER, label: 'Money Tower' }
            ]
        }
    }

    constructor(round: Round) {
        super(round.map.staticMap)
        this.bodies = round.bodies
    }

    public symmetricApply(x: number, y: number, fields: Record<string, MapEditorBrushField>, robotOne: boolean) {
        const towerType: schema.RobotType = fields.towerType.value
        const isTower: boolean = fields.isTower.value

        const add = (x: number, y: number, team: Team) => {
            const pos = { x, y }
            if (!checkValidRuinPlacement(pos, this.map, this.bodies)) {
                return null
            }

            const id = this.bodies.getNextID()
            this.bodies.spawnBodyFromValues(id, towerType, team, pos)

            return id
        }

        const remove = (x: number, y: number) => {
            const body = this.bodies.getBodyAtLocation(x, y)

            if (!body) return null

            const team = body.team
            this.bodies.removeBody(body.id)

            return team
        }

        if (isTower) {
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
