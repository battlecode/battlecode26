import { schema, flatbuffers } from 'battlecode-schema'
import Game, { Team } from '../../../playback/Game'
import Match from '../../../playback/Match'
import { CurrentMap, StaticMap } from '../../../playback/Map'
import Round from '../../../playback/Round'
import Bodies, { Body } from '../../../playback/Bodies'
import { BATTLECODE_YEAR, DIRECTIONS, TEAM_COLOR_NAMES } from '../../../constants'
import { nativeAPI } from '../runner/native-api-wrapper'
import { Vector } from '../../../playback/Vector'
import { RobotType } from 'battlecode-schema/js/battlecode/schema'

export function loadFileAsMap(file: File): Promise<Game> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.readAsArrayBuffer(file)
        reader.onload = () => {
            const data = new Uint8Array(reader.result as ArrayBuffer)
            const schemaMap = schema.GameMap.getRootAsGameMap(new flatbuffers.ByteBuffer(data))
            const game = new Game()
            game.currentMatch = Match.fromMap(schemaMap, game)
            resolve(game)
        }
    })
}

export function exportMap(round: Round, name: string) {
    /*
    Array.from(round.bodies.bodies.values())
        .filter(
            (body) =>
                body.robotType === RobotType.RAT ||
                body.robotType === RobotType.RAT_KING ||
                body.robotType === RobotType.CAT
        )
        .forEach((body) => round.bodies.removeBody(body.id))
    */
    const mapError = verifyMap(round.map, round.bodies)
    if (mapError) return mapError

    round.map.staticMap.name = name

    const data = mapToFile(round.map, round.bodies)
    exportFile(data, name + `.map${BATTLECODE_YEAR % 100}`)

    return ''
}

const squareIntersects = (check: Vector, center: Vector, radius: number) => {
    return (
        check.x >= center.x - radius &&
        check.x <= center.x + radius &&
        check.y >= center.y - radius &&
        check.y <= center.y + radius
    )
}

/**
 * Check that the map is valid and conforms with guarantees.
 * Returns a non-empty string with an error if applicable
 */
