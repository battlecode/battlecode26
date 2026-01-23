export const CLIENT_VERSION = '1.0.2'
export const SPEC_VERSION = '1'
export const BATTLECODE_YEAR: number = 2026
export const MAP_SIZE_RANGE = {
    min: 20,
    max: 60
}
export const GAME_MAX_TURNS = 2000
/*
 * General constants
 */
export const DIRECTIONS: Record<number, Array<number>> = {
    0: [0, 0],
    1: [-1, 0],
    2: [-1, -1],
    3: [0, -1],
    4: [1, -1],
    5: [1, 0],
    6: [1, 1],
    7: [0, 1],
    8: [-1, 1]
}

export const ENGINE_BUILTIN_MAP_NAMES: string[] = [
    // Default
    'DefaultSmall',
    'DefaultMedium',
    'DefaultLarge',
    // Sprint 1
    'sittingducks',
    'starvation',
    'thunderdome',
    'popthecork',
    'cheesefarm',
    'rift',
    'ZeroDay',
    'pipes',
    'Nofreecheese',
    'wallsofparadis',
    'dirtpassageway',
    'trapped',
    'arrows',
    'Meow',
    'dirtfulcat',
    'keepout',
    // Sprint 2
    '5t4rv4t10n_1337',
    'cheesebottles',
    'hatefullattice',
    'knifefight',
    'mercifullattice',
    'peaceinourtime',
    'jail',
    'safelycontained',
    'minimaze',
    'averyfineline',
    'averystrangespace',
    'canyoudig',
    'streetsofnewyork',
    'TheHeist',
    'closeup',
    'corridorofdoomanddespair',
    'EscapeTheNight',
    'tiny',
    'toomuchcheese',
    'uneruesansfin',
    'whatsthecatdoin',
    'whereisthecheese'
    // HS
    // Quals
]

export const TEAM_COLOR_NAMES = ['Cheddar', 'Plum']

export const INDICATOR_DOT_SIZE = 0.2
export const INDICATOR_LINE_WIDTH = 0.1

/*
 * Renderer constants
 */
export const TILE_RESOLUTION: number = 50 // Pixels per axis per tile
export const TOOLTIP_PATH_LENGTH = 8
export const TOOLTIP_PATH_INIT_R = 0.2
export const TOOLTIP_PATH_DECAY_R = 0.9
export const TOOLTIP_PATH_DECAY_OPACITY = 0.95
