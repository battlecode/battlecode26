import { GameRenderer } from './playback/GameRenderer'
import { AppContext } from './app-context'

/*
 * TODO: colors are defined in style.css as well
 */

export interface ColorFormat {
    version: number
    colors: Record<string, string>
}

export interface ColorSection {
    displayName: string
}

export interface ColorPreset {
    displayName: string
    data: ColorFormat
}

export class Color {
    readonly name: string
    readonly defaultColor: string
    readonly cssVariable: string
    readonly displayName: string | undefined
    readonly section: ColorSection | undefined

    /**
     * Constructs a Color which may be referenced using `.get()` or using a CSS variable.
     * @param name The key that this Color is stored into `Colors` with.
     * @param defaultColor The default color in the form of #FFFFFF
     * @param cssVariable The CSS variable that this Color is linked to
     * @param displayName The label of this Color in the config menu. If undefined, will not appear.
     * @param section The ColorSection of this Color in the config menu. If undefined, will not appear.
     */
    constructor(name: string, defaultColor: string, cssVariable: string, displayName?: string, section?: ColorSection) {
        this.name = name
        this.defaultColor = defaultColor
        this.cssVariable = cssVariable
        this.displayName = displayName
        this.section = section
    }

    set(newColor: string, context: AppContext): void {
        currentColors[this.name] = newColor
        localStorage.setItem('config-colors' + this.name, JSON.stringify(newColor))
        context.setState((prevState) => ({
            ...prevState,
            config: { ...prevState.config, colors: { ...prevState.config.colors, [this.name]: newColor } }
        }))
        document.documentElement.style.setProperty(this.cssVariable, newColor)

        // hopefully after the setState is done
        setTimeout(() => GameRenderer.fullRender(), 10)
    }

    setCssVariable(): void {
        document.documentElement.style.setProperty(this.cssVariable, this.get())
    }

    get(): string {
        return currentColors[this.name]
    }
}

// ColorSections in here will appear in the Config in the same order.
export const Sections = {
    INTERFACE: { displayName: 'Interface' } as ColorSection,
    GENERAL: { displayName: 'General' } as ColorSection,
    CHEDDAR: { displayName: 'Cheddar' } as ColorSection,
    PLUM: { displayName: 'Plum' } as ColorSection
}

// Colors placed in here will appear in their section in the same order.
export const Colors = {
    GAMEAREA_BACKGROUND: new Color(
        'GAMEAREA_BACKGROUND',
        '#4f345a',
        '--color-gamearea-background',
        'Background',
        Sections.INTERFACE
    ),
    SIDEBAR_BACKGROUND: new Color(
        'SIDEBAR_BACKGROUND',
        '#40284b',
        '--color-sidebar-background',
        'Sidebar',
        Sections.INTERFACE
    ),
    RED: new Color('RED', '#ff9194', '--color-red', 'Red', Sections.INTERFACE),
    PINK: new Color('PINK', '#ffb4c1', '--color-pink', 'Pink', Sections.INTERFACE),
    GREEN: new Color('GREEN', '#00a28e', '--color-green', 'Green', Sections.INTERFACE),
    CYAN: new Color('CYAN', '#02a7b9', '--color-cyan', 'Cyan', Sections.INTERFACE),
    CYAN_DARK: new Color('CYAN_DARK', '#1899a7', '--color-cyan-dark', 'Dark Cyan', Sections.INTERFACE),
    BLUE: new Color('BLUE', '#04a2d9', '--color-blue', 'Blue', Sections.INTERFACE),
    BLUE_LIGHT: new Color('BLUE_LIGHT', '#26abd9', '--color-blue-light', 'Light Blue', Sections.INTERFACE),
    BLUE_DARK: new Color('BLUE_DARK', '#00679e', '--color-blue-dark', 'Dark Blue', Sections.INTERFACE),
    DARK: new Color('DARK', '#1f2937', '--color-dark', 'Dark', Sections.INTERFACE),
    DARK_HIGHLIGHT: new Color(
        'DARK_HIGHLIGHT',
        '#140f0f',
        '--color-dark-highlight',
        'Dark Highlight',
        Sections.INTERFACE
    ),
    BLACK: new Color('BLACK', '#140f0f', '--color-black', 'Black', Sections.INTERFACE),
    WHITE: new Color('WHITE', '#fcdede', '--color-white', 'White', Sections.INTERFACE),
    LIGHT: new Color('LIGHT', '#aaaaaa22', '--color-light', 'Light', Sections.INTERFACE),
    LIGHT_HIGHLIGHT: new Color(
        'LIGHT_HIGHLIGHT',
        '#ffffff33',
        '--color-light-highlight',
        'Light Highlight',
        Sections.INTERFACE
    ),
    LIGHT_CARD: new Color('LIGHT_CARD', '#f7f7f722', '--color-light-card', 'Light Card', Sections.INTERFACE),

    WALLS_COLOR: new Color('WALLS_COLOR', '#52485a', '--color-walls', 'Walls', Sections.GENERAL),
    DIRT_COLOR: new Color('DIRT_COLOR', '#3b2931', '--color-dirt', 'Dirt', Sections.GENERAL),
    TILES_COLOR: new Color('TILES_COLOR', '#221725', '--color-tile', 'Tiles', Sections.GENERAL),

    TEAM_ONE: new Color('TEAM_ONE', '#fcc00d', '--color-team0', 'Text', Sections.CHEDDAR),

    TEAM_TWO: new Color('TEAM_TWO', '#c91c7e', '--color-team1', 'Text', Sections.PLUM)
}