function verifyMap(map: CurrentMap, bodies: Bodies): string {
    if (map.isEmpty() && bodies.isEmpty()) {
        return 'Map is empty'
    }

    // verify there are at least 2 cats
    const numCats = Array.from(bodies.bodies.values()).filter((body) => body.robotType === RobotType.CAT).length
    if (numCats < 2) {
        return 'Map must have at least 2 cats placed'
    }

    // verify there is exactly one rat-king per team initially
    const ratKings = Array.from(bodies.bodies.values()).filter((body) => body.robotType === RobotType.RAT_KING)
    const ratKingTeams = new Set<Team>()
    for (const ratK of ratKings) {
        ratKingTeams.add(ratK.team)
    }
    if (ratKings.length !== 2 || ratKingTeams.size !== 2) {
        return `Each team must have exactly 1 Rat King placed`
    }

    // Validate map elements
    let numWalls = 0
    let numDirt = 0
    const mapSize = map.width * map.height
    for (let i = 0; i < mapSize; i++) {
        const pos = map.indexToLocation(i)
        const wall = map.staticMap.walls[i]
        const cheeseMine = map.staticMap.cheeseMines.find((l) => l.x === pos.x && l.y === pos.y)
        const body = bodies.getBodyAtLocation(pos.x, pos.y)

        if (cheeseMine && wall) {
            return `Cheese mine and wall overlap at (${pos.x}, ${pos.y})`
        }

        if (wall && body) {
            return `Robot at (${pos.x}, ${pos.y}) is on top of a wall`
        }

        if (cheeseMine) {
            // Check distance to nearby cheese mines
            for (const checkCheeseMine of map.staticMap.cheeseMines) {
                if (checkCheeseMine === cheeseMine) continue

                if (squareIntersects(checkCheeseMine, pos, 4)) {
                    return (
                        `Cheese mine at (${pos.x}, ${pos.y}) is too close to cheese mine ` +
                        `at (${checkCheeseMine.x}, ${checkCheeseMine.y}), must be ` +
                        `>= 5 away`
                    )
                }
            }
        }

        if (wall) {
            // Check distance to nearby cheese mines

            for (const checkCheeseMine of map.staticMap.cheeseMines) {
                if (squareIntersects(checkCheeseMine, pos, 2)) {
                    return (
                        `Wall at (${pos.x}, ${pos.y}) is too close to cheese mine ` +
                        `at (${checkCheeseMine.x}, ${checkCheeseMine.y}), must be ` +
                        `>= 3 away`
                    )
                }
            }
        }

        numWalls += wall
        numDirt += map.dirt[i] > 0 ? 1 : 0
    }

    // Validate wall percentage
    const maxWallPercent = 20
    if (numWalls * 100 >= mapSize * maxWallPercent) {
        const displayPercent = (numWalls / mapSize) * 100
        return `Walls must take up at most ${maxWallPercent}% of the map, currently is ${displayPercent.toFixed(1)}%`
    }

    // Validate dirt percentage
    const maxDirtPercent = 50
    if (numDirt * 100 >= mapSize * maxDirtPercent) {
        const displayPercent = (numDirt / mapSize) * 100
        return `Dirt must take up at most ${maxDirtPercent}% of the map, currently is ${displayPercent.toFixed(1)}%`
    }

    // Validate initial bodies
    for (const body of bodies.bodies.values()) {
        // Check distance from cat to other cats and cheese mines
        let body_type = undefined
        switch (body.robotType) {
            case RobotType.CAT:
                body_type = 'Cat'
                break
            case RobotType.RAT:
                body_type = 'Rat'
                break
            case RobotType.RAT_KING:
                body_type = 'Rat King'
                break
            default:
                body_type = 'Unknown'
        }

        for (const checkCheeseMine of map.staticMap.cheeseMines) {
            if (squareIntersects(checkCheeseMine, body.pos, 2)) {
                return (
                    `Cheese mine at (${checkCheeseMine.x}, ${checkCheeseMine.y}) is too close to ${body_type} ` +
                    `at (${body.pos.x}, ${body.pos.y}), must be ` +
                    `>= 3 away`
                )
            }
        }

        for (const checkBody of bodies.bodies.values()) {
            if (checkBody === body) continue
            if (squareIntersects(checkBody.pos, body.pos, 0)) {
                return (
                    `${body_type} at (${body.pos.x}, ${body.pos.y}) is too close to cat ` +
                    `at (${checkBody.pos.x}, ${checkBody.pos.y}), must be ` +
                    `>= 1 away`
                )
            }
        }

        const wall = map.staticMap.walls.findIndex(
            (v, i) => !!v && squareIntersects(map.indexToLocation(i), body.pos, 2)
        )
        if (wall !== -1) {
            const pos = map.indexToLocation(wall)
            return (
                `${body_type} at (${body.pos.x}, ${body.pos.y}) is too close to wall ` +
                `at (${pos.x}, ${pos.y}), must be ` +
                `>= 3 away`
            )
        }

        if (body.robotType === schema.RobotType.CAT) {
            if (!map.staticMap.catWaypoints.get(body.id) || map.staticMap.catWaypoints.get(body.id)?.length === 0) {
                return `Cat with id ${body.id} has no waypoints`
            }
        }

    }

    return ''
}

/**
 * The order in which the data is written is important. When we change the schema, this may need to be refactored
 * Only one table or object can be created at once, so we have to create the bodies table first, then start the actual map object
 */
function mapToFile(currentMap: CurrentMap, initialBodies: Bodies): Uint8Array {
    const builder = new flatbuffers.Builder()
    const name = builder.createString(currentMap.staticMap.name)
    const initialBodiesTable = initialBodies.toInitialBodyTable(builder)
    const mapPacket = currentMap.getSchemaPacket(builder)

    schema.GameMap.startGameMap(builder)
    schema.GameMap.addName(builder, name)
    schema.GameMap.addSize(builder, schema.Vec.createVec(builder, currentMap.width, currentMap.height))
    schema.GameMap.addSymmetry(builder, currentMap.staticMap.symmetry)
    schema.GameMap.addInitialBodies(builder, initialBodiesTable)
    schema.GameMap.addRandomSeed(builder, Math.round(Math.random() * 1000))
    currentMap.insertSchemaPacket(builder, mapPacket)

    builder.finish(schema.GameMap.endGameMap(builder))
    return builder.asUint8Array()
}

async function exportFile(data: Uint8Array, fileName: string) {
    if (nativeAPI) {
        nativeAPI.exportMap(Array.from(data), fileName)
    } else {
        const mimeType = 'application/octet-stream'
        const blob = new Blob([data], { type: mimeType })
        const url = window.URL.createObjectURL(blob)

        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        document.body.appendChild(link)
        link.style.display = 'none'
        link.click()
        link.remove()

        setTimeout(function () {
            return window.URL.revokeObjectURL(url)
        }, 30000)
    }
}
