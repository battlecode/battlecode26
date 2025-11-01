import { GameRenderer } from './playback/GameRenderer'

/*
 * TODO: colors are defined in style.css as well
 */

export enum Colors {
    TEAM_ONE = 'TEAM_ONE',
    TEAM_TWO = 'TEAM_TWO',

    PAINT_TEAMONE_ONE = 'PAINT_TEAMONE_ONE',
    PAINT_TEAMONE_TWO = 'PAINT_TEAMONE_TWO',
    PAINT_TEAMTWO_ONE = 'PAINT_TEAMTWO_ONE',
    PAINT_TEAMTWO_TWO = 'PAINT_TEAMTWO_TWO',
    WALLS_COLOR = 'WALLS_COLOR',
    GAMEAREA_BACKGROUND = 'GAMEAREA_BACKGROUND',
    TILE_COLOR = 'TILE_COLOR',
    SIDEBAR_BACKGROUND = 'SIDEBAR_BACKGROUND',

    RED = 'RED',
    PINK = 'PINK',
    GREEN = 'GREEN',
    CYAN = 'CYAN',
    CYAN_DARK = 'CYAN_DARK',
    BLUE = 'BLUE',
    BLUE_LIGHT = 'BLUE_LIGHT',
    BLUE_DARK = 'BLUE_DARK',

    DARK = 'DARK',
    DARK_HIGHLIGHT = 'DARK_HIGHLIGHT',
    BLACK = 'BLACK',
    WHITE = 'WHITE',
    LIGHT = 'LIGHT',
    LIGHT_HIGHLIGHT = 'LIGHT_HIGHLIGHT',
    MED_HIGHLIGHT = 'MED_HIGHLIGHT',
    LIGHT_CARD = 'LIGHT_CARD',
}

export const DEFAULT_GLOBAL_COLORS = {
    [Colors.TEAM_ONE]: '#cdcdcc',
    [Colors.TEAM_TWO]: '#fee493',

    [Colors.PAINT_TEAMONE_ONE]: '#666666',
    [Colors.PAINT_TEAMONE_TWO]: '#565656',
    [Colors.PAINT_TEAMTWO_ONE]: '#b28b52',
    [Colors.PAINT_TEAMTWO_TWO]: '#997746',
    [Colors.WALLS_COLOR]: '#547f31',
    [Colors.TILE_COLOR]: '#4c301e',
    [Colors.GAMEAREA_BACKGROUND]: '#2e2323',
    [Colors.RED]: '#ff9194',
    [Colors.SIDEBAR_BACKGROUND]: '#3f3131',

    [Colors.PINK]: '#ffb4c1',
    [Colors.GREEN]: '#00a28e',
    [Colors.CYAN]: '#02a7b9',
    [Colors.CYAN_DARK]: '#1899a7',
    [Colors.BLUE]: '#04a2d9',
    [Colors.BLUE_LIGHT]: '#26abd9',
    [Colors.BLUE_DARK]: '#00679e',

    [Colors.DARK]: '#1f2937',
    [Colors.DARK_HIGHLIGHT]: '#140f0f',
    [Colors.BLACK]: '#140f0f',
    [Colors.WHITE]: '#fcdede',
    [Colors.LIGHT]: '#aaaaaa22',
    [Colors.LIGHT_HIGHLIGHT]: '#ffffff33',
    [Colors.MED_HIGHLIGHT]: '#d0d0d066',
    [Colors.LIGHT_CARD]: '#f7f7f722',
}

export const COLOR_CSS_VARIABLES = {
    [Colors.TEAM_ONE]: '--color-team0',
    [Colors.TEAM_TWO]: '--color-team1',

    [Colors.PAINT_TEAMONE_ONE]: '--color-paint-team0-0',
    [Colors.PAINT_TEAMONE_TWO]: '--color-paint-team0-1',
    [Colors.PAINT_TEAMTWO_ONE]: '--color-paint-team1-0',
    [Colors.PAINT_TEAMTWO_TWO]: '--color-paint-team1-1',
    [Colors.WALLS_COLOR]: '--color-walls',
    [Colors.TILE_COLOR]: '--color-tile',
    [Colors.GAMEAREA_BACKGROUND]: '--color-gamearea-background',
    [Colors.SIDEBAR_BACKGROUND]: '--color-sidebar-background',

    [Colors.RED]: '--color-red',
    [Colors.PINK]: '--color-pink',
    [Colors.GREEN]: '--color-green',
    [Colors.CYAN]: '--color-cyan',
    [Colors.CYAN_DARK]: '--color-cyan-dark',
    [Colors.BLUE]: '--color-blue',
    [Colors.BLUE_LIGHT]: '--color-blue-light',
    [Colors.BLUE_DARK]: '--color-blue-dark',

    [Colors.DARK]: '--color-dark',
    [Colors.DARK_HIGHLIGHT]: '--color-dark-highlight',
    [Colors.BLACK]: '--color-black',
    [Colors.WHITE]: '--color-white',
    [Colors.LIGHT]: '--color-light',
    [Colors.LIGHT_HIGHLIGHT]: '--color-light-highlight',
    [Colors.MED_HIGHLIGHT]: '--color-med-highlight',
    [Colors.LIGHT_CARD]: '--color-light-card',
}

export const currentColors: Record<Colors, string> = { ...DEFAULT_GLOBAL_COLORS }

export const updateGlobalColor = (color: Colors, value: string) => {
    currentColors[color] = value
    localStorage.setItem('config-colors' + color, JSON.stringify(currentColors[color]))
    GameRenderer.fullRender()
}

export const getGlobalColor = (color: Colors) => {
    return currentColors[color]
}

export const resetGlobalColors = () => {
    for (const key in currentColors) {
        const typedKey = key as Colors
        updateGlobalColor(typedKey, DEFAULT_GLOBAL_COLORS[typedKey])
    }
}

export const getPaintColors = () => {
    return [
        '#00000000',
        currentColors.PAINT_TEAMONE_ONE,
        currentColors.PAINT_TEAMONE_TWO,
        currentColors.PAINT_TEAMTWO_ONE,
        currentColors.PAINT_TEAMTWO_TWO
    ]
}

export const getTeamColors = () => {
    return [currentColors.TEAM_ONE, currentColors.TEAM_TWO]
}
