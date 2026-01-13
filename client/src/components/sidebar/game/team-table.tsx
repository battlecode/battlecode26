import React from 'react'
import { imageSource } from '../../../util/ImageLoader'
import { TEAM_COLOR_NAMES } from '../../../constants'
import { schema } from 'battlecode-schema'
import { TeamRoundStat } from '../../../playback/RoundStat'
import { DoubleChevronUpIcon } from '../../../icons/chevron'
import { CurrentMap } from '../../../playback/Map'
import { useRound } from '../../../playback/GameRunner'

interface UnitsIconProps {
    teamIdx: 0 | 1
    img: string
}

const UnitsIcon: React.FC<UnitsIconProps> = (props: UnitsIconProps) => {
    const color = TEAM_COLOR_NAMES[props.teamIdx].toLowerCase()
    let imagePath: string = ''
    if (props.img == 'rat') {
        imagePath = `robots/${color}/rat_0_64x64.png`
    } else {
        imagePath = `icons/${props.img}.png`
    }

    return (
        <th key={imagePath} className="pb-1 w-[50px] h-[50px]">
            <img src={imageSource(imagePath)} className="w-full h-full"></img>
        </th>
    )
}

interface TeamTableProps {
    teamIdx: 0 | 1
}

export const TeamTable: React.FC<TeamTableProps> = (props: TeamTableProps) => {
    const round = useRound()
    const teamStat = round?.stat?.getTeamStat(round?.match.game.teams[props.teamIdx])
    const map = round?.map

    return (
        <>
            <div className="flex flex-col">
                <ResourceTable map={map} teamStat={teamStat} teamIdx={props.teamIdx} />
            </div>
            <div className="flex flex-col items-center">
                <UnitsTable teamStat={teamStat} teamIdx={props.teamIdx} />
            </div>
        </>
    )
}

interface ResourceTableProps {
    teamStat: TeamRoundStat | undefined
    map: CurrentMap | undefined
    teamIdx: 0 | 1
}

export const ResourceTable: React.FC<ResourceTableProps> = ({ map, teamStat, teamIdx }) => {
    let cheeseAmount = 0
    let cheesePercent = 0
    let catDamageAmount = 0
    let catDamagePercent = 0
    let ratKingCount = 0
    let ratKingPercent = 0
    let globalCheese = 0

    if (map && teamStat) {
        cheeseAmount = teamStat.cheeseAmount
        cheesePercent = teamStat.cheesePercent
        catDamageAmount = teamStat.catDamageAmount
        catDamagePercent = teamStat.catDamagePercent
        ratKingCount = teamStat.ratKingCount
        ratKingPercent = teamStat.ratKingPercent
        globalCheese = teamStat.globalCheeseAmount
    }

    const formatPercent = (val: number) => (val * 100).toFixed(1).toString() + '%'

    const teamName = TEAM_COLOR_NAMES[teamIdx].toLowerCase()
    return (
        <div className="flex flex-col items-center">
            <div className="flex items-center w-full mt-2 mb-1 text-xs font-bold justify-around">
                <div className="flex items-center w-[160px] ml-6">
                    <div className="w-[30px] h-[30px] mr-5">
                        <img style={{ transform: 'scale(1.5)' }} src={imageSource(`icons/cheese_64x64.png`)} />
                    </div>
                    <div>Amount:</div>
                    <div className="ml-1">
                        <b>{cheeseAmount}</b>
                    </div>
                </div>
                <div className="flex items-center w-[145px]">
                    <div>Percent:</div>
                    <div className="ml-1">
                        <b>{formatPercent(cheesePercent)}</b>
                    </div>
                </div>
            </div>
            <div className="flex items-center w-full mt-2 mb-1 text-xs font-bold justify-around">
                <div className="flex items-center w-[160px] ml-6">
                    <div className="w-[30px] h-[30px] mr-5">
                        <img style={{ transform: 'scale(1.5)' }} src={imageSource(`robots/cat/cat_0.png`)} />
                    </div>
                    <div>Damage:</div>
                    <div className="ml-1">
                        <b>{catDamageAmount}</b>
                    </div>
                </div>
                <div className="flex items-center w-[145px]">
                    <div>Percent:</div>
                    <div className="ml-1">
                        <b>{formatPercent(catDamagePercent)}</b>
                    </div>
                </div>
            </div>
            <div className="flex items-center w-full mt-2 mb-1 text-xs font-bold justify-around">
                <div className="flex items-center w-[160px] ml-6">
                    <div className="w-[30px] h-[30px] mr-5">
                        <img
                            style={{ transform: 'scale(1.5)' }}
                            src={imageSource(`robots/${teamName}/rat_king_64x64.png`)}
                        />
                    </div>
                    <div>Count:</div>
                    <div className="ml-1">
                        <b>{ratKingCount}</b>
                    </div>
                </div>
                <div className="flex items-center w-[145px]">
                    <div>Percent:</div>
                    <div className="ml-1">
                        <b>{formatPercent(ratKingPercent)}</b>
                    </div>
                </div>
            </div>
            <div className="flex items-center w-full mt-2 mb-1 text-xs font-bold justify-around">Global Cheese Amount: {globalCheese}</div>
        </div>
    )
}

interface UnitsTableProps {
    teamStat: TeamRoundStat | undefined
    teamIdx: 0 | 1
}

export const UnitsTable: React.FC<UnitsTableProps> = ({ teamStat, teamIdx }) => {
    const columns: Array<[string, React.ReactElement]> = [
        ['Dirt', <UnitsIcon teamIdx={teamIdx} img="dirt" key="0" />],
        ['Rat Traps', <UnitsIcon teamIdx={teamIdx} img="rat_trap" key="1" />],
        ['Cat Traps', <UnitsIcon teamIdx={teamIdx} img="cat_trap" key="2" />],
        ['Baby Rats', <UnitsIcon teamIdx={teamIdx} img="rat" key="3" />]
    ]

    let data: [string, number[]][] = [['Count:', [0, 0, 0, 0]]]
    if (teamStat) {
        data = [['Count:', [teamStat.dirtAmount, teamStat.ratTrapAmount, teamStat.catTrapAmount, teamStat.babyRatCount]]]
    }

    return (
        <>
            <table className="my-1">
                <thead>
                    <tr className="mb-2">
                        <th className="pb-1"></th>
                        {columns.map((column) => column[1])}
                    </tr>
                </thead>
                <tbody>
                    {data.map((dataRow, rowIndex) => (
                        <tr key={rowIndex}>
                            <th className="text-xs">{dataRow[0]}</th>
                            {dataRow[1].map((value, colIndex) => (
                                <td className="text-center text-xs" key={rowIndex + ':' + colIndex}>
                                    {value}
                                </td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </>
    )
}
