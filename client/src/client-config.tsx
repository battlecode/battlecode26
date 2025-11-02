import React, { useEffect, useState, MouseEvent, PropsWithChildren, useRef } from 'react'
import { IconContext } from 'react-icons'
import { IoCloseCircle, IoCloseCircleOutline } from 'react-icons/io5'
import { ChromePicker, ColorResult } from 'react-color'
import { AppContextProvider, useAppContext } from './app-context'
import { GameRenderer } from './playback/GameRenderer'
import { NumInput } from './components/forms'
import { Colors, Color, Sections } from './colors'
import { BrightButton, Button } from './components/button'
import { useKeyboard } from './util/keyboard'

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
    colors: getDefaultColors(),
}

const configDescription: Record<keyof ClientConfig, string> = {
    showAllIndicators: 'Show all indicator dots and lines',
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

    useEffect(() => {
        if (context.state.disableHotkeys) return

        if (keyboard.keyCode === 'KeyF')
            context.updateConfigValue('focusRobotTurn', !context.state.config.focusRobotTurn)
        if (keyboard.keyCode === 'KeyI')
            context.updateConfigValue('showAllIndicators', !context.state.config.showAllIndicators)
    }, [keyboard.keyCode])

    if (!props.open) return null

    return (
        <div className={'flex flex-col'}>
            <div className="mb-2">Edit Client Config:</div>
            {Object.entries(DEFAULT_CONFIG).map(([k, v]) => {
                const key = k as keyof ClientConfig
                if (typeof v === 'string') return <ConfigStringElement configKey={key} key={key} />
                if (typeof v === 'boolean') return <ConfigBooleanElement configKey={key} key={key} />
                if (typeof v === 'number') return <ConfigNumberElement configKey={key} key={key} />
            })}

            <ColorConfig />
        </div>
    )
}

const ColorConfig = () => {
    const context = useAppContext()

    return (
        <>
            <div className="m-0 mt-4">
                <div className="pb-1">Customize Colors:</div>
                {
                    Object.values(Sections).map((section) => <div>
                        <div className="text-sm pb-1">{section.displayName}</div>
                        {
                            Object.values(Colors)
                                .filter((color) => color.section === section && color.displayName !== undefined)
                                .map((color) => <SingleColorPicker displayName={color.displayName as string} colorName={color} />)
                        }
                    </div>)
                }
            </div>
            <div className="flex flex-row mt-8">
                <BrightButton
                    className=""
                    onClick={() => {
                        Object.values(Colors).forEach((color) => {
                            color.set(color.defaultColor, context)
                        })
                    }}
                >
                    Reset Colors
                </BrightButton>
            </div>
        </>
    )
}

const SingleColorPicker = (props: { displayName: string; colorName: Color }) => {
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
    }

    const resetColor = () => {
        props.colorName.set(props.colorName.defaultColor, context)
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
                {props.displayName}
            </div>
            <div ref={ref} className={'width: w-min'}>
                {displayColorPicker && <ChromePicker color={value} onChange={onChange} />}
            </div>
        </>
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
                min={10}
                max={200}
            />
            <div className={'ml-2 text-xs'}>{configDescription[configKey] ?? configKey}</div>
        </div>
    )
}