export const Presets: ColorPreset[] = [
    {
        displayName: 'Battlecode 2026',
        data: {
            version: 0,
            colors: {
                GAMEAREA_BACKGROUND: '#4f345a',
                SIDEBAR_BACKGROUND: '#40284b',
                RED: '#ff9194',
                PINK: '#ffb4c1',
                GREEN: '#00a28e',
                CYAN: '#02a7b9',
                CYAN_DARK: '#1899a7',
                BLUE: '#04a2d9',
                BLUE_LIGHT: '#26abd9',
                BLUE_DARK: '#00679e',
                DARK: '#1f2937',
                DARK_HIGHLIGHT: '#140f0f',
                BLACK: '#140f0f',
                WHITE: '#fcdede',
                LIGHT: '#aaaaaa22',
                LIGHT_HIGHLIGHT: '#ffffff33',
                LIGHT_CARD: '#f7f7f722',
                WALLS_COLOR: '#52485A',
                TILES_COLOR: '#221725',
                TEAM_ONE: '#fcc00d',
                TEAM_TWO: '#c91c7e'
            }
        }
    },
    {
        displayName: 'Battlecode 2025',
        data: {
            version: 0,
            colors: {
                GAMEAREA_BACKGROUND: '#2e2323',
                SIDEBAR_BACKGROUND: '#3f3131',
                RED: '#ff9194',
                PINK: '#ffb4c1',
                GREEN: '#00a28e',
                CYAN: '#02a7b9',
                CYAN_DARK: '#1899a7',
                BLUE: '#04a2d9',
                BLUE_LIGHT: '#26abd9',
                BLUE_DARK: '#00679e',
                DARK: '#1f2937',
                DARK_HIGHLIGHT: '#140f0f',
                BLACK: '#140f0f',
                WHITE: '#fcdede',
                LIGHT: '#aaaaaa22',
                LIGHT_HIGHLIGHT: '#ffffff33',
                LIGHT_CARD: '#f7f7f722',
                WALLS_COLOR: '#547f31',
                TILES_COLOR: '#4c301e',
                TEAM_ONE: '#cdcdcc',
                TEAM_TWO: '#fee493'
            }
        }
    }
]

const currentColors: Record<string, string> = {}

for (const key in Colors) {
    const value = localStorage.getItem('config-colors' + key)
    currentColors[(Colors as any)[key].name] = value === null ? (Colors as any)[key].defaultColor : JSON.parse(value)
    ;(Colors as any)[key].setCssVariable()
}

export const getPaintColors = () => {
    return [
        '#00000000'
        // currentColors.PAINT_TEAMONE_ONE,
        // currentColors.PAINT_TEAMONE_TWO,
        // currentColors.PAINT_TEAMTWO_ONE,
        // currentColors.PAINT_TEAMTWO_TWO
    ]
}

export const getTeamColors = () => {
    return [currentColors.TEAM_ONE, currentColors.TEAM_TWO]
}
