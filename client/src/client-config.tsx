import React, { useEffect, useState, MouseEvent, PropsWithChildren, useRef } from 'react'
import { IconContext } from 'react-icons'
import { IoCloseCircle, IoCloseCircleOutline } from 'react-icons/io5'
import { ChromePicker, ColorResult } from 'react-color'
import { AppContextProvider, useAppContext, AppContext } from './app-context'
import { GameRenderer } from './playback/GameRenderer'
import { NativeAPI, nativeAPI } from './components/sidebar/runner/native-api-wrapper'
import { NumInput, TextInput, Select } from './components/forms'
import { Colors, Color, Sections, ColorFormat, ColorPreset, Presets } from './colors'
import { BrightButton, Button } from './components/button'
import { useKeyboard } from './util/keyboard'
import { SectionHeader } from './components/section-header'

export type ClientConfig = typeof DEFAULT_CONFIG

interface Props {
    open: boolean
}

function getDefaultColors(): Record<string, string> {
    const output: Record<string, string> = {}
    for (const key in Colors) {
        output[Colors[key].name] = Colors[key].defaultColor
    }
    return output
}

const DEFAULT_CONFIG = {
    showAllIndicators: false,
    showTeamOneIndicators: false,
    showTeamTwoIndicators: false,
    showAllRobotRadii: false,
    showTimelineMarkers: true,
    showHealthBars: true,
    showPaintBars: true,
    showPaintMarkers: true,
    showSRPOutlines: true,
    showSRPText: false,
    showExceededBytecode: false,
    showMapXY: true,
    focusRobotTurn: true,
    enableFancyPaint: true,
    streamRunnerGames: true,
    populateRunnerGames: true,
    profileGames: false,
    validateMaps: false,
    resolutionScale: 100,
    indicatorOpacity: 100,
    colors: getDefaultColors(),
}

const configDescription: Record<keyof ClientConfig, string> = {
    showAllIndicators: 'Show all indicator dots and lines',
    showTeamOneIndicators: "Show all indicator dots and lines just for Silver team (team 1)",
    showTeamTwoIndicators: "Show all indicator dots and lines just for Gold team (team 2)",
    showAllRobotRadii: 'Show all robot view and attack radii',
    showTimelineMarkers: 'Show user-generated markers on the timeline',
    showHealthBars: 'Show health bars below all robots',
    showPaintBars: 'Show paint bars below all robots',
    showPaintMarkers: 'Show paint markers created using mark()',
    showSRPOutlines: 'Show outlines around active SRPs',
    showSRPText: 'Show remaining rounds in the center of inactive SRPs',
    showExceededBytecode: 'Show a red highlight over bots that exceeded their bytecode limit',
    showMapXY: 'Show X,Y when hovering a tile',
    focusRobotTurn: 'Focus the robot when performing their turn during turn-stepping mode',
    enableFancyPaint: 'Enable fancy paint rendering',
    streamRunnerGames: 'Stream each round from the runner live as the game is being played',
    populateRunnerGames: 'Display the finished game immediately when the runner is finished running',
    profileGames: 'Enable saving profiling data when running games',
    validateMaps: 'Validate maps before running a game',
    resolutionScale: 'Resolution scale for the game area. Decrease to help performance.',
    indicatorOpacity: "Customize how opaque the indicator dots and lines are",
    colors: '',
}

const COLOR_FORMAT_VERSION = 0

function saveColorFile(): ColorFormat {
    const colors: Record<string, string> = {}
    for (const key in Colors) {
        const color = Colors[key].get()
        if (color != Colors[key].defaultColor) {
            colors[key] = color
        }
    }
    return {
        version: COLOR_FORMAT_VERSION,
        colors: colors,
    }
}

// Returns an error message or undefined if successful.
function loadColorFile(file: ColorFormat, context: AppContext): string | undefined {
    if (file.version != COLOR_FORMAT_VERSION) {
        return `Unsupported version ${file.version}`
    }
    const ColorRegexp = new RegExp(/(?:^#[\da-fA-F]{6}$)|(?:^#[\da-fA-F]{8}$)/)
    // verify that format is correct before continuing.
    for (const key in file.colors) {
        // The key does not correspond to a color.
        if (Colors[key] === undefined) {
            return `Unknown color name "${key}"`
        }
        // Color is not in the correct format
        if (!ColorRegexp.test(file.colors[key])) {
            return `Invalid color "${file.colors[key]}"`
        }
    }
    for (const key in file.colors) {
        Colors[key].set(file.colors[key], context)
    }
    return undefined
}

const configCategories: Record<keyof ClientConfig, string> = {
    // Game Visualization
    showAllIndicators: 'Game Visualization',
    showAllRobotRadii: 'Game Visualization',
    showSRPOutlines: 'Game Visualization',
    showSRPText: 'Game Visualization',
    showMapXY: 'Game Visualization',
    enableFancyPaint: 'Game Visualization',

    // Robot Display & Status
    showHealthBars: 'Robot Display & Status',
    showPaintBars: 'Robot Display & Status',
    showExceededBytecode: 'Robot Display & Status',
    focusRobotTurn: 'Robot Display & Status',

    // Markers & Paint Debugging
    showTimelineMarkers: 'Markers & Paint Debugging',
    showPaintMarkers: 'Markers & Paint Debugging',

    // Game Playback
    streamRunnerGames: 'Game Playback',
    populateRunnerGames: 'Game Playback',

    // Developer & Validation Tools
    profileGames: 'Developer Tools',
    validateMaps: 'Developer Tools',

    // Mischellanous
    resolutionScale: '',
    colors: ''
}

export function getDefaultConfig(): ClientConfig {
    const config: ClientConfig = { ...DEFAULT_CONFIG }
    for (const key in config) {
        const value = localStorage.getItem('config' + key)
        if (value) {
            ;(config[key as keyof ClientConfig] as any) = JSON.parse(value)
        }
    }

    for (const key in Colors) {
        const value = localStorage.getItem('config-colors' + key)
        if (value) {
            config.colors[key] = value
        }
    }

    return config
}

export const ConfigPage: React.FC<Props> = (props) => {
    const context = useAppContext()
    const keyboard = useKeyboard()

    const [input, setInput] = useState('')
    const [isSearchFocused, setIsSearchFocused] = useState(false)
    const [shouldForceOpen, setShouldForceOpen] = useState(false)

    const sidebarColor = context.state.config.colors[Colors.SIDEBAR_BACKGROUND]

    useEffect(() => {
        if (context.state.disableHotkeys || isSearchFocused) return

        if (keyboard.keyCode === 'KeyF')
            context.updateConfigValue('focusRobotTurn', !context.state.config.focusRobotTurn)
        if (keyboard.keyCode === 'KeyI')
            context.updateConfigValue('showAllIndicators', !context.state.config.showAllIndicators)
    }, [keyboard.keyCode])

    if (!props.open) return null

    const configEntries = Object.keys(DEFAULT_CONFIG).map((key) => ({
        key: key as keyof ClientConfig,
        category: configCategories[key as keyof ClientConfig],
        value: DEFAULT_CONFIG[key as keyof ClientConfig]
    }))

    const filteredEntries = configEntries.filter(({ key, category }) => {
        if (!input.trim()) return true
        const s = input.toLowerCase()
        const description = configDescription[key]?.toLowerCase() || ''
        return key.toLowerCase().includes(s) || description.includes(s)
    })

    const groupedCategories = filteredEntries.reduce(
        (acc, { category, key }) => {
            if (!acc[category]) acc[category] = []
            acc[category].push(key)
            return acc
        },
        {} as Record<string, Array<keyof ClientConfig>>
    )

    return (
        <div className={'flex flex-col'}>
            <div className="mb-2">Edit Client Config:</div>
            <input
                type="text"
                placeholder="Search Configs..."
                className="w-full mb-3 px-3 py-2 border border-white shadow-lg rounded-xl"
                value={input}
                onChange={(e) => {
                    setInput(e.target.value)
                    setShouldForceOpen(true)
                }}
                onFocus={(e) => {
                    setIsSearchFocused(true)
                    setTimeout(() => e.target.select(), 0)
                }}
                onBlur={() => {
                    setIsSearchFocused(false)
                    setShouldForceOpen(false)
                }}
                autoCapitalize="off"
                autoCorrect="off"
                autoComplete="off"
                style={{
                    backgroundColor: sidebarColor
                }}
            />
            {Object.entries(groupedCategories)
                .filter(([category]) => category !== '')
                .map(([category, keys]) => (
                    <ConfigCategoryDropdown
                        key={category}
                        title={category}
                        keys={keys}
                        forceOpen={shouldForceOpen && !!input.trim()}
                        hasInput={!!input.trim()}
                    />
                ))}

            {groupedCategories[''] && (
                <div className="mb-3">
                    {groupedCategories[''].map((key) => {
                        const value = DEFAULT_CONFIG[key]
                        if (key === 'colors') return null
                        if (typeof value === 'number') return <ConfigNumberElement configKey={key} key={key} />
                        return null
                    })}
                </div>
            )}

            <ColorConfig />
        </div>
    )
}

const ColorConfig = () => {
    const context = useAppContext()
    const [profileText, setProfileText] = useState<string>(JSON.stringify(saveColorFile()))
    const [profileError, setProfileError] = useState<string>()
    const [selectedDefaultProfile, setSelectedDefaultProfile] = useState<ColorPreset>()

    function resetProfileText(): void {
        setProfileText(JSON.stringify(saveColorFile()))
        setSelectedDefaultProfile(undefined)
    }

    return (
        <>
            <div className="m-0 mt-4">
                <div className="pb-1">Customize Colors:</div>
                {
                    Object.values(Sections).map((section) => <div key={`section-${section.displayName}`}>
                        <div className="text-sm pb-1">{section.displayName}</div>
                        {
                            Object.values(Colors)
                                .filter((color) => color.section === section && color.displayName !== undefined)
                                .map((color) => <div key={`color-${color.name}`}><SingleColorPicker
                                    displayName={color.displayName as string}
                                    colorName={color}
                                    resetProfileText={resetProfileText}
                                /></div>)
                        }
                    </div>)
                }
            </div>
            <BrightButton
                className=""
                onClick={() => {
                    Object.values(Colors).forEach((color) => {
                        color.set(color.defaultColor, context)
                    })
                    resetProfileText()
                }}
            >
                Reset Colors
            </BrightButton>
            <Select
                className='m-1'
                value={selectedDefaultProfile?.displayName ?? ""}
                onChange={(e) => {
                    const preset = Presets.find(preset => preset.displayName === e)
                    if (preset !== undefined) {
                        loadColorFile(preset.data, context)
                        setSelectedDefaultProfile(preset)
                    }
                }}
                disabled={Presets.length === 0}
            >
                {selectedDefaultProfile === undefined ? <option key="" value="">Select...</option> : undefined}
                {Presets.map((p) => (
                    <option key={p.displayName} value={p.displayName}>{p.displayName}</option>
                ))}
            </Select>
            <div className="flex flex-row mt-8">
                <BrightButton
                    onClick={() => {
                        navigator.clipboard.writeText(JSON.stringify(saveColorFile()))
                    }}
                    className="h-12"
                    style={{fontSize: "1rem"}}
                >📋</BrightButton>
                <TextInput
                    className="w-72 flex-initial m-1 h-12"
                    value={profileText}
                    placeholder="Profile"
                    onKeyDown={(event) => {
                        if (event.key === "Enter") {
                            let parsedJSON:string|undefined = undefined
                            try {
                                parsedJSON = JSON.parse(profileText)
                            } catch (error) {
                                setProfileError("Cannot load JSON")
                            }
                            if (parsedJSON !== undefined) {
                                try {
                                    const error = loadColorFile(JSON.parse(profileText), context)
                                    setProfileError(error)
                                    if (error === undefined) {
                                        setSelectedDefaultProfile(undefined)
                                    }
                                } catch (error) {
                                    setProfileError("Cannot parse")
                                }
                            }
                        }
                    }}
                    onInput={(event) => setProfileText(event.currentTarget.value)}
                />
            </div>
            <p>{profileError}</p>
        </>
    )
}

const SingleColorPicker = (props: { displayName: string; colorName: Color; resetProfileText: () => void }) => {
    const context = useAppContext()
    const value = props.colorName.get()
    const ref = useRef<HTMLDivElement>(null)
    const buttonRef = useRef<HTMLButtonElement>(null)
    const [hoveredClose, setHoveredClose] = useState(false)

    const [displayColorPicker, setDisplayColorPicker] = useState(false)

    const handleClick = () => {
        setDisplayColorPicker(!displayColorPicker)
    }

    const handleClose = () => {
        setDisplayColorPicker(false)
    }

    const handleClickOutside = (event: any) => {
        if (
            ref.current &&
            buttonRef.current &&
            !ref.current.contains(event.target) &&
            !buttonRef.current.contains(event.target)
        ) {
            handleClose()
        }
    }

    const onChange = (newColor: ColorResult) => {
        props.colorName.set(newColor.hex, context)
        props.resetProfileText()
    }

    const resetColor = () => {
        props.colorName.set(props.colorName.defaultColor, context)
        props.resetProfileText()
    }

    useEffect(() => {
        window.addEventListener('mousedown', handleClickOutside)

        return () => window.removeEventListener('mousedown', handleClickOutside)
    }, [])

    return (
        <>
            <div className={'ml-2 mb-2 text-xs flex flex-start justify-start items-center'}>
                <div
                    className="rounded-full overflow-clip"
                    onClick={() => resetColor()}
                    onMouseEnter={() => setHoveredClose(true)}
                    onMouseLeave={() => setHoveredClose(false)}
                >
                    <IconContext.Provider
                        value={{
                            color: 'white',
                            className: 'w-5 h-5'
                        }}
                    >
                        {hoveredClose ? <IoCloseCircle /> : <IoCloseCircleOutline />}
                    </IconContext.Provider>
                </div>
                <button
                    ref={buttonRef}
                    className={'text-xs ml-2 px-4 py-3 mr-2 flex flex-row hover:bg-cyanDark rounded-md text-white'}
                    style={{ backgroundColor: value, border: '2px solid white' }}
                    onClick={handleClick}
                ></button>
                <div title={props.colorName.name}>{props.displayName}</div>
            </div>
            <div ref={ref} className={'width: w-min'}>
                {displayColorPicker && <ChromePicker color={value} onChange={onChange} />}
            </div>
        </>
    )
}

const ConfigCategoryDropdown: React.FC<{
    title: string
    keys: Array<keyof ClientConfig>
    forceOpen?: boolean
    hasInput?: boolean
}> = ({ title, keys, forceOpen, hasInput }) => {
    const [open, setOpen] = useState<boolean>(false)

    useEffect(() => {
        if (forceOpen) {
            setOpen(true)
        } else if (!hasInput) {
            setOpen(false)
        }
    }, [forceOpen, hasInput])

    const onClick = () => {
        setOpen(!open)
    }
    return (
        <div className="mb-3">
            <SectionHeader title={title} open={open} onClick={onClick} children={<div></div>}></SectionHeader>

            {open && (
                <div className=" px-3 py-2">
                    {keys.map((key) => {
                        const value = DEFAULT_CONFIG[key]
                        if (typeof value === 'boolean') return <ConfigBooleanElement configKey={key} key={key} />
                        if (typeof value === 'number') return <ConfigNumberElement configKey={key} key={key} />
                        if (typeof value === 'string') return <ConfigStringElement configKey={key} key={key} />
                        return null
                    })}
                </div>
            )}
        </div>
    )
}

const ConfigBooleanElement: React.FC<{ configKey: keyof ClientConfig }> = ({ configKey }) => {
    const context = useAppContext()
    const value = context.state.config[configKey] as boolean
    return (
        <div className={'flex flex-row items-center mb-2'}>
            <input
                type={'checkbox'}
                checked={value as any}
                onChange={(e) => context.updateConfigValue(configKey, e.target.checked)}
            />
            <div className={'ml-2 text-xs'}>{configDescription[configKey] ?? configKey}</div>
        </div>
    )
}

const ConfigStringElement: React.FC<{ configKey: string }> = ({ configKey }) => {
    const context = useAppContext()
    const value = context.state.config[configKey as keyof ClientConfig]
    return <div className={'flex flex-row items-center'}>Todo</div>
}

const ConfigNumberElement: React.FC<{ configKey: keyof ClientConfig }> = ({ configKey }) => {
    const context = useAppContext()
    const value = context.state.config[configKey as keyof ClientConfig] as number
    return (
        <div className={'flex flex-row items-center mb-2'}>
            <NumInput
                value={value}
                changeValue={(newVal) => {
                    context.updateConfigValue(configKey, newVal)
                    if (configKey === 'resolutionScale') {
                        // Trigger canvas dimension update to ensure resolution is updated
                        GameRenderer.onMatchChange()
                    }
                }}
                min={configKey === "resolutionScale" ? 10 : 0}
                max={configKey === "resolutionScale" ? 200 : 100}
            />
            <div className={'ml-2 text-xs'}>{configDescription[configKey] ?? configKey}</div>
        </div>
    )
}
